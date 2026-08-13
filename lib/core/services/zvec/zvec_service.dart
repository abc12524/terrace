import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';
import 'package:zvec/zvec.dart';

import 'embedding_service.dart';

/// 一条向量搜索命中的记忆（snippet 为全文前 200 字符）。
class ZvecMemoryHit {
  final String pk;
  final double score;
  final String snippet;
  final String? title;
  final String? category;
  const ZvecMemoryHit({
    required this.pk,
    required this.score,
    required this.snippet,
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
  String? _filesRoot;
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
    _filesRoot = '${dir.path}/files';
    final filesDir = Directory(_filesRoot!);
    if (!filesDir.existsSync()) filesDir.createSync(recursive: true);
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
        FieldSchema(name: 'path', dataType: DataType.string),
        FieldSchema(name: 'title', dataType: DataType.string),
        FieldSchema(name: 'category', dataType: DataType.string),
      ],
    );
    return Collection.createAndOpen(path, schema);
  }

  /// zvec 主键要求字符集受限（不允许 `/`、`.` 等），用文件路径的
  /// SHA-256 十六进制作为内部主键；文件路径保存在 doc 的 `path` 字段。
  String _hashPk(String path) => sha256.convert(utf8.encode(path)).toString();

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
      outputFields: ['content', 'path', 'title', 'category'],
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
            pk: doc.getString('path') ?? doc.pk ?? '',
            score: score,
            snippet: _snippet(doc.getString('content') ?? ''),
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
  ///
  /// 全文写入本地 md 文件 `{files}/{pk}`，同时向量化存入 zvec 供检索。
  /// [content] 与 [sourcePath] 二选一：传 [sourcePath] 时直接从设备文件读取
  /// 内容做向量化（content 可省略）。
  Future<String> remember({
    required String pk,
    String? content,
    String? sourcePath,
    String? title,
    String? category,
  }) async {
    await ensureInitialized();
    String resolvedContent;
    if (sourcePath != null && sourcePath.isNotEmpty) {
      final src = File(sourcePath);
      if (!src.existsSync()) {
        throw ZvecException('源文件不存在: $sourcePath');
      }
      resolvedContent = src.readAsStringSync();
    } else if (content != null && content.isNotEmpty) {
      resolvedContent = content;
    } else {
      throw const ZvecException('必须提供 content 或 sourcePath 其中之一');
    }
    // 先向量化，失败则不落盘
    final vec = await embedding.embed(resolvedContent);
    final file = File('${_filesRoot!}/$pk');
    file.parent.createSync(recursive: true);
    file.writeAsStringSync(resolvedContent);

    final doc = Doc(id: _hashPk(pk))
      ..setField('content', resolvedContent)
      ..setField('path', pk)
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

  /// 按主键（文件相对路径，如 `preferences/deepseek余额.md`）读取全文。
  Future<ZvecMemoryRecord?> read(String pk) async {
    await ensureInitialized();
    final file = File('${_filesRoot!}/$pk');
    if (file.existsSync()) {
      final parts = pk.split('/');
      final category = parts.length >= 2 ? parts.first : null;
      final title = parts.isNotEmpty
          ? parts.last.replaceFirst(RegExp(r'\.md$'), '')
          : null;
      return ZvecMemoryRecord(
        pk: pk,
        content: file.readAsStringSync(),
        title: title,
        category: category,
      );
    }
    // 兜底：从 zvec 集合读取
    final docs = _collection!.fetch([_hashPk(pk)], includeVector: false);
    if (docs.isEmpty) return null;
    final doc = docs.first;
    try {
      return ZvecMemoryRecord(
        pk: doc.getString('path') ?? pk,
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
      final docs = _collection!.fetch(
        chunk.map(_hashPk).toList(),
        includeVector: false,
      );
      for (final doc in docs) {
        records.add(
          ZvecMemoryRecord(
            pk: doc.getString('path') ?? doc.pk ?? '',
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

  /// 删除一条记忆（同时删除本地 md 文件）。
  Future<void> delete(String pk) async {
    await ensureInitialized();
    _collection!.delete([_hashPk(pk)]);
    final file = File('${_filesRoot!}/$pk');
    if (file.existsSync()) file.deleteSync();
    _removePk(pk);
  }

  /// 清空全部记忆（同时清空本地文件目录）。
  Future<void> clear() async {
    await ensureInitialized();
    final pks = _loadPks();
    const chunkSize = 50;
    for (var i = 0; i < pks.length; i += chunkSize) {
      _collection!.delete(pks.skip(i).take(chunkSize).map(_hashPk).toList());
    }
    final filesDir = Directory(_filesRoot!);
    if (filesDir.existsSync()) filesDir.deleteSync(recursive: true);
    filesDir.createSync(recursive: true);
    _savePks([]);
  }

  /// 截断为前 200 字符的摘要（与命中展示一致）。
  String _snippet(String content) {
    const maxLen = 200;
    if (content.length <= maxLen) return content;
    return content.substring(0, maxLen);
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
