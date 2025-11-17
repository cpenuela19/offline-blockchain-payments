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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.g22.offline_blockchain_payments.ble.repository.BleRepository
import com.g22.offline_blockchain_payments.ble.viewmodel.PaymentBleViewModel
import com.g22.offline_blockchain_payments.ui.components.DrawerMenu
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.screens.*
import com.g22.offline_blockchain_payments.ui.theme.OfflineblockchainpaymentsTheme
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModel
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModelFactory
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bleRepository: BleRepository
    private lateinit var paymentBleViewModel: PaymentBleViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar BLE Repository
        bleRepository = BleRepository(applicationContext)
        
        // Inicializar SyncWorker
        SyncWorker.enqueue(this)
        
        setContent {
            OfflineblockchainpaymentsTheme {
                // Crear ViewModel BLE (se mantiene durante toda la sesión)
                paymentBleViewModel = remember { PaymentBleViewModel(bleRepository) }
                
                // ViewModel para el saldo de AgroPuntos
                val walletViewModel: WalletViewModel = viewModel()
                val availablePoints by walletViewModel.availablePoints.collectAsState()
                val pendingPoints by walletViewModel.pendingPoints.collectAsState()
                
                // ViewModel para vouchers (para el test de settle)
                val voucherViewModel: VoucherViewModel = viewModel(
                    factory = VoucherViewModelFactory(applicationContext as android.app.Application)
                )
                
                // Snackbar para mostrar resultados del test
                val snackbarHostState = remember { SnackbarHostState() }
                
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var currentRole by remember { mutableStateOf(Role.BUYER) }
                        var buyerAmount by remember { mutableStateOf(0L) }
                        var sellerAmount by remember { mutableStateOf(0L) }
                        var currentTransactionId by remember { mutableStateOf("") }
                
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
                            },
                            voucherViewModel = voucherViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    },
                    scrimColor = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = "initial_choice") {
                        composable("initial_choice") {
                            InitialChoiceScreen(
                                onSellClick = {
                                    navController.navigate("seller/charge")
                                },
                                onBuyClick = {
                                    navController.navigate("buyer/start")
                                },
                                availablePoints = availablePoints,
                                pendingPoints = pendingPoints
                            )
                        }
                        
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
                        
                        // Flujo Comprador - Ahora integrado con BLE
                        composable("buyer/start") {
                            SendScreen(
                                viewModel = paymentBleViewModel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPaymentSuccess = { transactionId, amount ->
                                    // Guardar datos reales de la transacción
                                    currentTransactionId = paymentBleViewModel.currentTransactionId.value ?: transactionId
                                    buyerAmount = amount
                                    
                                    // Crear voucher con settle (offline con firmas)
                                    val paymentTx = paymentBleViewModel.paymentTransaction.value
                                    voucherViewModel.createSettledVoucher(
                                        role = com.g22.offline_blockchain_payments.ui.data.Role.BUYER,
                                        amountAp = amount,
                                        counterparty = paymentTx?.receiverName ?: "Vendedor",
                                        offerId = transactionId
                                    )
                                    
                                    // Descontar puntos del comprador
                                    walletViewModel.deductPoints(amount)
                                    
                                    navController.navigate("buyer/receipt")
                                }
                            )
                        }
                        
                        composable("buyer/confirm") {
                            BuyerConfirmScreen(
                                amount = buyerAmount,
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
                val concept = paymentBleViewModel.paymentTransaction.value?.concept
                BuyerReceiptScreen(
                    amount = buyerAmount,
                    from = "Juan P.",
                    to = "Marta",
                    transactionId = currentTransactionId,
                    concept = concept,
                    onSave = {
                        // Guardar voucher (opcional - in-memory)
                    },
                    onClose = {
                        navController.navigate("initial_choice") {
                            popUpTo("initial_choice") { inclusive = true }
                        }
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
                        
                        // Flujo Vendedor - Ahora integrado con BLE
                        composable("seller/charge") {
                            ReceiveScreen(
                                viewModel = paymentBleViewModel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPaymentReceived = { amount, transactionId ->
                                    android.util.Log.d("MainActivity", "🎯 onPaymentReceived called: amount=$amount, transactionId=$transactionId")
                                    // Guardar datos reales de la transacción
                                    currentTransactionId = paymentBleViewModel.currentTransactionId.value ?: transactionId
                                    sellerAmount = amount
                                    
                                    // Crear voucher con settle (offline con firmas)
                                    val paymentTx = paymentBleViewModel.paymentTransaction.value
                                    voucherViewModel.createSettledVoucher(
                                        role = com.g22.offline_blockchain_payments.ui.data.Role.SELLER,
                                        amountAp = amount,
                                        counterparty = paymentTx?.senderName ?: "Comprador",
                                        offerId = transactionId
                                    )
                                    
                                    // Agregar puntos pendientes al vendedor
                                    walletViewModel.addPendingPoints(amount)
                                    
                                    android.util.Log.d("MainActivity", "📱 Navigating to seller/receipt...")
                                    navController.navigate("seller/receipt")
                                    android.util.Log.d("MainActivity", "✅ Navigation command sent")
                                }
                            )
                        }
                        
            composable("seller/receipt") {
                val concept = paymentBleViewModel.paymentTransaction.value?.concept
                SellerReceiptScreen(
                    amount = sellerAmount,
                    from = "Juan P.",
                    to = "Marta",
                    transactionId = currentTransactionId,
                    concept = concept,
                    onSave = {
                        // Guardar voucher (opcional - in-memory)
                    },
                    onClose = {
                        navController.navigate("initial_choice") {
                            popUpTo("initial_choice") { inclusive = true }
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
                        
                        // Snackbar para mostrar resultados del test
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos BLE
        if (::bleRepository.isInitialized) {
            bleRepository.stopGattServer()
            bleRepository.disconnect()
        }
    }
}