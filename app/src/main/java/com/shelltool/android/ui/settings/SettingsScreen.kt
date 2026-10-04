package com.shelltool.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelltool.android.BuildConfig
import com.shelltool.android.data.AppPreferences
import com.shelltool.android.data.api.ShellToolClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var host by remember { mutableStateOf(AppPreferences.host) }
    var port by remember { mutableStateOf(AppPreferences.port) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        AppPreferences.host = host
        AppPreferences.port = port
        saved = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
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
                    }, enabled = !testing && host.isNotBlank()) {
                        Text("保存并测试")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("服务端", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; saved = false; testMsg = null },
                        label = { Text("Host") },
                        placeholder = { Text("192.168.1.10") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { v -> port = v.filter { it.isDigit() }; saved = false; testMsg = null },
                            label = { Text("Port") },
                            placeholder = { Text("8000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(12.dp))
                        val preview = run {
                            val h = host.trim().removeSuffix("/")
                            if (h.isBlank()) "" else {
                                val s = if (h.startsWith("http")) h else "http://$h"
                                "$s:${port.trim()}"
                            }
                        }
                        Text(preview, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { save() }, enabled = !saved && host.isNotBlank()) {
                            Text(if (saved) "已保存" else "保存")
                        }
                        if (testing) {
                            CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                        }
                        if (testMsg != null && !testing) {
                            Text(testMsg!!, fontSize = 12.sp,
                                color = if (testOk) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("仅需填写运行 server/api.py 的主机与端口，例如 192.168.1.10:8000。",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("说明", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• 对话通过 /chat/stream 流式接口调用 shell-tool 服务端\n" +
                            "• 对话历史仅保存在本机 SQLite，方便随时回看，不向服务端请求历史\n" +
                            "• 新对话会在服务端新开会话（等价 -n）",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("关于", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(6.dp))
                    Text("Shell Tool Android · 版本 ${BuildConfig.VERSION_NAME}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
