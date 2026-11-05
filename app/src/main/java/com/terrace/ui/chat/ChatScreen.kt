package com.terrace.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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


import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun ChatScreen(
    sessionId: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToSessions: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val palette = appPalette()

    // 从设置 / 会话整页返回时保留当前会话，只有首次进入才初始化
    LaunchedEffect(sessionId) {
        if (viewModel.getSessionId().isBlank()) viewModel.initSession(sessionId)
    }

    val state = viewModel.uiState
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var drawerProgress by remember { mutableFloatStateOf(0f) }
    var drawerAnimJob by remember { mutableStateOf<Job?>(null) }
    // 容器宽度（px）：把拖动位移换算成 0..1 的抽屉进度
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val drawerWidthPx = containerWidthPx * AppSpacing.drawerWidthFraction

    val showJumpToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < info.totalItemsCount - 1
        }
    }

    suspend fun animateDrawerToSuspend(target: Float) {
        animate(
            initialValue = drawerProgress,
            targetValue = target,
            animationSpec = tween(AppMotion.drawerMs, easing = AppMotion.ease),
        ) { value, _ -> drawerProgress = value }
    }

    fun animateDrawerTo(target: Float) {
        drawerAnimJob?.cancel()
        drawerAnimJob = scope.launch { animateDrawerToSuspend(target) }
    }

    fun closeDrawerThen(action: () -> Unit) {
        drawerAnimJob?.cancel()
        drawerAnimJob = scope.launch {
            animateDrawerToSuspend(0f)
            action()
        }
    }

    fun settleDrawer(velocity: Float) {
        val target = when {
            velocity > 350f -> 1f
            velocity < -350f -> 0f
            drawerProgress >= 0.5f -> 1f
            else -> 0f
        }
        animateDrawerTo(target)
    }

    // 只在完全展开时订阅布尔值，避免拖动过程中整页重组
    val drawerOpen by remember { derivedStateOf { drawerProgress > 0.99f } }

    // 抽屉展开时按返回键先收起抽屉
    BackHandler(enabled = drawerOpen) { animateDrawerTo(0f) }

    // 整屏横向拖动：右滑拉出会话抽屉，左滑推回；竖向滚动仍由列表自己消费
    val dragState = rememberDraggableState { delta ->
        if (drawerWidthPx > 0f) {
            drawerAnimJob?.cancel()
            drawerProgress = (drawerProgress + delta / drawerWidthPx).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(palette.surface)
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    drawerAnimJob?.cancel()
                    keyboard?.hide()
                },
                onDragStopped = { velocity -> settleDrawer(velocity) },
            )
    ) {
        val drawerWidthDp = maxWidth * AppSpacing.drawerWidthFraction
        val cornerPx = with(density) { 24.dp.toPx() }
        val shadowPx = with(density) { AppElevation.drawer.toPx() }

        // 会话面板：常驻在底层，随主界面位移露出
        SessionDrawer(
            sessions = state.allSessions,
            currentId = viewModel.getSessionId(),
            onSelect = {
                closeDrawerThen { viewModel.switchToSession(it) }
            },
            onSettings = {
                closeDrawerThen(onNavigateToSettings)
            },
            onOpenSessionsPage = {
                closeDrawerThen(onNavigateToSessions)
            },
            modifier = Modifier.width(drawerWidthDp).fillMaxHeight(),
        )

        // 主界面：右移 80%、圆角与投影随进度出现
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = drawerWidthPx * drawerProgress
                    shape = RoundedCornerShape(cornerPx * drawerProgress)
                    clip = drawerProgress > 0.001f
                    shadowElevation = shadowPx * drawerProgress
                    ambientShadowColor = Color.Black.copy(alpha = 0.32f)
                    spotShadowColor = Color.Black.copy(alpha = 0.32f)
                }
        ) {
            Scaffold(
                containerColor = palette.surface,
                topBar = {
                    ChatTopBar(
                        title = state.sessionTitle,
                        showTitle = state.messages.isNotEmpty() || state.isLoading,
                        onMenu = {
                            keyboard?.hide()
                            haptics(HapticsType.Light)
                            animateDrawerTo(1f)
                        },
                        onNew = {
                            viewModel.startNewSession()
                            haptics(HapticsType.Light)
                        },
                    )
                },
                bottomBar = {
                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        // 跑着的时候也能打字：这时候发出去就是插话
                        enabled = true,
                        canSend = inputText.isNotBlank() && !state.interjecting,
                        running = state.isLoading,
                        pendingCount = state.pendingCount,
                        onSend = {
                            viewModel.submit(inputText.trim())
                            inputText = ""
                            haptics(HapticsType.Light)
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val displayMessages = state.messages.filter { it.role != "system" }
                    if (displayMessages.isEmpty() && !state.isLoading) {
                        WelcomeScreen()
                    } else {
                        StreamChatList(
                            state = state,
                            listState = listState,
                            onRegenerate = { viewModel.regenerate(); haptics(HapticsType.Light) },
                        )
                    }

                    if (showJumpToBottom) {
                        GoToBottomButton(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            onClick = {
                                scope.launch {
                                    val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                    listState.animateScrollToItem(last)
                                }
                                haptics(HapticsType.Light)
                            },
                        )
                    }

                    val banner = state.error ?: state.notice
                    if (banner != null) {
                        // 插话结果也走这里：成功/已排队都给一句明确回执，用户不用猜
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            action = {
                                TextButton(onClick = {
                                    if (state.error != null) viewModel.clearError()
                                    else viewModel.clearNotice()
                                }) { Text("关闭") }
                            }
                        ) { Text(banner) }
                    }
                }
            }
        }

        // 左边缘保留手势排除区：从屏幕最左侧起手右滑时也不被系统返回手势抢走
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(24.dp)
                .systemGestureExclusion()
        )

        // 打开态：右侧留白点击关闭（左滑推回由整屏横向拖动处理）
        if (drawerOpen) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(1f - AppSpacing.drawerWidthFraction)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptics(HapticsType.Light)
                        animateDrawerTo(0f)
                    }
            )
        }
    }
}

