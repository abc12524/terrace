import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/zvec/embedding_service.dart';
import '../services/zvec/zvec_service.dart';

/// 本地向量记忆（zvec）配置 Provider。
///
/// 持有 embedding 模型配置与 [ZvecService]。
class ZvecProvider extends ChangeNotifier {
  static const _eKey = 'zvec_enabled_v1';
  static const _aKey = 'zvec_api_key_v1';
  static const _bKey = 'zvec_api_base_v1';
  static const _mKey = 'zvec_model_v1';
  static const _dKey = 'zvec_dimension_v1';
  static const _tKey = 'zvec_threshold_v1';
  static const _cKey = 'zvec_display_count_v1';

  bool _enabled = false;
  String _apiKey = '';
  String _apiBase = 'https://ark.cn-beijing.volces.com/api/v3';
  String _model = 'doubao-embedding-vision-251215';
  int _dimension = 1024;
  double _threshold = 0.35;
  int _displayCount = 3;

  bool get enabled => _enabled;
  String get apiKey => _apiKey;
  String get apiBase => _apiBase;
  String get model => _model;
  int get dimension => _dimension;
  double get threshold => _threshold;
  int get displayCount => _displayCount;

  ZvecService? _service;
  ZvecService? get service => _service;
  bool get isConfigured => _apiKey.trim().isNotEmpty;

  ZvecProvider() {
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    _enabled = prefs.getBool(_eKey) ?? false;
    _apiKey = prefs.getString(_aKey) ?? '';
    _apiBase =
        prefs.getString(_bKey) ?? 'https://ark.cn-beijing.volces.com/api/v3';
    _model = prefs.getString(_mKey) ?? 'doubao-embedding-vision-251215';
    _dimension = prefs.getInt(_dKey) ?? 1024;
    _threshold = prefs.getDouble(_tKey) ?? 0.35;
    _displayCount = prefs.getInt(_cKey) ?? 3;
    _rebuildService();
    notifyListeners();
  }

  void _rebuildService() {
    if (_apiKey.trim().isNotEmpty) {
      _service = ZvecService(
        embedding: EmbeddingService(
          apiKey: _apiKey,
          apiBase: _apiBase,
          model: _model,
          dimension: _dimension,
        ),
      );
    } else {
      _service = null;
    }
  }

  Future<void> setEnabled(bool v) async {
    _enabled = v;
    notifyListeners();
    (await SharedPreferences.getInstance()).setBool(_eKey, v);
  }

  Future<void> setApiKey(String v) async {
    _apiKey = v.trim();
    _rebuildService();
    notifyListeners();
    (await SharedPreferences.getInstance()).setString(_aKey, _apiKey);
  }

  Future<void> setApiBase(String v) async {
    _apiBase = v.trim();
    _rebuildService();
    notifyListeners();
    (await SharedPreferences.getInstance()).setString(_bKey, _apiBase);
  }

  Future<void> setModel(String v) async {
    _model = v.trim();
    _rebuildService();
    notifyListeners();
    (await SharedPreferences.getInstance()).setString(_mKey, _model);
  }

  Future<void> setDimension(int v) async {
    _dimension = v;
    _rebuildService();
    notifyListeners();
    (await SharedPreferences.getInstance()).setInt(_dKey, _dimension);
  }

  Future<void> setThreshold(double v) async {
    _threshold = v;
    notifyListeners();
    (await SharedPreferences.getInstance()).setDouble(_tKey, v);
  }

  Future<void> setDisplayCount(int v) async {
    _displayCount = v.clamp(0, 20);
    notifyListeners();
    (await SharedPreferences.getInstance()).setInt(_cKey, _displayCount);
  }
}
