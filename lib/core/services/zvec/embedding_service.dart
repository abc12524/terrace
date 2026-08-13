import 'dart:convert';
import 'package:http/http.dart' as http;

/// Volcengine Ark 多模态向量化服务封装。
///
/// 调用 `POST {apiBase}/embeddings/multimodal`，输入文本返回对应维度向量。
/// 默认模型输出 2048 维，传入 `dimensions` 参数可降维（本项目用 1024）。
class EmbeddingService {
  final String apiKey;
  final String apiBase;
  final String model;
  final int dimension;

  EmbeddingService({
    required this.apiKey,
    required this.apiBase,
    required this.model,
    this.dimension = 1024,
  });

  bool get isConfigured => apiKey.trim().isNotEmpty;

  /// 将单条文本编码为向量。失败时抛出 [EmbeddingException]。
  Future<List<double>> embed(String text) async {
    final texts = text
        .trim()
        .split('\n')
        .where((t) => t.trim().isNotEmpty)
        .toList();
    if (texts.isEmpty) {
      throw const EmbeddingException('待编码文本为空');
    }
    if (texts.length == 1) return _embedOne(texts.first);

    // 逐行编码并取平均，避免单条超长文本超出模型输入上限
    final vectors = <List<double>>[];
    for (final t in texts) {
      vectors.add(await _embedOne(t));
    }
    final dim = vectors.first.length;
    final result = List<double>.filled(dim, 0);
    for (final v in vectors) {
      for (var i = 0; i < dim; i++) {
        result[i] += v[i];
      }
    }
    for (var i = 0; i < dim; i++) {
      result[i] /= vectors.length;
    }
    return result;
  }

  Future<List<double>> _embedOne(String text) async {
    final base = apiBase.trim().replaceAll(RegExp(r'/$'), '');
    final url = Uri.parse('$base/embeddings/multimodal');
    final response = await http
        .post(
          url,
          headers: {
            'Authorization': 'Bearer ${apiKey.trim()}',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({
            'model': model.trim(),
            'input': [
              {'type': 'text', 'text': text},
            ],
            if (dimension > 0) 'dimensions': dimension,
          }),
        )
        .timeout(const Duration(seconds: 30));
    if (response.statusCode != 200) {
      throw EmbeddingException(
        'Embedding API HTTP ${response.statusCode}: ${response.body}',
      );
    }
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final data = body['data'];
    if (data is Map<String, dynamic> && data['embedding'] is List) {
      return (data['embedding'] as List)
          .map((e) => (e as num).toDouble())
          .toList();
    }
    throw EmbeddingException('Unexpected embedding response: ${response.body}');
  }
}

class EmbeddingException implements Exception {
  final String message;
  const EmbeddingException(this.message);
  @override
  String toString() => message;
}
