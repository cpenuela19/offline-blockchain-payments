package com.g22.offline_blockchain_payments

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.g22.offline_blockchain_payments.ui.components.DrawerMenu
import com.g22.offline_blockchain_payments.ui.screens.HomeScreen
import com.g22.offline_blockchain_payments.ui.screens.HistoryScreen
import com.g22.offline_blockchain_payments.ui.screens.ReceiveScreen
import com.g22.offline_blockchain_payments.ui.screens.SendScreen
import com.g22.offline_blockchain_payments.ui.screens.SwapScreen
import com.g22.offline_blockchain_payments.ui.theme.OfflineblockchainpaymentsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineblockchainpaymentsTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerMenu(
                            onSendClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("send")
                            },
                            onReceiveClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("receive")
                            },
                            onSwapClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("swap")
                            },
                            onHistoryClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("history")
                            },
                            onSettingsClick = {
                                scope.launch { drawerState.close() }
                                // Navegar a pantalla de ajustes (por implementar)
                            },
                            onLogoutClick = {
                                scope.launch { drawerState.close() }
                                // Implementar lógica de cerrar sesión
                            }
                        )
                    },
                    scrimColor = Color.Black.copy(alpha = 0.5f)
                ) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                },
                                onSendClick = {
                                    navController.navigate("send")
                                },
                                onReceiveClick = {
                                    navController.navigate("receive")
                                },
                                onSwapClick = {
                                    navController.navigate("swap")
                                },
                                onHistoryClick = {
                                    navController.navigate("history")
                                }
                            )
                        }
                        
                        composable("send") {
                            SendScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("receive") {
                            ReceiveScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("history") {
                            HistoryScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("swap") {
                            SwapScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}