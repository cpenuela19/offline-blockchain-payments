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
import com.g22.offline_blockchain_payments.ui.components.DrawerMenu
import com.g22.offline_blockchain_payments.ui.screens.HomeScreen
import com.g22.offline_blockchain_payments.ui.theme.OfflineblockchainpaymentsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineblockchainpaymentsTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerMenu(
                            onSendClick = {
                                scope.launch { drawerState.close() }
                                // Navegar a pantalla de enviar (por implementar)
                            },
                            onReceiveClick = {
                                scope.launch { drawerState.close() }
                                // Navegar a pantalla de recibir (por implementar)
                            },
                            onSwapClick = {
                                scope.launch { drawerState.close() }
                                // Navegar a pantalla de swap (por implementar)
                            },
                            onHistoryClick = {
                                scope.launch { drawerState.close() }
                                // Navegar a pantalla de histórico (por implementar)
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
                    HomeScreen(
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        },
                        onSendClick = {
                            // Navegar a pantalla de enviar (por implementar)
                        },
                        onReceiveClick = {
                            // Navegar a pantalla de recibir (por implementar)
                        },
                        onSwapClick = {
                            // Navegar a pantalla de swap (por implementar)
                        }
                    )
                }
            }
        }
    }
}