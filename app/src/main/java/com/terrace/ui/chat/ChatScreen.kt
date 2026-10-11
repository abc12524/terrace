package com.terrace.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTableBasicText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import com.terrace.data.model.ChatSession
import com.terrace.data.model.Message
import com.terrace.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(sessionId) { viewModel.initSession(sessionId) }

    // 打开侧边栏时收起输入法，避免键盘压住抽屉底部（如「设置」）
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) keyboard?.hide()
    }

    val state = viewModel.uiState
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()

    val showJumpToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < info.totalItemsCount - 1
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = true,
        drawerContent = {
            SessionDrawer(
                state.allSessions, viewModel.getSessionId(),
                onSelect = { scope.launch { drawerState.close() }; viewModel.switchToSession(it) },
                onNew = { scope.launch { drawerState.close() }; viewModel.startNewSession() },
                onSettings = { scope.launch { drawerState.close() }; onNavigateToSettings() }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = state.sessionTitle,
                    subtitle = state.model,
                    showTitle = state.messages.isNotEmpty() || state.isLoading,
                    onMenu = {
                        keyboard?.hide()
                        scope.launch { drawerState.open() }
                        haptics(HapticsType.Light)
                    },
                    onNew = { viewModel.startNewSession(); haptics(HapticsType.Light) },
                )
            },
            bottomBar = {
                ChatInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !state.isLoading,
                    canSend = inputText.isNotBlank() && !state.isLoading,
                    onSend = {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                        haptics(HapticsType.Light)
                    },
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val displayMessages = state.messages.filter { it.role != "system" }
                val chatItems = remember(displayMessages) { groupChatItems(displayMessages) }
                if (displayMessages.isEmpty() && !state.isLoading) {
                    WelcomeScreen(onPick = { inputText = it })
                } else {
                    val lastIndex = (chatItems.size - 1).coerceAtLeast(0)

                    LaunchedEffect(chatItems.size, state.isLoading) {
                        if (!state.isLoading && chatItems.isNotEmpty()) {
                            listState.animateScrollToItem(lastIndex)
                        }
                    }
                    LaunchedEffect(state.streamingContent.length, state.streamingReasoning.length, chatItems.size) {
                        if (state.isLoading) listState.scrollToItem(chatItems.size)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
                    ) {
                        items(chatItems.size, key = { chatItems[it].key }) { index ->
                            when (val item = chatItems[index]) {
                                is ChatItem.User -> UserBubble(item.msg)
                                is ChatItem.Assistant -> AssistantCell(
                                    msg = item.msg,
                                    tools = item.tools,
                                    onRegenerate = { viewModel.regenerate(); haptics(HapticsType.Light) },
                                )
                                is ChatItem.Tools -> ToolFragmentGroup(item.steps)
                            }
                        }
                        if (state.isLoading) {
                            item(key = "streaming") {
                                StreamingAssistant(
                                    content = state.streamingContent,
                                    reasoning = state.streamingReasoning,
                                )
                            }
                        }
                        if (state.messages.any { it.role == "assistant" }) {
                            item(key = "token_footer") {
                                TokenFooter(
                                    cacheHit = state.totalHit,
                                    cacheMiss = state.totalMiss,
                                    sessionOut = state.totalOut,
                                    totalCost = state.totalCost,
                                    family = state.model,
                                    balance = state.balance,
                                    symbol = state.symbol,
                                )
                            }
                        }
                    }
                }

                if (showJumpToBottom) {
                    GoToBottomButton(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        onClick = {
                            scope.launch { listState.animateScrollToItem(chatItems.size) }
                            haptics(HapticsType.Light)
                        },
                    )
                }

                if (state.error != null) {
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = { TextButton(onClick = { viewModel.clearError() }) { Text("关闭") } }
                    ) { Text(state.error) }
                }
            }
        }
    }
}

