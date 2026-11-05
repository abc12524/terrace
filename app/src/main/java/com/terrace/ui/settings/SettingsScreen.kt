package com.terrace.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terrace.BuildConfig
import com.terrace.data.AppPreferences
import com.terrace.data.ServerProfile
import com.terrace.data.api.ShellToolClient
import com.terrace.ui.theme.*
import kotlinx.coroutines.launch

// ==================== 设置页 ====================

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var host by remember { mutableStateOf(AppPreferences.host) }
    var port by remember { mutableStateOf(AppPreferences.port) }
    var accessKey by remember { mutableStateOf(AppPreferences.accessKey) }
    var name by remember {
        mutableStateOf(
            AppPreferences.servers()
                .firstOrNull { it.key == AppPreferences.activeKey() }
                ?.name.orEmpty()
        )
    }
    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf(AppPreferences.servers()) }
    var showServer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val p = appPalette()

    fun save() {
        if (host.isBlank()) return
        AppPreferences.saveServer(
            ServerProfile(
                name = name.trim().ifBlank { host.trim() },
                host = host,
                port = port,
                accessKey = accessKey,
            )
        )
        host = AppPreferences.host
        port = AppPreferences.port
        accessKey = AppPreferences.accessKey
        name = AppPreferences.servers()
            .firstOrNull { it.key == AppPreferences.activeKey() }?.name.orEmpty()
        servers = AppPreferences.servers()
    }

    fun switchTo(profile: ServerProfile) {
        AppPreferences.switchTo(profile)
        host = AppPreferences.host
        port = AppPreferences.port
        accessKey = AppPreferences.accessKey
        name = profile.name
        testMsg = null
        servers = AppPreferences.servers()
    }

    fun testConnection() {
        save()
        scope.launch {
            testing = true
            testMsg = null
            ShellToolClient().health().fold(
                onSuccess = { testOk = true; testMsg = "连接成功" },
                onFailure = { testOk = false; testMsg = it.message ?: "连接失败" }
            )
            testing = false
        }
    }

    fun stepBack() {
        if (showServer) showServer = false else onBack()
    }

    BackHandler(enabled = showServer) { showServer = false }

    Scaffold(
        containerColor = p.settingsBg,
        topBar = {
            SettingsTopBar(
                title = if (showServer) "服务端连接" else "设置",
                onBack = {
                    haptics(HapticsType.Light)
                    stepBack()
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = showServer,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                if (targetState) {
                    (
                        slideInHorizontally(
                            animationSpec = tween(AppMotion.settingsMs, easing = AppMotion.ease)
                        ) { it / 4 } + fadeIn(tween(AppMotion.settingsMs))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(AppMotion.settingsMs, easing = AppMotion.ease)
                        ) { -it / 4 } + fadeOut(tween(AppMotion.settingsMs))
                        )
                } else {
                    (
                        slideInHorizontally(
                            animationSpec = tween(AppMotion.settingsMs, easing = AppMotion.ease)
                        ) { -it / 4 } + fadeIn(tween(AppMotion.settingsMs))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(AppMotion.settingsMs, easing = AppMotion.ease)
                        ) { it / 4 } + fadeOut(tween(AppMotion.settingsMs))
                        )
                }
            },
            label = "settingsPage",
        ) { serverPage ->
            if (serverPage) {
                ServerPage(
                    name = name,
                    host = host,
                    port = port,
                    accessKey = accessKey,
                    testing = testing,
                    testMsg = testMsg,
                    testOk = testOk,
                    onNameChange = { name = it; testMsg = null },
                    onHostChange = { host = it; testMsg = null },
                    onPortChange = { v -> port = v.filter { it.isDigit() }; testMsg = null },
                    onAccessKeyChange = { v -> accessKey = v.trim(); testMsg = null },
                    onSaveTest = { haptics(HapticsType.Light); testConnection() },
                )
            } else {
                SettingsRootPage(
                    servers = servers,
                    host = host,
                    port = port,
                    hasAccessKey = accessKey.isNotBlank(),
                    onOpenServer = {
                        haptics(HapticsType.Light)
                        showServer = true
                    },
                    onSwitch = { haptics(HapticsType.Light); switchTo(it) },
                    onDelete = {
                        haptics(HapticsType.Light)
                        AppPreferences.removeServer(it)
                        servers = AppPreferences.servers()
                    },
                )
            }
        }
    }
}

