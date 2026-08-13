import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import '../../../core/models/assistant.dart';
import '../../../core/providers/assistant_provider.dart';
import '../../../core/providers/mcp_provider.dart';
import '../../../core/providers/memory_provider.dart';
import '../../../core/providers/settings_provider.dart';
import '../../../core/providers/tts_provider.dart';
import '../../../core/services/api/chat_api_service.dart';
import '../../../core/services/mcp/mcp_tool_service.dart';
import '../../../core/services/zvec/zvec_service.dart';
import '../../../core/providers/zvec_provider.dart';
import '../../../core/services/search/search_tool_service.dart';
import 'ask_user_interaction_service.dart';
import '../../../core/services/device_tools_service.dart';
import 'local_tools_service.dart';
import 'tool_approval_service.dart';

/// 工具调用处理服务
///
/// 处理各类工具调用：
/// - MCP 工具
/// - Memory 工具 (create/edit/delete)
/// - Search 工具
class ToolHandlerService {
  ToolHandlerService({required this.contextProvider});

  /// Build context (used for accessing providers)
  final BuildContext contextProvider;

  // ============================================================================
  // Tool Schema Sanitization
  // ============================================================================

  /// Sanitize/translate JSON Schema to each provider's accepted subset.
  ///
  /// Different providers (Google, OpenAI, Claude) have different requirements
  /// for tool parameter schemas. This method normalizes schemas to work across
  /// all providers.
  static Map<String, dynamic> sanitizeToolParametersForProvider(
    Map<String, dynamic> schema,
    ProviderKind kind,
  ) {
    Map<String, dynamic> clone = _deepCloneMap(schema);
    clone = _sanitizeNode(clone, kind) as Map<String, dynamic>;
    return clone;
  }

  static dynamic _sanitizeNode(dynamic node, ProviderKind kind) {
    if (node is List) {
      return node.map((e) => _sanitizeNode(e, kind)).toList();
    }
    if (node is! Map) return node;

    final m = Map<String, dynamic>.from(node);
    // Remove $schema as it's not needed for tool definitions
    m.remove(r'$schema');

    // Convert 'const' to 'enum' for compatibility
    if (m.containsKey('const')) {
      final v = m['const'];
      if (v is String || v is num || v is bool) {
        m['enum'] = [v];
      }
      m.remove('const');
    }

    // Flatten anyOf/oneOf/allOf to first variant for simplicity
    for (final key in [
      'anyOf',
      'oneOf',
      'allOf',
      'any_of',
      'one_of',
      'all_of',
    ]) {
      if (m[key] is List && (m[key] as List).isNotEmpty) {
        final first = (m[key] as List).first;
        final flattened = _sanitizeNode(first, kind);
        m.remove(key);
        if (flattened is Map<String, dynamic>) {
          m
            ..remove('type')
            ..remove('properties')
            ..remove('items');
          m.addAll(flattened);
        }
      }
    }

    // Normalize type array to single type
    final t = m['type'];
    if (t is List && t.isNotEmpty) m['type'] = t.first.toString();

    // Normalize items array to single item
    final items = m['items'];
    if (items is List && items.isNotEmpty) m['items'] = items.first;
    if (m['items'] is Map) m['items'] = _sanitizeNode(m['items'], kind);

    // Recursively sanitize properties
    if (m['properties'] is Map) {
      final props = Map<String, dynamic>.from(m['properties']);
      final norm = <String, dynamic>{};
      props.forEach((k, v) {
        norm[k] = _sanitizeNode(v, kind);
      });
      m['properties'] = norm;
    }

    // Keep only allowed keys based on provider
    Set<String> allowed;
    switch (kind) {
      case ProviderKind.google:
        allowed = {
          'type',
          'description',
          'properties',
          'required',
          'items',
          'enum',
        };
        break;
      case ProviderKind.openai:
      case ProviderKind.claude:
        allowed = {
          'type',
          'description',
          'properties',
          'required',
          'items',
          'enum',
        };
        break;
    }
    m.removeWhere((k, v) => !allowed.contains(k));
    return m;
  }