// ==================== 顶栏 ====================

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    showTitle: Boolean,
    onMenu: () -> Unit,
    onNew: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .height(44.dp)
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(cs.onSurface.copy(alpha = 0.04f))
                .border(1.dp, cs.onSurface.copy(alpha = 0.06f), CircleShape)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "会话列表",
                modifier = Modifier.size(20.dp), tint = cs.onSurface)
        }

        if (showTitle) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = cs.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = cs.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }

        IconButton(
            onClick = onNew,
            modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新对话",
                modifier = Modifier.size(26.dp), tint = cs.onSurface)
        }
    }
}

// ==================== 欢迎页 ====================

private val welcomeSuggestions = listOf(
    "列出当前目录下的文件",
    "看看系统资源占用情况",
    "帮我搜索并总结一个话题",
)

@Composable
private fun WelcomeScreen(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("T", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }
        Spacer(Modifier.height(18.dp))
        Text("有什么想聊的？", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "通过 shell-tool 服务执行命令、搜索与记忆",
            fontSize = 14.sp, color = cs.onSurfaceVariant.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(30.dp))
        welcomeSuggestions.forEach { suggestion ->
            Surface(
                onClick = { onPick(suggestion) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = AppRadii.card,
                color = cs.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(suggestion, fontSize = 14.sp, color = cs.onSurface,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = cs.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ==================== 底部输入栏 ====================

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            // 键盘弹出时把输入栏顶上去；用 union 取较大值，避免与导航栏内边距叠加
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, cs.onSurface.copy(alpha = 0.08f)),
            shadowElevation = AppElevation.soft,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(fontSize = 16.sp, color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    )
                    if (value.isEmpty()) {
                        Text("发消息", fontSize = 16.sp,
                            color = cs.onSurface.copy(alpha = 0.4f))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.06f)
                        )
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowUpward, contentDescription = "发送",
                        modifier = Modifier.size(22.dp),
                        tint = if (canSend) cs.onPrimary else cs.onSurface.copy(alpha = 0.28f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoToBottomButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(cs.surface)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "回到底部",
            tint = cs.onSurface, modifier = Modifier.size(22.dp))
    }
}

// ==================== 用户气泡 ====================

@Composable
private fun UserBubble(msg: Message) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).wrapContentWidth(Alignment.End, unbounded = false),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                bottomStart = 18.dp, bottomEnd = 4.dp),
            color = cs.primaryContainer,
        ) {
            SelectionContainer {
                Text(
                    msg.content,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        Text(
            fmtTime(msg.timestamp), fontSize = 10.sp,
            color = cs.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ==================== 助手消息单元 ====================

@Composable
private fun AssistantCell(
    msg: Message,
    tools: List<Message>,
    onRegenerate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val reasoning = msg.reasoningContent.orEmpty()
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    val hasTools = tools.any { it.toolName != null }
    if (msg.content.isBlank() && reasoning.isBlank() && !hasTools) return

    Column(Modifier.fillMaxWidth()) {
        if (reasoning.isNotBlank()) {
            ThinkingBlock(reasoning)
            Spacer(Modifier.height(10.dp))
        }
        if (msg.content.isNotBlank()) {
            MarkdownBody(msg.content)
        }
        if (hasTools) {
            if (msg.content.isNotBlank()) Spacer(Modifier.height(10.dp))
            ToolFragmentGroup(tools)
        }
        if (msg.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FooterIcon(Icons.Outlined.ContentCopy, "复制") {
                    clipboard.setText(AnnotatedString(msg.content))
                    haptics(HapticsType.Light)
                }
                FooterIcon(Icons.Outlined.Refresh, "重新生成", onRegenerate)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(msg.timestamp), fontSize = 10.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

@Composable
private fun ThinkingBlock(reasoning: String) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "thinkChevron")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadii.card)
            .background(cs.surfaceVariant.copy(alpha = 0.4f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(tween(200)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Psychology, contentDescription = null,
                tint = cs.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "深度思考" else "已深度思考",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "收起" else "展开",
                tint = cs.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp).rotate(rotation))
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                reasoning, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp, color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolFragmentGroup(steps: List<Message>) {
    val cs = MaterialTheme.colorScheme
    val entries = steps
        .filter { it.role == "tool" && it.toolName != null }
        .map { ToolCallEntry(it.toolName!!, buildToolDetail(it)) }
    if (entries.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<List<ToolCallEntry>?>(null) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "toolChevron")

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(200)),
        shape = AppRadii.card,
        color = cs.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, cs.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(vertical = 2.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Build, contentDescription = null,
                    tint = cs.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("已运行 ${entries.size} 个工具", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = cs.onSurface,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = cs.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp).rotate(rotation))
            }
            if (expanded) {
                entries.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { detail = listOf(entry) }
                            .padding(start = 36.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.name, fontSize = 13.sp, color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "查看详情",
                            tint = cs.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    detail?.let { list ->
        val title = if (list.size == 1) "工具调用: ${list[0].name}" else "工具调用 (${list.size})"
        val body = list.joinToString("\n\n") { "工具调用: ${it.name}\n${it.detail}" }
        DetailSheet(title = title, body = body, onDismiss = { detail = null })
    }
}

// ==================== 流式回复 ====================

@Composable
private fun StreamingAssistant(content: String, reasoning: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (reasoning.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().animateContentSize(tween(200)),
                shape = AppRadii.card,
                color = cs.primaryContainer.copy(alpha = 0.35f),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, contentDescription = null,
                            tint = cs.tertiary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("正在深度思考…", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        Spacer(Modifier.weight(1f))
                        BlinkingCursor(color = cs.primary.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        reasoning, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp, color = cs.onSurfaceVariant,
                        maxLines = 8, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (content.isNotBlank()) Spacer(Modifier.height(10.dp))
        }

        if (content.isBlank() && reasoning.isBlank()) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamingDots()
                Spacer(Modifier.width(10.dp))
                Text("正在思考…", fontSize = 13.sp, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
            }
        } else if (content.isNotBlank()) {
            MarkdownBody(content)
            Spacer(Modifier.height(4.dp))
            BlinkingCursor(color = cs.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "streamDots")
    val alpha = transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(3.dp))
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha.value)),
            )
        }
    }
}

@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha = transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Text("▍", fontSize = 15.sp, color = color.copy(alpha = alpha.value),
        modifier = Modifier.padding(top = 2.dp))
}

// ==================== Markdown 渲染 ====================

@Composable
private fun MarkdownBody(content: String) {
    val components = remember {
        markdownComponents(table = { model -> AutoSizedTable(model) })
    }
    val state = rememberMarkdownState(content = content, retainState = true)
    val typography = chatMarkdownTypography()
    SelectionContainer {
        Markdown(
            markdownState = state,
            components = components,
            typography = typography,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 收窄 m3 默认的标题字号（默认 h1=displayLarge=57sp，正文里过大）。 */
@Composable
private fun chatMarkdownTypography(): MarkdownTypography {
    val base = MaterialTheme.typography.bodyLarge
    return markdownTypography(
        h1 = base.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        h2 = base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        h3 = base.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        h4 = base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        h5 = base.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        h6 = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** 内容自适应的 markdown 表格：外边框 + 行分隔线，列宽按单元格文本估算。 */
@Composable
private fun AutoSizedTable(model: MarkdownComponentModel) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline.copy(alpha = 0.6f)
    val headerBg = cs.surfaceVariant.copy(alpha = 0.55f)
    val bodyBg = cs.surfaceVariant.copy(alpha = 0.22f)
    val shape = RoundedCornerShape(10.dp)
    val src = model.content
    val style = model.typography.table
    val density = LocalDensity.current

    val rows = remember(model.node) {
        model.node.children
            .filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
            .map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    }
    val colCount = rows.maxOfOrNull { it.size } ?: 0

    val colWidths = remember(model.node, src, density) {
        val em = with(density) { 14.sp.toPx() }
        val pad = with(density) { 26.dp.toPx() }
        val minW = with(density) { 44.dp.toPx() }
        val maxW = with(density) { 260.dp.toPx() }
        Array(colCount) { c ->
            var w = em * 2f
            rows.forEach { r ->
                r.getOrNull(c)?.let { cell ->
                    var cw = 0f
                    for (ch in src.substring(cell.startOffset, cell.endOffset)) {
                        cw += if (ch.code > 0x2E80) em else em * 0.55f
                    }
                    if (cw > w) w = cw
                }
            }
            (w + pad).coerceIn(minW, maxW)
        }
    }

    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(
            Modifier
                .border(1.dp, line, shape)
                .clip(shape)
                .background(bodyBg),
        ) {
            rows.forEachIndexed { ri, cells ->
                Row(
                    Modifier
                        .background(if (ri == 0) headerBg else Color.Transparent)
                        .drawBehind {
                            val stroke = 0.8f.dp.toPx()
                            var x = 0f
                            for (i in 0 until cells.size - 1) {
                                x += colWidths.getOrElse(i) { 0f }
                                drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                            }
                        },
                ) {
                    cells.forEachIndexed { ci, cell ->
                        Box(
                            Modifier
                                .width(with(density) { colWidths.getOrElse(ci) { 0f }.toDp() })
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            MarkdownTableBasicText(
                                content = src,
                                cell = cell,
                                style = if (ri == 0) style.copy(fontWeight = FontWeight.Bold) else style,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
                if (ri != rows.lastIndex) {
                    HorizontalDivider(color = line, thickness = 0.6.dp)
                }
            }
        }
    }
}

// ==================== Token 统计底部 ====================

@Composable
private fun TokenFooter(
    cacheHit: Int, cacheMiss: Int, sessionOut: Int,
    totalCost: Double, family: String, balance: String, symbol: String,
) {
    val cs = MaterialTheme.colorScheme
    val muted = cs.onSurfaceVariant.copy(alpha = 0.7f)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(36.dp).height(1.dp)
                .background(cs.outlineVariant.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "${family.ifBlank { "未知模型" }} · 合计 $symbol${"%.4f".format(totalCost)}",
            fontSize = 11.sp, color = muted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "缓存命中 ${"%,d".format(cacheHit)} · 未命中 ${"%,d".format(cacheMiss)} · 输出 ${"%,d".format(sessionOut)} tokens",
            fontSize = 11.sp, color = muted,
        )
        if (balance.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("余额 $balance", fontSize = 11.sp, color = muted)
        }
    }
}

// ==================== 侧边栏会话列表 ====================

@Composable
private fun SessionDrawer(
    sessions: List<ChatSession>, currentId: String,
    onSelect: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val validSessions = sessions.filter { it.messageCount > 0 }
    val groups = groupSessions(validSessions)

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = cs.surface,
        drawerContentColor = cs.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("terrace",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = cs.onSurface)
            Text("命令行 AI 助手 · 本地历史", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.55f))
            Spacer(Modifier.height(14.dp))
            NewChatPill(onClick = onNew)
        }

        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            groups.forEach { (label, list) ->
                item(key = "hdr_$label") {
                    Text(label,
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant.copy(alpha = 0.8f))
                }
                items(list, key = { it.id }) { session ->
                    SessionTile(
                        session = session,
                        active = session.id == currentId,
                        onClick = { onSelect(session.id) },
                    )
                }
                item(key = "gap_$label") { Spacer(Modifier.height(6.dp)) }
            }
        }

        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
        DrawerFooterRow(
            icon = Icons.Outlined.Settings,
            label = "设置",
            onClick = onSettings,
        )
    }
}

@Composable
private fun NewChatPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) cs.primary.copy(alpha = 0.78f) else cs.primary,
        label = "newChat",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppRadii.card)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null) {
                haptics(HapticsType.Medium); onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = cs.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text("新对话", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = cs.onPrimary)
    }
}