// ==================== 顶栏 ====================

@Composable
private fun ChatTopBar(
    title: String,
    showTitle: Boolean,
    onMenu: () -> Unit,
    onNew: () -> Unit,
) {
    val p = appPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            MenuGlyph(color = p.ink)
        }

        if (showTitle) {
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onNew),
            contentAlignment = Alignment.Center,
        ) {
            PlusGlyph(color = p.ink, size = 26.dp)
        }
    }
}

@Composable
private fun MenuGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.7.dp.toPx()
        drawLine(
            color,
            Offset(4.dp.toPx(), 8.5.dp.toPx()),
            Offset(21.dp.toPx(), 8.5.dp.toPx()),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(4.dp.toPx(), 15.dp.toPx()),
            Offset(15.dp.toPx(), 15.dp.toPx()),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 1.7.dp.toPx()
        val c = this.size.width / 2f
        val r = 8.dp.toPx()
        drawLine(color, Offset(c, c - r), Offset(c, c + r), stroke, StrokeCap.Round)
        drawLine(color, Offset(c - r, c), Offset(c + r, c), stroke, StrokeCap.Round)
    }
}

// ==================== 欢迎页 ====================

@Composable
private fun WelcomeScreen() {
    val p = appPalette()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(AppRadii.welcomeLogo)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF5B84FF), Color(0xFF3358F0), Color(0xFF2C4BE0))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            WhaleLogo(color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "你好，让我们开始聊天吧",
            fontSize = 19.sp,
            color = p.ink,
        )
    }
}

@Composable
private fun WhaleLogo(color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val s = size.minDimension / 24f
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(4 * s, 15 * s)
            cubicTo(7.5f * s, 17.5f * s, 12.5f * s, 17f * s, 15f * s, 13.5f * s)
            cubicTo(16.6f * s, 11.3f * s, 16.8f * s, 8.5f * s, 15.4f * s, 6.3f * s)
            cubicTo(14f * s, 4.1f * s, 11.4f * s, 3.1f * s, 9f * s, 3.7f * s)
        }
        drawPath(
            body, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.7f * s, cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        )
        val tail = androidx.compose.ui.graphics.Path().apply {
            moveTo(4 * s, 15 * s)
            cubicTo(3.3f * s, 17f * s, 4.2f * s, 19.3f * s, 6.2f * s, 20 * s)
        }
        drawPath(
            tail, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.7f * s, cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        )
        val fin = androidx.compose.ui.graphics.Path().apply {
            moveTo(15 * s, 13.5f * s)
            cubicTo(16.4f * s, 14 * s, 17.8f * s, 15.1f * s, 18.4f * s, 16.6f * s)
        }
        drawPath(
            fin, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.7f * s, cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        )
        drawCircle(color, radius = 0.9f * s, center = Offset(9.2f * s, 8.4f * s))
    }
}

