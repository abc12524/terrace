package com.terrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terrace.ui.chat.ChatScreen
import com.terrace.ui.chat.ChatViewModel
import com.terrace.ui.permission.LocalNetworkPermissionBanner
import com.terrace.ui.permission.rememberLocalNetworkPermissionState
import com.terrace.ui.sessions.SessionsScreen
import com.terrace.ui.settings.SettingsScreen
import com.terrace.ui.theme.AppMotion
import com.terrace.ui.theme.ShellToolTheme

/** 覆盖在对话页之上的整页（从右推入、向右推出）。 */
private enum class OverlayPage { Sessions, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark = isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }

            ShellToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val chatViewModel: ChatViewModel = viewModel()
                    // Android 17+ 访问内网需运行时授权，进入 App 即请求一次
                    val localNetwork = rememberLocalNetworkPermissionState()
                    // 覆盖页栈：对话页常驻底层，设置 / 会话整页压在它上面，退出时原样露出
                    var pages by remember { mutableStateOf<List<OverlayPage>>(emptyList()) }

                    BackHandler(enabled = pages.isNotEmpty()) {
                        pages = pages.dropLast(1)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        LocalNetworkPermissionBanner(
                            granted = localNetwork.granted,
                            onRequest = localNetwork::request
                        )
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                            // 上层整页出现时，对话页向左收 1/4 屏，形成推拉层次
                            val parallax by animateFloatAsState(
                                targetValue = if (pages.isEmpty()) 0f else 1f,
                                animationSpec = tween(AppMotion.settingsMs, easing = AppMotion.ease),
                                label = "pageParallax",
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = -widthPx * 0.25f * parallax }
                            ) {
                                ChatScreen(
                                    sessionId = "new",
                                    onNavigateToSettings = {
                                        pages = pages + OverlayPage.Settings
                                    },
                                    onNavigateToSessions = {
                                        pages = pages + OverlayPage.Sessions
                                    },
                                    viewModel = chatViewModel,
                                )
                            }

                            OverlayPageHost(visible = pages.contains(OverlayPage.Sessions)) {
                                val chatState = chatViewModel.uiState
                                SessionsScreen(
                                    sessions = chatState.allSessions,
                                    currentId = chatViewModel.getSessionId(),
                                    onSelect = { id ->
                                        chatViewModel.switchToSession(id)
                                        pages = emptyList()
                                    },
                                    onSettings = { pages = pages + OverlayPage.Settings },
                                )
                            }

                            OverlayPageHost(visible = pages.contains(OverlayPage.Settings)) {
                                SettingsScreen(onBack = { pages = pages.dropLast(1) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 覆盖页容器：进入时从右侧整屏推入，退出时向右推回并淡出。
 * 保持在上层，退出动画期间下层的对话页才不会被它挡住。
 */
@Composable
private fun OverlayPageHost(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = pushEnter(),
        exit = popExit(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // 吞掉落在页面空白处的点击，避免穿透到底层对话页
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        ) {
            content()
        }
    }
}

// ==================== 页面转场 ====================

private fun pageTween() = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = AppMotion.settingsMs,
    easing = AppMotion.ease,
)

/** 推入：整页从右侧滑进来。 */
private fun pushEnter(): EnterTransition = slideInHorizontally(
    animationSpec = pageTween(),
    initialOffsetX = { it },
)

/** 推出：整页向右滑出并淡出。 */
private fun popExit(): ExitTransition = slideOutHorizontally(
    animationSpec = pageTween(),
    targetOffsetX = { it },
) + fadeOut(tween(AppMotion.settingsMs))