@Composable
private fun SessionTile(session: ChatSession, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            active -> cs.primary.copy(alpha = 0.12f)
            pressed -> cs.onSurface.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        label = "sessionTile",
    )
    val title = if (session.title != "新对话") session.title else fmtSessionTime(session.createdAt)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(AppRadii.card)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null) {
                haptics(HapticsType.Light); onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${session.messageCount} 条", fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.55f))
                if (session.title != "新对话") {
                    Text(" · ${fmtSessionTime(session.createdAt)}", fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.45f))
                }
            }
        }
        if (active) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(cs.primary))
        }
    }
}

@Composable
private fun DrawerFooterRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) cs.onSurface.copy(alpha = 0.06f) else Color.Transparent,
        label = "footer",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(interactionSource = interaction, indication = null) {
                haptics(HapticsType.Light); onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, color = cs.onSurfaceVariant)
    }
}

// ==================== 工具函数 ====================

private fun fmtTime(ms: Long) =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun fmtSessionTime(ms: Long): String {
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().also { it.timeInMillis = ms }
    return when {
        sameDay(now, d) -> "今天 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))}"
        isYest(now, d)  -> "昨天 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))}"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ms))
    }
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYest(now: Calendar, d: Calendar): Boolean {
    val y = Calendar.getInstance().also { it.timeInMillis = now.timeInMillis - 86400000L }
    return sameDay(y, d)
}

