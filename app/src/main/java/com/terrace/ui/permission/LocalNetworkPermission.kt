package com.terrace.ui.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Android 17（API 37）起，访问局域网地址（192.168.x.x / 10.x.x.x 等内网设备）
 * 需要运行时权限 ACCESS_LOCAL_NETWORK。
 *
 * 低版本系统上该权限不存在，直接视为已授权，不弹任何授权框。
 */
const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** 需要该权限的最低系统版本（Android 17）。 */
const val LOCAL_NETWORK_PERMISSION_SDK = 37

/** 当前系统是否要求局域网运行时权限。 */
val requiresLocalNetworkPermission: Boolean
    get() = Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK

/** 当前进程是否已获得局域网访问权限（低版本系统恒为 true）。 */
fun Context.hasLocalNetworkPermission(): Boolean =
    !requiresLocalNetworkPermission ||
        ContextCompat.checkSelfPermission(this, PERMISSION_ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

/** 权限状态 + 主动请求的能力。 */
class LocalNetworkPermissionState internal constructor(
    val granted: Boolean,
    private val requestBlock: () -> Unit
) {
    /** 再次拉起系统授权框（用户点「授权」时调用）。 */
    fun request() = requestBlock()
}

/**
 * 记住局域网权限状态：首次进入时自动请求一次，
 * 之后可通过 [LocalNetworkPermissionState.request] 重新请求。
 */
@Composable
fun rememberLocalNetworkPermissionState(autoRequest: Boolean = true): LocalNetworkPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocalNetworkPermission()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result ->
        // 回调结果与实际授权状态再核对一次，避免部分 ROM 返回不准
        granted = result || context.hasLocalNetworkPermission()
    }

    LaunchedEffect(Unit) {
        if (autoRequest && !granted) {
            launcher.launch(PERMISSION_ACCESS_LOCAL_NETWORK)
        }
    }

    return remember(granted) {
        LocalNetworkPermissionState(granted) {
            if (!context.hasLocalNetworkPermission()) {
                launcher.launch(PERMISSION_ACCESS_LOCAL_NETWORK)
            }
        }
    }
}

/** 未授权时显示的内网访问提示条，避免用户只看到「连接失败」。 */
@Composable
fun LocalNetworkPermissionBanner(
    granted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (granted) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "未获得局域网访问权限", style = MaterialTheme.typography.labelLarge)
                Text(text = "无法连接内网 shell-tool 服务", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRequest) {
                Text("授权")
            }
        }
    }
}