// ==================== 顶栏 ====================

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    val p = appPalette()
    Box(
        Modifier
            .fillMaxWidth()
            .background(p.settingsBg)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(p.card)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = p.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Crossfade(
            targetState = title,
            animationSpec = tween(AppMotion.fastMs),
            label = "settingsTitle",
            modifier = Modifier.align(Alignment.Center),
        ) { text ->
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        }
        Spacer(Modifier.align(Alignment.CenterEnd).width(52.dp))
    }
}

// ==================== 根页 ====================

@Composable
private fun SettingsRootPage(
    servers: List<ServerProfile>,
    host: String,
    port: String,
    hasAccessKey: Boolean,
    onOpenServer: () -> Unit,
    onSwitch: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
) {
    val p = appPalette()
    val activeDisplay = if (host.isBlank()) "未配置" else "$host:$port"
    val activeKey = ServerProfile.keyOf(host, port)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        GroupTitle("连接")
        SettingsGroup {
            ConnectionRow(
                value = activeDisplay,
                hasAccessKey = hasAccessKey,
                onClick = onOpenServer,
            )
        }

        GroupTitle("已保存的服务端")
        SettingsGroup {
            if (servers.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "尚未保存服务端",
                        fontSize = 14.sp,
                        color = p.ink3,
                    )
                }
            } else {
                servers.forEach { profile ->
                    ServerRow(
                        profile = profile,
                        active = profile.key == activeKey,
                        onClick = { onSwitch(profile) },
                        onDelete = { onDelete(profile) },
                    )
                }
            }
        }

        GroupTitle("关于")
        SettingsGroup {
            AboutRow()
        }

        Footnote("对话历史仅保存在本机 SQLite\n不向服务端读取历史记录")
    }
}

@Composable
private fun GroupTitle(text: String) {
    val p = appPalette()
    Text(
        text,
        Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp),
        fontSize = 14.sp,
        color = p.ink3,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val p = appPalette()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadii.group)
            .background(p.card)
            .padding(vertical = 2.dp),
        content = content,
    )
}