// ==================== 底部输入栏 ====================

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    running: Boolean,
    pendingCount: Int,
    onSend: () -> Unit,
) {
    val p = appPalette()
    val focusSource = remember { MutableInteractionSource() }
    val focused by focusSource.collectIsFocusedAsState()
    // 无输入时按钮保留在原位，只是去掉底色（幽灵态）
    val sendBg by animateColorAsState(
        targetValue = if (canSend) p.brand else Color.Transparent,
        animationSpec = tween(AppMotion.fastMs, easing = AppMotion.ease),
        label = "sendBg",
    )
    val sendTint by animateColorAsState(
        targetValue = when {
            canSend -> if (p.brand.luminance() > 0.6f) Color(0xFF10121A) else Color.White
            else -> p.ink4
        },
        animationSpec = tween(AppMotion.fastMs, easing = AppMotion.ease),
        label = "sendTint",
    )
    // 跑着的时候边框换个色，暗示「现在发出去是插话」
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> p.brand
            running -> p.brand.copy(alpha = 0.45f)
            else -> p.divider.copy(alpha = 0.7f)
        },
        animationSpec = tween(AppMotion.fastMs, easing = AppMotion.ease),
        label = "inputBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
    ) {
        Surface(
            shape = AppRadii.input,
            color = p.surface,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = AppElevation.soft,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 13.dp, bottom = 9.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        interactionSource = focusSource,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = p.ink,
                        ),
                        cursorBrush = SolidColor(p.brand),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp)
                            .padding(vertical = 3.dp),
                    )
                    if (value.isEmpty()) {
                        Text(
                            if (running) "这轮还在跑 · 发送即插话" else "发消息或按住说话",
                            fontSize = 16.sp,
                            color = p.ink3.copy(alpha = 0.55f),
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (running) {
                        // 进行中：告诉用户这句话会去哪（插进本轮，插不进就排到后面）
                        Text(
                            if (pendingCount > 0) "运行中 · 已排队 $pendingCount 条" else "运行中",
                            fontSize = 12.sp,
                            color = p.ink3,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    Box(
                        Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlusGlyph(color = p.ink, size = 25.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(sendBg)
                            .clickable(enabled = canSend, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = if (running) "插话" else "发送",
                            modifier = Modifier.size(20.dp),
                            tint = sendTint,
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun GoToBottomButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = appPalette()
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(p.surface)
            .border(1.dp, p.divider.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "回到底部",
            tint = p.ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ==================== 用户气泡 ====================

@Composable
internal fun UserBubble(msg: Message) {
    val p = appPalette()
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (msg.interjected) {
            // 中途插进去的话：标一下，免得回看时分不清哪些是打断说的
            Box(
                Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .clip(CircleShape)
                    .background(p.brandSoft)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("插话", fontSize = 11.sp, color = p.brandInk)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End, unbounded = false),
                shape = AppRadii.userBubble,
                color = p.bubble,
            ) {
                SelectionContainer {
                    Text(
                        msg.content,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = p.ink,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FooterIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = appPalette()
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            icon, contentDescription = label, modifier = Modifier.size(16.dp),
            tint = p.ink3.copy(alpha = 0.8f),
        )
    }
}

@Composable
internal fun StreamingDots() {
    val p = appPalette()
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(3.dp))
            val transition = rememberInfiniteTransition(label = "streamDots$i")
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
                label = "dotAlpha$i",
            )
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(p.ink2.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
internal fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Text(
        "▍", fontSize = 15.sp, color = color.copy(alpha = alpha),
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ==================== Markdown 渲染 ====================

@Composable
internal fun MarkdownBody(content: String) {
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
    val p = appPalette()
    val line = p.divider
    val headerBg = p.field.copy(alpha = 0.7f)
    val bodyBg = p.code
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
                            if (ri != rows.lastIndex) {
                                val y = size.height - stroke / 2f
                                drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
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
            }
        }
    }
}

// ==================== Token 统计底部 ====================

@Composable
internal fun TokenFooter(
    cacheHit: Int, cacheMiss: Int, sessionOut: Int,
    totalCost: Double, family: String, balance: String, symbol: String,
) {
    val p = appPalette()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadii.card)
            .background(p.field.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = p.brand,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Token 消耗 · ${family.ifBlank { "未知模型" }}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$symbol${"%.4f".format(totalCost)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.brand,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp)
                .height(1.dp)
                .background(p.divider.copy(alpha = 0.7f))
        )
        TokenRow("输入 · 缓存命中", "${"%,d".format(cacheHit)} tokens")
        TokenRow("输入 · 缓存未命中", "${"%,d".format(cacheMiss)} tokens")
        TokenRow("输出", "${"%,d".format(sessionOut)} tokens")
        if (balance.isNotBlank()) {
            TokenRow("余额", balance)
        }
    }
}

@Composable
private fun TokenRow(label: String, value: String) {
    val p = appPalette()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = p.ink3.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = TextStyle(
                fontSize = 11.sp,
                fontFeatureSettings = "tnum",
                color = p.ink3.copy(alpha = 0.7f),
            ),
        )
    }
}

// ==================== 会话面板 ====================

@Composable
private fun SessionDrawer(
    sessions: List<ChatSession>,
    currentId: String,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
    onOpenSessionsPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = appPalette()
    Column(
        modifier
            .clip(AppRadii.drawer)
            .background(p.surface)
    ) {
        SessionSearchBar(onClick = onOpenSessionsPage)
        SessionListBody(
            sessions = sessions,
            currentId = currentId,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        SessionAccountRow(onSettings = onSettings)
    }
}