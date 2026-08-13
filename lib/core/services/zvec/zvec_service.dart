import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';
import 'package:zvec/zvec.dart';

import 'embedding_service.dart';

/// 一条向量搜索命中的记忆。
class ZvecMemoryHit {
  final String pk;
  final double score;
  final String content;
  final String? title;
  final String? category;
  const ZvecMemoryHit({
    required this.pk,
    required this.score,
    required this.content,
    this.title,
    this.category,
  });
}

/// 本地记忆库中的一条记录。
class ZvecMemoryRecord {
  final String pk;
  final String content;
  final String? title;
  final String? category;
  const ZvecMemoryRecord({
    required this.pk,
    required this.content,
    this.title,
    this.category,
  });
}

/// 本地向量记忆服务。
///
/// 基于 zvec（进程内向量数据库）持久化记忆文档，配合 [EmbeddingService]
/// 将文本编码为向量后做余弦相似度检索。集合存放在应用支持目录下。
class ZvecService {
  ZvecService({required this.embedding});

  final EmbeddingService embedding;

  Collection? _collection;
  String? _pkIndexPath;
  int _dimension = 0;

  bool get isReady => _collection != null;

  /// 初始化 zvec 并打开/创建记忆集合。
  Future<void> ensureInitialized() async {
    if (_collection != null) return;
    if (!Zvec.isInitialized) Zvec.initialize();
    final support = await getApplicationSupportDirectory();
    final dir = Directory('${support.path}/zvec');
    if (!dir.existsSync()) dir.createSync(recursive: true);
    _pkIndexPath = '${dir.path}/pks.json';
    _dimension = embedding.dimension;
    _collection = _openOrCreate('${dir.path}/memories');
  }

  Collection _openOrCreate(String path) {
    try {
      final coll = Collection.open(path);
      final schema = coll.schema;
      final vectorField = schema.getField('embedding');
      final existingDim = vectorField?.dimension ?? 0;
      schema.destroy();
      if (existingDim != 0 && existingDim == _dimension) return coll;
      // 维度不匹配（如换了 embedding 模型），重建集合
      coll.close();
      final dbDir = Directory(path);
      if (dbDir.existsSync()) dbDir.deleteSync(recursive: true);
    } catch (_) {
      // 集合尚不存在，走创建分支
    }
    final schema = CollectionSchema(
      name: 'memories',
      fields: [
        VectorSchema('embedding', _dimension, indexParams: HnswIndexParams()),
        FieldSchema(
          name: 'content',
          dataType: DataType.string,
          nullable: false,
        ),
        FieldSchema(name: 'title', dataType: DataType.string),
        FieldSchema(name: 'category', dataType: DataType.string),
      ],
    );
    return Collection.createAndOpen(path, schema);
  }

  /// 语义搜索，按 [scoreThreshold] 过滤相似度低于阈值的命中。
  Future<List<ZvecMemoryHit>> search(
    String query, {
    double scoreThreshold = 0.0,
    int limit = 5,
  }) async {
    await ensureInitialized();
    if (query.trim().isEmpty) return [];
    final vec = await embedding.embed(query);
    final q = VectorQuery(
      fieldName: 'embedding',
      vector: Float32List.fromList(vec),
      topk: limit,
      outputFields: ['content', 'title', 'category'],
    );
    try {
      final results = _collection!.query(q);
      final hits = <ZvecMemoryHit>[];
      for (final doc in results) {
        final score = doc.score;
        if (score < scoreThreshold) {
          doc.destroy();
          continue;
        }
        hits.add(
          ZvecMemoryHit(
            pk: doc.pk ?? '',
            score: score,
            content: doc.getString('content') ?? '',
            title: doc.getString('title'),
            category: doc.getString('category'),
          ),
        );
        doc.destroy();
      }
      return hits;
    } finally {
      q.destroy();
    }
  }