// ==================== 消息分组 ====================

private sealed interface ChatItem {
    val key: Any
    data class User(val msg: Message) : ChatItem {
        override val key: Any get() = msg.id
    }
    data class Assistant(val msg: Message, val tools: List<Message>) : ChatItem {
        override val key: Any get() = msg.id
    }
    data class Tools(val steps: List<Message>) : ChatItem {
        override val key: Any get() = steps.first().id
    }
}

private fun groupChatItems(messages: List<Message>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        when (m.role) {
            "tool" -> {
                out.add(ChatItem.Tools(listOf(m)))
                i++
            }
            "assistant" -> {
                val tools = mutableListOf<Message>()
                var j = i + 1
                while (j < messages.size && messages[j].role == "tool") {
                    tools.add(messages[j]); j++
                }
                out.add(ChatItem.Assistant(m, tools))
                i = j
            }
            else -> {
                out.add(ChatItem.User(m))
                i++
            }
        }
    }
    return out
}

private data class SG(val label: String, val sessions: List<ChatSession>)

private fun groupSessions(all: List<ChatSession>): List<SG> {
    val now = Calendar.getInstance()
    val weekStart = Calendar.getInstance().also {
        it.add(Calendar.DAY_OF_WEEK, it.firstDayOfWeek - it.get(Calendar.DAY_OF_WEEK))
        it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
        it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
    }
    val t = mutableListOf<ChatSession>(); val y = mutableListOf<ChatSession>()
    val w = mutableListOf<ChatSession>(); val e = mutableListOf<ChatSession>()
    for (s in all.sortedByDescending { it.createdAt }) {
        val c = Calendar.getInstance().also { it.timeInMillis = s.createdAt }
        when {
            sameDay(now, c) -> t.add(s)
            isYest(now, c) -> y.add(s)
            c.timeInMillis >= weekStart.timeInMillis -> w.add(s)
            else -> e.add(s)
        }
    }
    val r = mutableListOf<SG>()
    if (t.isNotEmpty()) r.add(SG("今天", t))
    if (y.isNotEmpty()) r.add(SG("昨天", y))
    if (w.isNotEmpty()) r.add(SG("本周", w))
    if (e.isNotEmpty()) r.add(SG("更早", e))
    return r
}
