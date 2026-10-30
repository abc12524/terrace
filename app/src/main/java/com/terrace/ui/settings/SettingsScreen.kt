package com.terrace.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terrace.BuildConfig
import com.terrace.data.AppPreferences
import com.terrace.data.ServerProfile
import com.terrace.data.api.ShellToolClient
import com.terrace.ui.theme.HapticsType
import com.terrace.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

// ==================== iOS 配色 ====================

private data class IosPalette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val groupTitle: Color,
    val separator: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val icon: Color,
    val navBar: Color,
)

@Composable
private fun rememberIosPalette(): IosPalette {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) IosPalette(
            bg = Color(0xFF000000),
            card = Color(0xFF1C1C1E),
            text = Color(0xFFFFFFFF),
            secondary = Color(0xFF8E8E93),
            groupTitle = Color(0xFF8E8E93),
            separator = Color(0x99545488),
            blue = Color(0xFF0A84FF),
            green = Color(0xFF30D158),
            red = Color(0xFFFF453A),
            icon = Color(0xFF5A5A5E),
            navBar = Color(0xFF1C1C1E),
        ) else IosPalette(
            bg = Color(0xFFF2F2F7),
            card = Color(0xFFFFFFFF),
            text = Color(0xFF000000),
            secondary = Color(0xFF8A8A8E),
            groupTitle = Color(0xFF6D6D72),
            separator = Color(0x4A3C3C43),
            blue = Color(0xFF007AFF),
            green = Color(0xFF34C759),
            red = Color(0xFFFF3B30),
            icon = Color(0xFFC6C6C8),
            navBar = Color(0xFFF9F9F9),
        )
    }
}

// ==================== 设置页 ====================

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var host by remember { mutableStateOf(AppPreferences.host) }
    var port by remember { mutableStateOf(AppPreferences.port) }
    var name by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf(AppPreferences.servers()) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val ios = rememberIosPalette()

    fun save() {
        if (host.isBlank()) return
        AppPreferences.saveServer(
            ServerProfile(name = name.trim().ifBlank { host.trim() }, host = host, port = port)
        )
        host = AppPreferences.host
        port = AppPreferences.port
        name = AppPreferences.servers().firstOrNull { it.key == AppPreferences.activeKey() }?.name.orEmpty()
        servers = AppPreferences.servers()
    }

    fun switchTo(p: ServerProfile) {
        AppPreferences.switchTo(p)
        host = AppPreferences.host
        port = AppPreferences.port
        name = p.name
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

    Scaffold(
        containerColor = ios.bg,
        topBar = { IosNavBar(ios = ios, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                IosGroup(ios = ios, title = "服务端连接") {
                    IosFormRow(
                        label = "名称", value = name, ios = ios, placeholder = "可选",
                        onValueChange = { name = it; testMsg = null },
                    )
                    IosFormRow(
                        label = "Host", value = host, ios = ios, placeholder = "192.168.1.10",
                        onValueChange = { host = it; testMsg = null },
                    )
                    IosFormRow(
                        label = "端口", value = port, ios = ios, placeholder = "8000",
                        onValueChange = { v -> port = v.filter { it.isDigit() }; testMsg = null },
                        keyboardType = KeyboardType.Number, showDivider = false,
                    )
                }
                if (testMsg != null && !testing) {
                    IosFeedback(ok = testOk, message = testMsg!!, ios = ios)
                }
            }

            IosGroup(ios = ios) {
                IosActionRow(
                    text = if (testing) "测试中…" else "保存并测试",
                    enabled = !testing && host.isNotBlank(),
                    loading = testing,
                    ios = ios,
                ) { haptics(HapticsType.Light); testConnection() }
            }

            if (servers.isNotEmpty()) {
                IosGroup(ios = ios, title = "已保存的服务端") {
                    servers.forEachIndexed { i, p ->
                        IosServerRow(
                            profile = p,
                            active = p.key == AppPreferences.activeKey(),
                            ios = ios,
                            showDivider = i != servers.lastIndex,
                            onClick = { haptics(HapticsType.Light); switchTo(p) },
                            onDelete = {
                                haptics(HapticsType.Light)
                                AppPreferences.removeServer(p)
                                servers = AppPreferences.servers()
                            },
                        )
                    }
                }
            }

            IosGroup(ios = ios) {
                IosAboutRow(ios = ios)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==================== 导航栏 ====================

@Composable
private fun IosNavBar(ios: IosPalette, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ios.navBar)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
                    .height(44.dp)
                    .padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回",
                    tint = ios.blue, modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text("返回", fontSize = 17.sp, color = ios.blue)
            }
            Text(
                "设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.text,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ios.separator))
    }
}

// ==================== 分组容器 ====================

@Composable
private fun IosGroup(
    ios: IosPalette,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title, fontSize = 13.sp, color = ios.groupTitle,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = ios.card,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun IosSeparator(ios: IosPalette) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).padding(start = 16.dp).background(ios.separator))
}

// ==================== 表单行 ====================

@Composable
private fun IosFormRow(
    label: String,
    value: String,
    ios: IosPalette,
    placeholder: String,
    onValueChange: (String) -> Unit,
    showDivider: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 17.sp, color = ios.text)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = ios.text, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(ios.blue),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 17.sp, color = ios.secondary)
                        }
                        inner()
                    }
                },
            )
        }
        if (showDivider) IosSeparator(ios)
    }
}

// ==================== 操作行 ====================

@Composable
private fun IosActionRow(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    ios: IosPalette,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed && enabled) ios.text.copy(alpha = 0.06f) else Color.Transparent
    Box(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .heightIn(min = 46.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ios.blue,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 17.sp, color = if (enabled) ios.blue else ios.secondary)
        }
    }
}

// ==================== 连接反馈 ====================

@Composable
private fun IosFeedback(ok: Boolean, message: String, ios: IosPalette) {
    val tint = if (ok) ios.green else ios.red
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(message, fontSize = 13.sp, color = tint)
    }
}

// ==================== 服务端条目 ====================

@Composable
private fun IosServerRow(
    profile: ServerProfile,
    active: Boolean,
    ios: IosPalette,
    showDivider: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed) ios.text.copy(alpha = 0.06f) else Color.Transparent

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { profile.display },
                    fontSize = 17.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = ios.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.name.isNotBlank()) {
                    Text(
                        profile.display, fontSize = 13.sp, color = ios.secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (active) {
                Icon(Icons.Filled.Check, contentDescription = "当前", tint = ios.blue, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline, contentDescription = "删除",
                    tint = ios.icon, modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) IosSeparator(ios)
    }
}

// ==================== 关于 ====================

@Composable
private fun IosAboutRow(ios: IosPalette) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF4AA3FF), ios.blue))),
            contentAlignment = Alignment.Center,
        ) {
            Text("T", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text("terrace", fontSize = 17.sp, color = ios.text)
            Text("版本 ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = ios.secondary)
        }
    }
}