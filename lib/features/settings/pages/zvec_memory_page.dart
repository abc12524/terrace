import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../icons/lucide_adapter.dart';
import '../../../core/providers/zvec_provider.dart';
import 'package:Kelivo/theme/app_font_weights.dart';

/// 本地向量记忆（zvec）设置页。
class ZvecMemoryPage extends StatefulWidget {
  const ZvecMemoryPage({super.key});
  @override
  State<ZvecMemoryPage> createState() => _ZvecMemoryPageState();
}

class _ZvecMemoryPageState extends State<ZvecMemoryPage> {
  late TextEditingController _apiKeyCtrl;
  late TextEditingController _apiBaseCtrl;
  late TextEditingController _modelCtrl;
  late TextEditingController _dimensionCtrl;

  @override
  void initState() {
    super.initState();
    final zvec = context.read<ZvecProvider>();
    _apiKeyCtrl = TextEditingController(text: zvec.apiKey);
    _apiBaseCtrl = TextEditingController(text: zvec.apiBase);
    _modelCtrl = TextEditingController(text: zvec.model);
    _dimensionCtrl = TextEditingController(text: '${zvec.dimension}');
  }

  @override
  void dispose() {
    _apiKeyCtrl.dispose();
    _apiBaseCtrl.dispose();
    _modelCtrl.dispose();
    _dimensionCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final zvec = context.watch<ZvecProvider>();

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Lucide.ArrowLeft),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text('本地记忆 (zvec)'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        children: [
          // Enable switch
          _sectionCard(
            cs,
            children: [
              _switchRow(cs, '启用本地记忆', zvec.enabled, (v) {
                zvec.setEnabled(v);
              }),
            ],
          ),
          const SizedBox(height: 12),

          // Embedding model settings
          _sectionHeader('向量模型设置', cs),
          _sectionCard(
            cs,
            children: [
              _textFieldRow(
                cs,
                label: 'API Key（火山方舟）',
                hint: 'f4...',
                controller: _apiKeyCtrl,
                onChanged: (v) => zvec.setApiKey(v),
                obscure: true,
              ),
              _divider(cs),
              _textFieldRow(
                cs,
                label: 'API Base',
                hint: 'https://ark.cn-beijing.volces.com/api/v3',
                controller: _apiBaseCtrl,
                onChanged: (v) => zvec.setApiBase(v),
              ),
              _divider(cs),
              _textFieldRow(
                cs,
                label: '模型',
                hint: 'doubao-embedding-vision-251215',
                controller: _modelCtrl,
                onChanged: (v) => zvec.setModel(v),
              ),
              _divider(cs),
              _textFieldRow(
                cs,
                label: '向量维度',
                hint: '1024',
                controller: _dimensionCtrl,
                onChanged: (v) {
                  final dim = int.tryParse(v.trim());
                  if (dim != null && dim > 0) zvec.setDimension(dim);
                },
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Search settings
          _sectionHeader('搜索设置', cs),
          _sectionCard(
            cs,
            children: [
              _sliderRow(
                cs,
                label: '分数阈值: ${zvec.threshold.toStringAsFixed(2)}',
                value: zvec.threshold,
                min: 0.0,
                max: 1.0,
                divisions: 20,
                onChanged: (v) => zvec.setThreshold(v),
              ),
              _divider(cs),
              _sliderRow(
                cs,
                label: '显示条数: ${zvec.displayCount}',
                value: zvec.displayCount.toDouble(),
                min: 0,
                max: 20,
                divisions: 20,
                onChanged: (v) => zvec.setDisplayCount(v.round()),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Status
          if (zvec.isConfigured)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                zvec.enabled ? '✔️ 本地记忆已配置并启用，对话时自动检索相关记忆' : '⚠️ 本地记忆已配置但未启用',
                style: TextStyle(
                  fontSize: 13,
                  color: cs.onSurface.withValues(alpha: 0.6),
                ),
              ),
            )
          else
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                '⚠️ 请配置向量模型 API Key',
                style: TextStyle(
                  fontSize: 13,
                  color: cs.onSurface.withValues(alpha: 0.6),
                ),
              ),
            ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              '记忆使用本地 zvec 向量库存储，向量由火山方舟 doubao-embedding-vision 生成。',
              style: TextStyle(
                fontSize: 12,
                color: cs.onSurface.withValues(alpha: 0.4),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sectionHeader(String text, ColorScheme cs) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 4, 12, 6),
    child: Text(
      text,
      style: TextStyle(
        fontSize: 13,
        fontWeight: AppFontWeights.semibold,
        color: cs.onSurface.withValues(alpha: 0.8),
      ),
    ),
  );

  Widget _sectionCard(ColorScheme cs, {required List<Widget> children}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      decoration: BoxDecoration(
        color: isDark ? Colors.white10 : Colors.white.withValues(alpha: 0.96),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: cs.outlineVariant.withValues(alpha: isDark ? 0.08 : 0.06),
          width: 0.6,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(children: children),
      ),
    );
  }

  Widget _divider(ColorScheme cs) => Divider(
    height: 6,
    thickness: 0.6,
    indent: 16,
    endIndent: 12,
    color: cs.outlineVariant.withValues(alpha: 0.18),
  );

  Widget _switchRow(
    ColorScheme cs,
    String label,
    bool value,
    ValueChanged<bool> onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 15,
                color: cs.onSurface.withValues(alpha: 0.9),
              ),
            ),
          ),
          Switch(value: value, onChanged: onChanged),
        ],
      ),
    );
  }

  Widget _textFieldRow(
    ColorScheme cs, {
    required String label,
    required String hint,
    required TextEditingController controller,
    ValueChanged<String>? onChanged,
    bool obscure = false,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: cs.onSurface.withValues(alpha: 0.7),
              ),
            ),
          ),
          TextField(
            controller: controller,
            obscureText: obscure,
            decoration: InputDecoration(
              hintText: hint,
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 10,
                vertical: 8,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            style: TextStyle(fontSize: 14, color: cs.onSurface),
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }

  Widget _sliderRow(
    ColorScheme cs, {
    required String label,
    required double value,
    required double min,
    required double max,
    int? divisions,
    required ValueChanged<double> onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 2),
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: cs.onSurface.withValues(alpha: 0.7),
              ),
            ),
          ),
          Slider(
            value: value,
            min: min,
            max: max,
            divisions: divisions,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}