  static Map<String, dynamic> _deepCloneMap(Map<String, dynamic> input) {
    return jsonDecode(jsonEncode(input)) as Map<String, dynamic>;
  }

  static String _toolError({
    required String error,
    required String message,
    required String tool,
    String? instruction,
  }) {
    return jsonEncode({
      'type': 'tool_error',
      'error': error,
      'message': message,
      'tool': tool,
      if (instruction != null) 'instruction': instruction,
    });
  }

  // ============================================================================
  // Tool Definitions Builder
  // ============================================================================

  /// Build tool definitions for API call.
  ///
  /// Returns a list of tool definitions including:
  /// - Search tool (if enabled and model supports tools)
  /// - Memory tools (if assistant has memory enabled)
  /// - MCP tools (from selected servers for the assistant)
  List<Map<String, dynamic>> buildToolDefinitions(
    SettingsProvider settings,
    Assistant? assistant,
    String providerKey,
    String modelId,
    bool hasBuiltInSearch, {
    required bool Function(String providerKey, String modelId) isToolModel,
  }) {
    final List<Map<String, dynamic>> toolDefs = <Map<String, dynamic>>[];
    final supportsTools = isToolModel(providerKey, modelId);

    // Search tool (skip when Gemini built-in search is active)
    if (assistant?.searchEnabled == true &&
        !hasBuiltInSearch &&
        supportsTools) {
      toolDefs.add(SearchToolService.getToolDefinition());
    }

    // Memory tools
    if (assistant?.enableMemory == true && supportsTools) {
      toolDefs.addAll(_buildMemoryToolDefinitions());
    }

    // Local tools
    toolDefs.addAll(
      LocalToolsService.buildToolDefinitions(
        assistant: assistant,
        supportsTools: supportsTools,
      ),
    );

    // MCP tools
    final mcpTools = _buildMcpToolDefinitions(
      settings: settings,
      assistant: assistant,
      providerKey: providerKey,
      supportsTools: supportsTools,
    );
    toolDefs.addAll(mcpTools);
    toolDefs.addAll(DeviceToolsService.getToolDefinitions());

    // Local zvec memory tools (when configured)
    if (supportsTools) {
      final zvecProvider = contextProvider.read<ZvecProvider>();
      if (zvecProvider.isConfigured) {
        toolDefs.addAll(_buildZvecToolDefinitions());
      }
    }

    return toolDefs;
  }

