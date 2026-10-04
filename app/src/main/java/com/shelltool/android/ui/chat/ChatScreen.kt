package com.shelltool.android.ui.chat

import dev.jeziellago.compose.markdowntext.MarkdownText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelltool.android.data.model.ChatSession
import com.shelltool.android.data.model.Message
import com.shelltool.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) { viewModel.initSession(sessionId) }

    val state = viewModel.uiState
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()

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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .height(40.dp)
                ) {
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.open() }
                            haptics(HapticsType.Light)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "会话列表",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }

                    if (state.messages.isNotEmpty()) {
                        Text(state.sessionTitle,
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp))
                    }

                    IconButton(
                        onClick = { viewModel.startNewSession(); haptics(HapticsType.Light) },
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新对话",
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    val canSend = inputText.isNotBlank() && !state.isLoading
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    enabled = !state.isLoading,
                                    textStyle = TextStyle(fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)
                                )
                                if (inputText.isEmpty()) {
                                    Text("发消息", fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                    .clickable(enabled = canSend) {
                                        viewModel.sendMessage(inputText.trim())
                                        inputText = ""
                                        haptics(HapticsType.Light)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "发送",
                                    modifier = Modifier.size(22.dp),
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.messages.isEmpty() && !state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SelectionContainer {
                                Text("有什么想聊的?", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("通过 shell-tool 服务执行命令、搜索与记忆。", fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                        }
                    }
                } else {
                    val displayMessages = state.messages.filter { it.role != "system" }
                    val chatItems = remember(displayMessages) { groupChatItems(displayMessages) }
                    val lastRealIndex = (chatItems.size - 1).coerceAtLeast(0)

                    LaunchedEffect(chatItems.size, state.isLoading) {
                        if (!state.isLoading && chatItems.isNotEmpty()) {
                            listState.animateScrollToItem(lastRealIndex)
                        }
                    }
                    LaunchedEffect(state.streamingContent.length) {
                        if (state.isLoading) {
                            listState.scrollToItem(chatItems.size)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)
                    ) {
                        items(chatItems.size, key = { index ->
                            when (val it = chatItems[index]) {
                                is ChatItem.Bubble -> it.msg.id
                                is ChatItem.Reason -> it.key
                                is ChatItem.ToolRun -> it.key
                            }
                        }) { index ->
                            when (val it = chatItems[index]) {
                                is ChatItem.Bubble -> SelectionContainer { MessageBubble(it.msg) }
                                is ChatItem.Reason -> ReasoningCard(
                                    reasoning = it.msg.reasoningContent.orEmpty(),
                                    title = "深度思考",
                                )
                                is ChatItem.ToolRun -> SelectionContainer { ToolRunCard(it.steps) }
                            }
                        }
                        if (state.isLoading) {
                            item(key = "streaming") {
                                StreamingReply(
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

// ==================== Token 统计底部 ====================

@Composable
private fun TokenFooter(
    cacheHit: Int, cacheMiss: Int, sessionOut: Int,
    totalCost: Double, family: String, balance: String, symbol: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("📊 会话累计 Token · ${family.ifBlank { "未知模型" }} · 合计 $symbol${"%.4f".format(totalCost)}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            TokenCostRow("输入(缓存命中)", cacheHit)
            TokenCostRow("输入(缓存未命中)", cacheMiss)
            TokenCostRow("输出", sessionOut)
            Spacer(Modifier.height(4.dp))
            Text("  当前余额：${balance.ifBlank { "无" }}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TokenCostRow(label: String, tokens: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = color)
        Text("${"%,d".format(tokens)} tokens", fontSize = 11.sp, color = color)
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
            Text("Shell Tool",
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

// ==================== 流式回复 ====================

@Composable
private fun StreamingReply(content: String, reasoning: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        if (reasoning.isNotBlank()) {
            StreamingReasoning(reasoning, modifier = Modifier.padding(bottom = 4.dp))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .animateContentSize(tween(220)),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = 4.dp, bottomEnd = 16.dp,
            ),
            color = BubbleAssistant,
        ) {
            if (content.isBlank()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StreamingDots()
                    Spacer(Modifier.width(10.dp))
                    Text("正在思考…", fontSize = 13.sp, color = BubbleAssistantText.copy(alpha = 0.6f))
                }
            } else {
                val blocks = remember(content) {
                    try { markdownBlocks(content) } catch (_: Exception) { listOf(MdBlock.Text(content)) }
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    blocks.forEachIndexed { index, block ->
                        when (block) {
                            is MdBlock.Text -> MarkdownText(
                                markdown = block.text,
                                style = TextStyle(color = BubbleAssistantText, fontSize = 15.sp),
                                syntaxHighlightColor = Color(0x80FFEB3B),
                            )
                            is MdBlock.Table -> MarkdownTable(
                                table = block.table,
                                textStyle = TextStyle(color = BubbleAssistantText, fontSize = 15.sp),
                            )
                        }
                        if (index != blocks.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                    BlinkingCursor(color = cs.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "streamDots")
    val alpha = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(BubbleAssistantText.copy(alpha = alpha.value)),
            )
        }
    }
}

@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha = transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Text(
        "▍",
        fontSize = 15.sp,
        color = color.copy(alpha = alpha.value),
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ==================== 消息气泡 ====================

@Composable
fun MessageBubble(msg: Message) {
    val isUser = msg.role == "user"
    val bc = if (isUser) BubbleUser else BubbleAssistant
    val tc = if (isUser) BubbleUserText else BubbleAssistantText

    Column(Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (msg.reasoningContent != null && msg.reasoningContent.isNotBlank()) {
            ReasoningCard(msg.reasoningContent, Modifier.padding(bottom = 4.dp))
        }

        Surface(
            modifier = if (isUser)
                Modifier.fillMaxWidth(0.92f).wrapContentWidth(Alignment.End, unbounded = false)
            else
                Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp),
            color = bc
        ) {
            val blocks = remember(msg.content) { markdownBlocks(msg.content) }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is MdBlock.Text -> MarkdownText(
                            markdown = block.text,
                            style = TextStyle(color = tc, fontSize = 15.sp),
                            syntaxHighlightColor = Color(0x80FFEB3B)
                        )
                        is MdBlock.Table -> MarkdownTable(
                            table = block.table,
                            textStyle = TextStyle(color = tc, fontSize = 15.sp)
                        )
                    }
                    if (index != blocks.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (isUser || (msg.role == "assistant" && msg.content.isNotBlank())) {
            Text(fmtTime(msg.timestamp), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

// ==================== 深度思考折叠卡片 ====================

@Composable
private fun ReasoningCard(
    reasoning: String,
    modifier: Modifier = Modifier,
    title: String = "深度思考",
    icon: ImageVector = Icons.Outlined.Psychology,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "reasonChevron")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(200)),
        shape = AppRadii.card,
        color = if (cs.surface.luminance() < 0.5f) Color(0xFF2B2B2E) else BubbleAssistant,
        shadowElevation = AppElevation.soft,
        border = BorderStroke(0.8.dp, cs.outlineVariant.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = cs.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp).rotate(rotation),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    reasoning,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StreamingReasoning(reasoning: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(200)),
        shape = AppRadii.card,
        color = cs.primaryContainer.copy(alpha = if (dark) 0.25f else 0.30f),
        shadowElevation = AppElevation.soft,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, tint = cs.tertiary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("深度思考",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface)
                Spacer(Modifier.weight(1f))
                BlinkingCursor(color = cs.primary.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            Text(reasoning,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                color = cs.onSurfaceVariant,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis)
        }
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

private sealed class ChatItem {
    data class Bubble(val msg: Message) : ChatItem()
    data class Reason(val msg: Message) : ChatItem() {
        val key: Long get() = msg.id
    }
    data class ToolRun(val steps: List<Message>) : ChatItem() {
        val key: Long get() = steps.first().id
    }
}

private fun groupChatItems(messages: List<Message>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        val isToolTurn = m.role == "assistant" && m.toolCalls != null && m.content.isBlank()
        if (isToolTurn) {
            if (!m.reasoningContent.isNullOrBlank()) {
                out.add(ChatItem.Reason(m))
            }
            val tools = mutableListOf<Message>()
            var j = i + 1
            while (j < messages.size && messages[j].role == "tool") {
                tools.add(messages[j]); j++
            }
            if (tools.any { it.toolName != null }) {
                out.add(ChatItem.ToolRun(tools))
            }
            i = j
            continue
        } else if (m.role == "tool") {
            out.add(ChatItem.ToolRun(listOf(m)))
            i++
            continue
        }
        out.add(ChatItem.Bubble(m))
        i++
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
