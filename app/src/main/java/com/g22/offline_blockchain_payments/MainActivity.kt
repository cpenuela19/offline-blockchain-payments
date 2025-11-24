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
import androidx.compose.runtime.collectAsState
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
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletSetupViewModel
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletUnlockViewModel
import com.g22.offline_blockchain_payments.data.wallet.WalletManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    private var walletViewModelRef: WalletViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar BLE Repository
        bleRepository = BleRepository(applicationContext)
        
        // Inicializar SyncWorker
        SyncWorker.enqueue(this)
        
        // Observar lifecycle para refrescar balance cuando la app vuelve del background
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // App vuelve del background - refrescar balance real
                walletViewModelRef?.refreshRealBalance()
            }
        })
        
        setContent {
            OfflineblockchainpaymentsTheme {
                // Crear ViewModel BLE (se mantiene durante toda la sesión)
                paymentBleViewModel = remember { PaymentBleViewModel(bleRepository) }
                
                // ViewModel para el saldo de AgroPuntos
                val walletViewModel: WalletViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return WalletViewModel(application) as T
                        }
                    }
                )
                // Guardar referencia para lifecycle observer
                walletViewModelRef = walletViewModel
                
                val availablePoints by walletViewModel.availablePoints.collectAsState()
                val pendingPoints by walletViewModel.pendingPoints.collectAsState()
                
                // ViewModel para vouchers (para el test de settle)
                val voucherViewModel: VoucherViewModel = viewModel(
                    factory = VoucherViewModelFactory(applicationContext as android.app.Application)
                )
                
                // ViewModels para wallet setup y unlock
                val walletSetupViewModel: WalletSetupViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return WalletSetupViewModel(application) as T
                        }
                    }
                )
                val walletUnlockViewModel: WalletUnlockViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return WalletUnlockViewModel(application) as T
                        }
                    }
                )
                
                // Snackbar para mostrar resultados del test
                val snackbarHostState = remember { SnackbarHostState() }
                
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                var currentRole by remember { mutableStateOf(Role.BUYER) }
                val scope = rememberCoroutineScope()
                var buyerAmount by remember { mutableStateOf(0L) }
                var sellerAmount by remember { mutableStateOf(0L) }
                var currentTransactionId by remember { mutableStateOf("") }
                
                // Verificar estado del wallet al iniciar
                var walletChecked by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!walletChecked) {
                        walletChecked = true
                        val walletExists = WalletManager.walletExists(applicationContext)
                        val walletUnlocked = WalletManager.isWalletUnlocked()
                        
                        when {
                            !walletExists -> {
                                // No hay wallet, ir a setup
                                navController.navigate("wallet/setup") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            !walletUnlocked -> {
                                // Hay wallet pero está bloqueado, ir a unlock
                                navController.navigate("wallet/unlock") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            else -> {
                                // Wallet desbloqueado, ir a pantalla principal
                                navController.navigate("initial_choice") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    }
                }
                
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
                                navController.navigate("user/data")
                            },
                            onLogoutClick = {
                                scope.launch { drawerState.close() }
                                // Implementar lógica de cerrar sesión
                            },
                        )
                    },
                    scrimColor = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = "wallet/check") {
                        // Wallet setup y unlock
                        composable("wallet/setup") {
                            WalletSetupScreen(
                                onSetupComplete = {
                                    // Wallet creado, ir a unlock para la primera vez
                                    navController.navigate("wallet/unlock") {
                                        popUpTo("wallet/setup") { inclusive = true }
                                    }
                                },
                                viewModel = walletSetupViewModel
                            )
                            android.util.Log.d("MainActivity", "🔵 WalletSetupScreen renderizado")
                        }
                        
                        composable("wallet/unlock") {
                            WalletUnlockScreen(
                                onUnlocked = {
                                    // Wallet desbloqueado, ir a pantalla principal
                                    navController.navigate("initial_choice") {
                                        popUpTo("wallet/unlock") { inclusive = true }
                                    }
                                },
                                viewModel = walletUnlockViewModel
                            )
                        }
                        
                        composable("wallet/check") {
                            // Pantalla de carga mientras se verifica el wallet
                            // Se navega automáticamente según el estado
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        }
                        
                        composable("initial_choice") {
                            InitialChoiceScreen(
                                onSellClick = {
                                    navController.navigate("seller/charge")
                                },
                                onBuyClick = {
                                    navController.navigate("buyer/start")
                                },
                                availablePoints = availablePoints,
                                pendingPoints = pendingPoints,
                                walletViewModel = walletViewModel
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
                                walletViewModel = walletViewModel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPaymentSuccess = { transactionId, amount ->
                                    // IMPORTANTE: SendScreen ya hace TODO:
                                    // - Descuenta puntos (en sendPaymentConfirmation) ✅
                                    // - Crea voucher (en handleSellerSignature) ✅
                                    // - Incrementa nonce (en handleSellerSignature) ✅
                                    // Solo necesitamos guardar datos para la UI y navegar
                                    
                                    currentTransactionId = paymentBleViewModel.currentTransactionId.value ?: transactionId
                                    buyerAmount = amount
                                    
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
                val paymentTransaction by paymentBleViewModel.paymentTransaction.collectAsState()
                val concept = paymentTransaction?.concept
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
                                walletViewModel = walletViewModel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPaymentReceived = { amount, transactionId ->
                                    android.util.Log.d("MainActivity", "🎯 onPaymentReceived called: amount=$amount, transactionId=$transactionId")
                                    
                                    // IMPORTANTE: ReceiveScreen ya hace TODO:
                                    // - Verifica firma del comprador ✅
                                    // - Firma como vendedor ✅
                                    // - Suma pending points (en verifyAndSignAsSeller) ✅
                                    // - Crea voucher con createSettledVoucherWithAddresses ✅
                                    // Solo necesitamos guardar datos para la UI y navegar
                                    
                                    currentTransactionId = paymentBleViewModel.currentTransactionId.value ?: transactionId
                                    sellerAmount = amount
                                    
                                    android.util.Log.d("MainActivity", "📱 Navigating to seller/receipt...")
                                    navController.navigate("seller/receipt")
                                    android.util.Log.d("MainActivity", "✅ Navigation command sent")
                                }
                            )
                        }
                        
            composable("seller/receipt") {
                val paymentTransaction by paymentBleViewModel.paymentTransaction.collectAsState()
                val concept = paymentTransaction?.concept
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
                        
                        // User Data (Tus datos)
                        composable("user/data") {
                            UserDataScreen(
                                onBack = {
                                    navController.popBackStack()
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
    
    override fun onPause() {
        super.onPause()
        // Borrar clave privada de memoria al pasar a background
        WalletManager.clearUnlockedWallet()
    }
    
    override fun onResume() {
        super.onResume()
        // Verificar si el wallet necesita desbloqueo al volver a foreground
        // La navegación se maneja en el LaunchedEffect
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Borrar clave privada de memoria
        WalletManager.clearUnlockedWallet()
        
        // Limpiar recursos BLE
        if (::bleRepository.isInitialized) {
            bleRepository.stopGattServer()
            bleRepository.disconnect()
        }
    }
}