@Composable
private fun SettingsRowBase(
    selected: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val p = appPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        selected -> p.brandSoft
        pressed && onClick != null -> p.ink.copy(alpha = 0.04f)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ConnectionRow(value: String, hasAccessKey: Boolean, onClick: () -> Unit) {
    val p = appPalette()
    SettingsRowBase(selected = false, onClick = onClick) {
        ServerGlyph(color = p.ink, size = 23.dp)
        Spacer(Modifier.width(15.dp))
        Text(
            "服务端连接",
            fontSize = 16.sp,
            color = p.ink,
        )
        if (hasAccessKey) {
            Spacer(Modifier.width(8.dp))
            CodeTag()
        }
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 14.sp,
            color = p.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Chevron(p.ink3.copy(alpha = 0.6f))
    }
}

/** 「验证码」小标签：一眼看出这个连接是带访问码的 */
@Composable
private fun CodeTag() {
    val p = appPalette()
    Box(
        Modifier
            .clip(CircleShape)
            .background(p.ink.copy(alpha = 0.06f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text("验证码", fontSize = 11.sp, color = p.ink3)
    }
}

@Composable
private fun ServerRow(
    profile: ServerProfile,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = appPalette()
    val tint = if (active) p.brandInk else p.ink
    SettingsRowBase(selected = active, onClick = if (active) null else onClick) {
        ServerGlyph(color = tint, size = 23.dp)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name.ifBlank { profile.display },
                fontSize = 16.sp,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.display,
                    fontSize = 12.sp,
                    color = if (active) p.brandInk.copy(alpha = 0.7f) else p.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.hasCode) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· 验证码",
                        fontSize = 12.sp,
                        color = if (active) p.brandInk.copy(alpha = 0.7f) else p.ink3.copy(alpha = 0.8f),
                    )
                }
            }
        }
        if (active) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "当前",
                tint = p.brand,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = p.ink4,
                    modifier = Modifier.size(18.dp),
                )
            }
            Chevron(p.ink3.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun AboutRow() {
    val p = appPalette()
    SettingsRowBase(selected = false, onClick = null) {
        Box(
            Modifier
                .size(40.dp)
                .clip(AppRadii.appIcon)
                .background(p.brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "t",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (p.brand.luminance() > 0.6f) Color(0xFF10121A) else Color.White,
            )
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text("terrace", fontSize = 16.sp, color = p.ink)
            Text(
                "版本 ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = p.ink3,
            )
        }
    }
}

// ==================== 二级页：服务端连接 ====================

@Composable
private fun ServerPage(
    name: String,
    host: String,
    port: String,
    accessKey: String,
    testing: Boolean,
    testMsg: String?,
    testOk: Boolean,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onAccessKeyChange: (String) -> Unit,
    onSaveTest: () -> Unit,
) {
    val p = appPalette()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        GroupTitle("服务端连接")
        SettingsGroup {
            ServerField(
                label = "名称",
                value = name,
                placeholder = "可选",
                onValueChange = onNameChange,
            )
            FieldDivider()
            ServerField(
                label = "Host",
                value = host,
                placeholder = "192.168.1.10",
                onValueChange = onHostChange,
            )
            FieldDivider()
            ServerField(
                label = "端口",
                value = port,
                placeholder = "8000",
                onValueChange = onPortChange,
                keyboardType = KeyboardType.Number,
            )
            FieldDivider()
            ServerField(
                label = "访问验证码",
                value = accessKey,
                placeholder = "服务端没配就留空",
                onValueChange = onAccessKeyChange,
                masked = true,
            )
        }

        Footnote(
            "验证码即服务端 .env 的 API_ACCESS_KEY（至少 8 位）\n" +
                "留空表示该服务端不校验；每个服务端各存一份，切换时一起切换"
        )

        Button(
            onClick = onSaveTest,
            enabled = !testing && host.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = p.brand,
                contentColor = if (p.brand.luminance() > 0.6f) Color(0xFF10121A) else Color.White,
                disabledContainerColor = p.brand.copy(alpha = 0.45f),
                disabledContentColor = (if (p.brand.luminance() > 0.6f) Color(0xFF10121A) else Color.White)
                    .copy(alpha = 0.6f),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            if (testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = if (p.brand.luminance() > 0.6f) Color(0xFF10121A) else Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (testing) "测试中…" else "保存并测试",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (testMsg != null && !testing) {
            TestNote(ok = testOk, message = testMsg)
        }

        Footnote("修改后自动保存到本机")
    }
}

@Composable
private fun ServerField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
) {
    val p = appPalette()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.ink3)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(AppRadii.field)
                .background(p.field.copy(alpha = 0.55f))
                .border(
                    width = if (focused) 1.6.dp else 1.dp,
                    color = if (focused) p.brand else p.divider,
                    shape = AppRadii.field,
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = p.ink,
                    // 验证码逐字符核对，等宽更好读
                    fontFamily = if (masked) FontFamily.Monospace else FontFamily.Default,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (masked && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(p.brand),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (masked && value.isNotEmpty()) Modifier.padding(end = 40.dp) else Modifier),
            )
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = p.ink3.copy(alpha = 0.7f))
            }
            if (masked && value.isNotEmpty()) {
                // 手机上没有物理键盘，看不清容易输错，给个显式开关
                Text(
                    text = if (revealed) "隐藏" else "显示",
                    fontSize = 12.sp,
                    color = p.ink3,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable { revealed = !revealed }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FieldDivider() {
    val p = appPalette()
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.divider.copy(alpha = 0.6f))
    )
}

@Composable
private fun TestNote(ok: Boolean, message: String) {
    val p = appPalette()
    val tint = if (ok) p.success else p.danger
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(message, fontSize = 12.sp, color = tint)
        }
    }
}

@Composable
private fun Footnote(text: String) {
    val p = appPalette()
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        color = p.ink3.copy(alpha = 0.6f),
    )
}

// ==================== 线性图标 ====================

@Composable
private fun ServerGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = 1.7f * s
        drawRoundRect(
            color = color,
            topLeft = Offset(3 * s, 4 * s),
            size = Size(18 * s, 12 * s),
            cornerRadius = CornerRadius(2 * s, 2 * s),
            style = Stroke(width = stroke),
        )
        drawLine(
            color,
            Offset(8 * s, 20 * s),
            Offset(16 * s, 20 * s),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun Chevron(color: Color) {
    Canvas(Modifier.size(17.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.38f, h * 0.25f), Offset(w * 0.62f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.62f, h * 0.5f), Offset(w * 0.38f, h * 0.75f), stroke, StrokeCap.Round)
    }
}