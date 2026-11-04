package com.terrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.terrace.ui.chat.ChatScreen
import com.terrace.ui.chat.ChatViewModel
import com.terrace.ui.permission.LocalNetworkPermissionBanner
import com.terrace.ui.permission.rememberLocalNetworkPermissionState
import com.terrace.ui.sessions.SessionsScreen
import com.terrace.ui.settings.SettingsScreen
import com.terrace.ui.theme.ShellToolTheme

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
                    val navController = rememberNavController()
                    val chatViewModel: ChatViewModel = viewModel()
                    // Android 17+ 访问内网需运行时授权，进入 App 即请求一次
                    val localNetwork = rememberLocalNetworkPermissionState()

                    Column(modifier = Modifier.fillMaxSize()) {
                        LocalNetworkPermissionBanner(
                            granted = localNetwork.granted,
                            onRequest = localNetwork::request
                        )
                        NavHost(
                            navController = navController,
                            startDestination = "chat",
                            modifier = Modifier.weight(1f)
                        ) {
                            composable("chat") {
                                ChatScreen(
                                    sessionId = "new",
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToSessions = { navController.navigate("sessions") },
                                    viewModel = chatViewModel,
                                )
                            }
                            composable("sessions") {
                                val chatState = chatViewModel.uiState
                                SessionsScreen(
                                    sessions = chatState.allSessions,
                                    currentId = chatViewModel.getSessionId(),
                                    onSelect = { id ->
                                        chatViewModel.switchToSession(id)
                                        navController.popBackStack()
                                    },
                                    onSettings = { navController.navigate("settings") },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}