  /// Build memory tool definitions (create/edit/delete).
  List<Map<String, dynamic>> _buildMemoryToolDefinitions() {
    return [
      {
        'type': 'function',
        'function': {
          'name': 'create_memory',
          'description': 'create a memory record',
          'parameters': {
            'type': 'object',
            'properties': {
              'content': {
                'type': 'string',
                'description': 'The content of the memory record',
              },
            },
            'required': ['content'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'edit_memory',
          'description': 'update a memory record',
          'parameters': {
            'type': 'object',
            'properties': {
              'id': {
                'type': 'integer',
                'description': 'The id of the memory record',
              },
              'content': {
                'type': 'string',
                'description': 'The content of the memory record',
              },
            },
            'required': ['id', 'content'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'delete_memory',
          'description': 'delete a memory record',
          'parameters': {
            'type': 'object',
            'properties': {
              'id': {
                'type': 'integer',
                'description': 'The id of the memory record',
              },
            },
            'required': ['id'],
          },
        },
      },
    ];
  }

  /// Build MCP tool definitions from connected servers.
  List<Map<String, dynamic>> _buildMcpToolDefinitions({
    required SettingsProvider settings,
    required Assistant? assistant,
    required String providerKey,
    required bool supportsTools,
  }) {
    if (!supportsTools) return [];

    final mcp = contextProvider.read<McpProvider>();
    final toolSvc = contextProvider.read<McpToolService>();
    final tools = toolSvc.listAvailableToolsForAssistant(
      mcp,
      contextProvider.read<AssistantProvider>(),
      assistant?.id,
    );

    if (tools.isEmpty) return [];

    final providerCfg = settings.getProviderConfig(providerKey);
    final providerKind = ProviderConfig.classify(
      providerCfg.id,
      explicitType: providerCfg.providerType,
    );

    return tools.map((t) {
      Map<String, dynamic> baseSchema;
      if (t.schema != null && t.schema!.isNotEmpty) {
        baseSchema = Map<String, dynamic>.from(t.schema!);
      } else {
        final props = <String, dynamic>{
          for (final p in t.params) p.name: {'type': (p.type ?? 'string')},
        };
        final required = [
          for (final p in t.params.where((e) => e.required)) p.name,
        ];
        baseSchema = {
          'type': 'object',
          'properties': props,
          if (required.isNotEmpty) 'required': required,
        };
      }
      final sanitized = sanitizeToolParametersForProvider(
        baseSchema,
        providerKind,
      );
      return {
        'type': 'function',
        'function': {
          'name': t.name,
          if ((t.description ?? '').isNotEmpty) 'description': t.description,
          'parameters': sanitized,
        },
      };
    }).toList();
  }

  // ============================================================================
  // Tool Call Handler
  // ============================================================================

  /// Build tool call handler function.
  ///
  /// Returns a function that handles tool calls by name and arguments.
  /// Supports:
  /// - Search tool calls
  /// - Memory tool calls (create/edit/delete)
  /// - MCP tool calls
  ToolCallHandler? buildToolCallHandler(
    SettingsProvider settings,
    Assistant? assistant, {
    ToolApprovalService? approvalService,
    AskUserInteractionService? askUserService,
  }) {
    final mcp = contextProvider.read<McpProvider>();
    final toolSvc = contextProvider.read<McpToolService>();
    // Capture AssistantProvider reference before async gap to avoid
    // use_build_context_synchronously warning
    final assistantProvider = contextProvider.read<AssistantProvider>();

    return (name, args, {toolCallId}) async {
      try {
        // Search tool
        if (name == SearchToolService.toolName &&
            assistant?.searchEnabled == true) {
          final q = (args['query'] ?? '').toString();
          return await SearchToolService.executeSearch(q, settings);
        }

        // Memory tools
        final memoryResult = await _handleMemoryToolCall(name, args, assistant);
        if (memoryResult != null) {
          return memoryResult;
        }

        // Local tools
        final localResult = await LocalToolsService.tryHandleToolCall(
          name,
          args,
          assistant,
          onSpeakText: (text) async {
            final tts = contextProvider.read<TtsProvider>();
            if (!tts.isAvailable) {
              throw StateError('Text-to-speech is unavailable.');
            }
            unawaited(
              tts.speak(text).catchError((Object error, StackTrace stack) {
                FlutterError.reportError(
                  FlutterErrorDetails(
                    exception: error,
                    stack: stack,
                    library: 'Kelivo local tools',
                    context: ErrorDescription('while playing text-to-speech'),
                  ),
                );
              }),
            );
          },
        );
        if (localResult != null) {
          return localResult;
        }

        // Local zvec memory tools (must be before DeviceTools to prevent MethodChannel fallthrough)
        try {
          final zvecResult = await _handleZvecToolCall(name, args);
          if (zvecResult != null) return zvecResult;
        } catch (_) {}

        // Device tools (GPS, sensor, shell, SSH, etc.)
        try {
          final deviceResult = await DeviceToolsService.execute(name, args);
          if (deviceResult.isNotEmpty) {
            return deviceResult;
          }
        } catch (_) {}

        if (name == LocalToolNames.askUser &&
            assistant != null &&
            assistant.localToolIds.contains(LocalToolNames.askUser)) {
          if (askUserService == null) {
            return _toolError(
              error: 'ask_user_unavailable',
              message: 'Ask user interaction service is unavailable.',
              tool: name,
            );
          }
          try {
            final result = await askUserService.requestAnswer(
              toolCallId: (toolCallId?.trim().isNotEmpty == true)
                  ? toolCallId!.trim()
                  : '${name}_${DateTime.now().microsecondsSinceEpoch}',
              arguments: args,
            );
            return result.toJsonString();
          } on AskUserInvalidRequestException catch (e) {
            return _toolError(
              error: 'invalid_ask_user_request',
              message: e.message,
              tool: name,
            );
          }
        }

        // Approval gate for MCP tools
        if (approvalService != null && mcp.toolNeedsApproval(name)) {
          // Generate a unique id for this tool call approval request
          final toolCallId = '${name}_${DateTime.now().microsecondsSinceEpoch}';
          final result = await approvalService.requestApproval(
            toolCallId: toolCallId,
            toolName: name,
            arguments: args,
          );
          if (!result.approved) {
            return _toolError(
              error: 'approval_denied',
              message: result.denyReason ?? 'User denied the tool call',
              tool: name,
            );
          }
        }

        // MCP tools
        final text = await toolSvc.callToolTextForAssistant(
          mcp,
          assistantProvider,
          assistantId: assistant?.id,
          toolName: name,
          arguments: args,
        );
        return text;
      } catch (e) {
        // Catch unexpected exceptions and return error JSON to LLM
        // This prevents tool failures from terminating the chat flow
        return _toolError(
          error: 'execution_error',
          message: e.toString(),
          tool: name,
          instruction:
              'The tool execution failed unexpectedly. You may try again with different parameters or inform the user about the issue.',
        );
      }
    };
  }

  /// Handle memory tool calls (create/edit/delete).
  ///
  /// Returns null if the tool is not a memory tool or memory is not enabled.
  Future<String?> _handleMemoryToolCall(
    String name,
    Map<String, dynamic> args,
    Assistant? assistant,
  ) async {
    if (assistant?.enableMemory != true) return null;
    if (name != 'create_memory' &&
        name != 'edit_memory' &&
        name != 'delete_memory') {
      return null;
    }

    try {
      final mp = contextProvider.read<MemoryProvider>();

      if (name == 'create_memory') {
        final content = (args['content'] ?? '').toString();
        if (content.isEmpty) {
          return _toolError(
            error: 'invalid_memory_content',
            message: 'Memory content must not be empty.',
            tool: name,
          );
        }
        final m = await mp.add(assistantId: assistant!.id, content: content);
        return m.content;
      } else if (name == 'edit_memory') {
        final id = (args['id'] as num?)?.toInt() ?? -1;
        final content = (args['content'] ?? '').toString();
        if (id <= 0) {
          return _toolError(
            error: 'invalid_memory_id',
            message: 'Memory id must be a positive integer.',
            tool: name,
          );
        }
        if (content.isEmpty) {
          return _toolError(
            error: 'invalid_memory_content',
            message: 'Memory content must not be empty.',
            tool: name,
          );
        }
        final m = await mp.update(id: id, content: content);
        if (m == null) {
          return _toolError(
            error: 'memory_not_found',
            message: 'No memory record was found for id $id.',
            tool: name,
            instruction:
                'Use the available memory records shown in context, or create a new memory instead of editing a missing one.',
          );
        }
        return m.content;
      } else if (name == 'delete_memory') {
        final id = (args['id'] as num?)?.toInt() ?? -1;
        if (id <= 0) {
          return _toolError(
            error: 'invalid_memory_id',
            message: 'Memory id must be a positive integer.',
            tool: name,
          );
        }
        final ok = await mp.delete(id: id);
        if (!ok) {
          return _toolError(
            error: 'memory_not_found',
            message: 'No memory record was found for id $id.',
            tool: name,
            instruction:
                'Use the available memory records shown in context, or skip deleting a missing memory.',
          );
        }
        return 'deleted';
      }
    } catch (e) {
      return _toolError(
        error: 'memory_execution_error',
        message: e.toString(),
        tool: name,
        instruction:
            'The memory tool failed. Retry only after correcting the parameters, or inform the user about the issue.',
      );
    }

    return null;
  }

  /// Build local zvec memory tool definitions.
  static List<Map<String, dynamic>> _buildZvecToolDefinitions() {
    return [
      {
        'type': 'function',
        'function': {
          'name': 'zvec_search',
          'description': '在本地向量记忆中语义搜索，查找之前保存的知识、偏好、项目信息等',
          'parameters': {
            'type': 'object',
            'properties': {
              'query': {'type': 'string', 'description': '搜索关键词，描述要查找什么内容'},
              'score_threshold': {
                'type': 'number',
                'description': '相似度阈值（0-1，越大越严格，默认用设置值）',
              },
              'limit': {'type': 'integer', 'description': '返回条数（1-10，默认用设置值）'},
            },
            'required': ['query'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'zvec_remember',
          'description':
              '将重要信息保存到本地向量记忆中，以便后续对话回忆。content 与 path 二选一：传 path 时直接读取设备上该文件的内容做向量化保存（适合保存文件全文）；适合保存用户偏好、项目配置、关键决策、操作经验',
          'parameters': {
            'type': 'object',
            'properties': {
              'category': {
                'type': 'string',
                'enum': ['preferences', 'entities', 'events', 'experiences'],
                'description':
                    '记忆分类：preferences=用户偏好, entities=项目/概念/人物, events=决策/里程碑, experiences=操作经验',
              },
              'name': {'type': 'string', 'description': '记忆名称/主题'},
              'content': {
                'type': 'string',
                'description': '要保存的内容（Markdown 格式）。与 path 二选一',
              },
              'path': {
                'type': 'string',
                'description': '设备上文件的绝对路径，提供时直接从该文件读取内容保存。与 content 二选一',
              },
            },
            'required': ['category', 'name'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'zvec_read',
          'description': '按文件路径读取本地记忆中的单条记录全文（路径形如 memories/preferences/主题.md）',
          'parameters': {
            'type': 'object',
            'properties': {
              'pk': {
                'type': 'string',
                'description': '记忆的文件路径，如 preferences/主题.md',
              },
            },
            'required': ['pk'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'zvec_list',
          'description': '列出本地记忆库中的全部记录，用于浏览已保存的知识',
          'parameters': {
            'type': 'object',
            'properties': {
              'limit': {'type': 'integer', 'description': '最大返回条数（默认 20）'},
            },
            'required': [],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'zvec_delete',
          'description': '按文件路径删除本地记忆中的一条记录。注意：此操作不可撤销！',
          'parameters': {
            'type': 'object',
            'properties': {
              'pk': {
                'type': 'string',
                'description': '要删除的记忆文件路径，如 preferences/主题.md',
              },
            },
            'required': ['pk'],
          },
        },
      },
      {
        'type': 'function',
        'function': {
          'name': 'zvec_clear',
          'description': '清空本地记忆库中的所有记录。注意：此操作不可撤销！',
          'parameters': {'type': 'object', 'properties': {}, 'required': []},
        },
      },
    ];
  }

  /// Handle local zvec memory tool calls. Returns null if not a zvec tool.
  Future<String?> _handleZvecToolCall(
    String name,
    Map<String, dynamic> args,
  ) async {
    const zvecTools = [
      'zvec_search',
      'zvec_remember',
      'zvec_read',
      'zvec_list',
      'zvec_delete',
      'zvec_clear',
    ];
    if (!zvecTools.contains(name)) return null;

    ZvecService? svc;
    try {
      final zvecProvider = contextProvider.read<ZvecProvider>();
      if (!zvecProvider.isConfigured) {
        return jsonEncode({'error': 'zvec 未配置'});
      }
      svc = zvecProvider.service;
      if (svc == null) return jsonEncode({'error': 'zvec 服务不可用'});
    } catch (e) {
      return jsonEncode({'error': '读取 zvec 配置失败: $e'});
    }

    try {
      if (name == 'zvec_search') {
        final query = (args['query'] ?? '').toString().trim();
        if (query.isEmpty) return jsonEncode({'error': 'query is required'});
        final zvecProvider = contextProvider.read<ZvecProvider>();
        final threshold =
            (args['score_threshold'] as num?)?.toDouble() ??
            zvecProvider.threshold;
        final limit =
            (args['limit'] as num?)?.toInt() ?? zvecProvider.displayCount;
        final hits = await svc.search(
          query,
          scoreThreshold: threshold,
          limit: limit.clamp(1, 10),
        );
        if (hits.isEmpty) {
          return jsonEncode({
            'success': true,
            'results': <Map<String, dynamic>>[],
            'message': '未找到相关记忆',
          });
        }
        return jsonEncode({
          'success': true,
          'results': hits
              .map(
                (h) => {
                  'path': h.pk,
                  'score': h.score,
                  'snippet': h.snippet,
                  'category': h.category ?? '',
                },
              )
              .toList(),
        });
      }

      if (name == 'zvec_remember') {
        final category = (args['category'] ?? 'entities').toString().trim();
        final title = (args['name'] ?? 'untitled').toString().trim();
        final content = (args['content'] ?? '').toString().trim();
        final sourcePath = (args['path'] ?? '').toString().trim();
        if (content.isEmpty && sourcePath.isEmpty) {
          return jsonEncode({'error': 'content 与 path 至少提供一个'});
        }
        final pk = '$category/${_slug(title)}.md';
        await svc.remember(
          pk: pk,
          content: content.isEmpty ? null : content,
          sourcePath: sourcePath.isEmpty ? null : sourcePath,
          title: title,
          category: category,
        );
        return jsonEncode({'success': true, 'path': pk});
      }

      if (name == 'zvec_read') {
        final pk = (args['pk'] ?? '').toString().trim();
        if (pk.isEmpty) return jsonEncode({'error': 'pk is required'});
        final rec = await svc.read(pk);
        if (rec == null) return jsonEncode({'error': '未找到记录: $pk'});
        return jsonEncode({
          'success': true,
          'path': rec.pk,
          'title': rec.title,
          'category': rec.category,
          'content': rec.content,
        });
      }

      if (name == 'zvec_list') {
        final limit = (args['limit'] as num?)?.toInt() ?? 20;
        final records = await svc.list(limit: limit.clamp(1, 200));
        return jsonEncode({
          'success': true,
          'count': records.length,
          'results': records
              .map(
                (r) => {
                  'path': r.pk,
                  'title': r.title ?? '',
                  'category': r.category ?? '',
                  'snippet': r.content.length > 100
                      ? r.content.substring(0, 100)
                      : r.content,
                },
              )
              .toList(),
        });
      }

      if (name == 'zvec_delete') {
        final pk = (args['pk'] ?? '').toString().trim();
        if (pk.isEmpty) return jsonEncode({'error': 'pk is required'});
        await svc.delete(pk);
        return jsonEncode({'success': true, 'path': pk});
      }

      if (name == 'zvec_clear') {
        await svc.clear();
        return jsonEncode({'success': true});
      }
    } catch (e) {
      return jsonEncode({'error': 'zvec call failed: $e'});
    }

    return jsonEncode({'error': 'zvec tool internal error: unexpected flow'});
  }

  /// 将记忆名称转为安全的主键片段。
  static String _slug(String s) {
    final trimmed = s.trim();
    if (trimmed.isEmpty) return 'untitled';
    final out = trimmed
        .replaceAll(RegExp(r'[\\/:*?"<>|#%&\s]+'), '_')
        .replaceAll(RegExp(r'_+'), '_')
        .replaceAll(RegExp(r'^_|_$'), '');
    return out.isEmpty ? 'untitled' : out;
  }
}