  /// 保存一条记忆（按 pk upsert，已存在则覆盖）。
  Future<String> remember({
    required String pk,
    required String content,
    String? title,
    String? category,
  }) async {
    await ensureInitialized();
    final vec = await embedding.embed(content);
    final doc = Doc(id: pk)
      ..setField('content', content)
      ..setVector('embedding', Float32List.fromList(vec));
    if (title != null && title.isNotEmpty) doc.setField('title', title);
    if (category != null && category.isNotEmpty) {
      doc.setField('category', category);
    }
    try {
      final result = _collection!.upsert([doc]);
      if (result.errorCount > 0) {
        throw ZvecException(result.errorMessages.join('; '));
      }
      _addPk(pk);
    } finally {
      doc.destroy();
    }
    // 增量构建 HNSW 索引，保证后续查询走向量索引
    try {
      _collection!.optimize();
    } catch (_) {}
    return pk;
  }

  /// 按主键读取单条记忆。
  Future<ZvecMemoryRecord?> read(String pk) async {
    await ensureInitialized();
    final docs = _collection!.fetch([pk], includeVector: false);
    if (docs.isEmpty) return null;
    final doc = docs.first;
    try {
      return ZvecMemoryRecord(
        pk: doc.pk ?? pk,
        content: doc.getString('content') ?? '',
        title: doc.getString('title'),
        category: doc.getString('category'),
      );
    } finally {
      doc.destroy();
    }
  }

  /// 列出记忆库中的全部记录（按保存顺序）。
  Future<List<ZvecMemoryRecord>> list({int limit = 200}) async {
    await ensureInitialized();
    final pks = _loadPks();
    final records = <ZvecMemoryRecord>[];
    const chunkSize = 50;
    for (var i = 0; i < pks.length && records.length < limit; i += chunkSize) {
      final chunk = pks.skip(i).take(chunkSize).toList();
      final docs = _collection!.fetch(chunk, includeVector: false);
      for (final doc in docs) {
        records.add(
          ZvecMemoryRecord(
            pk: doc.pk ?? '',
            content: doc.getString('content') ?? '',
            title: doc.getString('title'),
            category: doc.getString('category'),
          ),
        );
        doc.destroy();
      }
    }
    return records.take(limit).toList();
  }

  /// 删除一条记忆。
  Future<void> delete(String pk) async {
    await ensureInitialized();
    _collection!.delete([pk]);
    _removePk(pk);
  }

  /// 清空全部记忆。
  Future<void> clear() async {
    await ensureInitialized();
    final pks = _loadPks();
    const chunkSize = 50;
    for (var i = 0; i < pks.length; i += chunkSize) {
      _collection!.delete(pks.skip(i).take(chunkSize).toList());
    }
    _savePks([]);
  }

  /// 关闭集合，释放原生资源。
  void close() {
    _collection?.close();
    _collection = null;
  }

  // ---------------------------------------------------------------------------
  // 主键注册表（zvec 没有全量枚举接口，用 JSON 文件维护 pk 列表）
  // ---------------------------------------------------------------------------

  List<String> _loadPks() {
    if (_pkIndexPath == null) return [];
    final f = File(_pkIndexPath!);
    if (!f.existsSync()) return [];
    try {
      final decoded = jsonDecode(f.readAsStringSync());
      if (decoded is List) {
        return decoded.map((e) => e.toString()).toSet().toList();
      }
    } catch (_) {}
    return [];
  }

  void _savePks(List<String> pks) {
    if (_pkIndexPath == null) return;
    try {
      File(_pkIndexPath!).writeAsStringSync(jsonEncode(pks));
    } catch (_) {}
  }

  void _addPk(String pk) {
    final pks = _loadPks();
    if (!pks.contains(pk)) pks.add(pk);
    _savePks(pks);
  }

  void _removePk(String pk) {
    final pks = _loadPks();
    pks.remove(pk);
    _savePks(pks);
  }
}

class ZvecException implements Exception {
  final String message;
  const ZvecException(this.message);
  @override
  String toString() => message;
}
