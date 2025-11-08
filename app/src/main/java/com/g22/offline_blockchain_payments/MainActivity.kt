package com.g22.offline_blockchain_payments

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.g22.offline_blockchain_payments.worker.SyncWorker
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.g22.offline_blockchain_payments.ui.components.DrawerMenu
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.screens.*
import com.g22.offline_blockchain_payments.ui.theme.OfflineblockchainpaymentsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar SyncWorker
        SyncWorker.enqueue(this)
        
        setContent {
            OfflineblockchainpaymentsTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var currentRole by remember { mutableStateOf(Role.BUYER) }
                var sellerAmount by remember { mutableStateOf(0L) }
                
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerMenu(
                            onSendClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("buyer/start")
                            },
                            onReceiveClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("seller/charge")
                            },
                            onSwapClick = {
                                scope.launch { drawerState.close() }
                                // Mantener swap por ahora
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
                    NavHost(navController = navController, startDestination = "home_minimal") {
                        composable("home_minimal") {
                            HomeMinimal(
                                currentRole = currentRole,
                                onRoleChange = { currentRole = it },
                                onBuyClick = {
                                    navController.navigate("buyer/start")
                                },
                                onSellClick = {
                                    navController.navigate("seller/charge")
                                },
                                onHistoryClick = {
                                    navController.navigate("history")
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        // Flujo Comprador
                        composable("buyer/start") {
                            BuyerStartScreen(
                                onPayClick = {
                                    navController.navigate("buyer/confirm")
                                },
                                onBack = {
                                    navController.popBackStack()
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        composable("buyer/confirm") {
                            BuyerConfirmScreen(
                                amount = 12000L,
                                destination = "Marta",
                                onConfirm = {
                                    navController.navigate("buyer/receipt")
                                },
                                onBack = {
                                    navController.popBackStack()
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        composable("buyer/receipt") {
                            BuyerReceiptScreen(
                                amount = 12000L,
                                from = "Juan P.",
                                to = "Marta",
                                onSave = {
                                    // Guardar voucher (opcional - in-memory)
                                },
                                onClose = {
                                    navController.navigate("home_minimal") {
                                        popUpTo("home_minimal") { inclusive = true }
                                    }
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        // Flujo Vendedor
                        composable("seller/charge") {
                            SellerChargeScreen(
                                onContinue = { amount ->
                                    sellerAmount = amount
                                    navController.navigate("seller/receipt")
                                },
                                onBack = {
                                    navController.popBackStack()
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        composable("seller/receipt") {
                            SellerReceiptScreen(
                                amount = sellerAmount,
                                from = "Juan P.",
                                to = "Marta",
                                onSave = {
                                    // Guardar voucher (opcional - in-memory)
                                },
                                onClose = {
                                    navController.navigate("home_minimal") {
                                        popUpTo("home_minimal") { inclusive = true }
                                    }
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                        
                        // History
                        composable("history") {
                            HistoryScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}