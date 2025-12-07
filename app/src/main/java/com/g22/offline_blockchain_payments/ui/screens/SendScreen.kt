package com.g22.offline_blockchain_payments.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ble.model.ConnectionState
import com.g22.offline_blockchain_payments.ble.permission.PermissionManager
import com.g22.offline_blockchain_payments.ble.viewmodel.PaymentBleViewModel
import com.g22.offline_blockchain_payments.data.config.WalletConfig
import com.g22.offline_blockchain_payments.data.repository.VoucherRepository
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicator
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

enum class SendStep {
    SCANNING,
    CONFIRMATION,
    CONNECTING
}

@Composable
fun SendScreen(
    viewModel: PaymentBleViewModel,
    walletViewModel: com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel,
    onBack: () -> Unit,
    onPaymentSuccess: (transactionId: String, amount: Long) -> Unit
) {
    val context = LocalContext.current
    val voucherRepository = remember { VoucherRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(SendStep.SCANNING) }
    var paymentConfirmed by remember { mutableStateOf(false) }
    
    // Guardar datos del pago para enviar cuando BLE esté listo
    var pendingPaymentData by remember { mutableStateOf<Triple<String, String, com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel>?>(null) }
    
    val scannedPayload by viewModel.scannedPayload.collectAsState()
    val paymentTransaction by viewModel.paymentTransaction.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    
    // Datos pendientes antes de confirmar transacción
    val pendingAmount by viewModel.pendingAmount.collectAsState()
    val pendingReceiverName by viewModel.pendingReceiverName.collectAsState()
    val pendingConcept by viewModel.pendingConcept.collectAsState()
    
    // Mensajes de error/éxito (arquitectura robusta de pagos offline)
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    // Mostrar Snackbar para errores/éxito
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    // Scanner QR real con JourneyApps
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            viewModel.processQrContent(result.contents)
            currentStep = SendStep.CONFIRMATION
        }
    }
    
    // Solicitud de permisos (BLE + Cámara)
    val clientPermissions = remember { PermissionManager.getClientPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Permisos concedidos, abrir scanner
            val options = ScanOptions().apply {
                setPrompt("Escanea el código QR del vendedor")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            scannerLauncher.launch(options)
        }
    }
    
    // Navegar al recibo cuando el pago se confirma y recibe sellerSig
    var hasNavigated by remember { mutableStateOf(false) }
    
    // Observar cuando BLE esté conectado para enviar el pago
    // CRÍTICO: Solo ejecutar UNA VEZ usando paymentConfirmed como flag
    LaunchedEffect(connectionState, isConnected, pendingPaymentData, paymentConfirmed) {
        android.util.Log.d("SendScreen", "📊 LaunchedEffect: connectionState=$connectionState, isConnected=$isConnected, hasPendingData=${pendingPaymentData != null}, paymentConfirmed=$paymentConfirmed")
        
        // CRÍTICO: Solo ejecutar si NO se ha enviado ya el pago
        if (connectionState is ConnectionState.Success && 
            isConnected && 
            pendingPaymentData != null &&
            !paymentConfirmed) {  // ← NUEVO: Previene ejecución múltiple
            // BLE está conectado y tenemos datos pendientes para enviar
            android.util.Log.d("SendScreen", "🔗 BLE conectado, enviando pago (PRIMERA VEZ)...")
            
            val (buyerAddress, privateKey, walletVm) = pendingPaymentData!!
            
            // CRÍTICO: Marcar como confirmado ANTES de enviar para evitar re-ejecuciones
            paymentConfirmed = true
            pendingPaymentData = null
            android.util.Log.d("SendScreen", "🔒 Pago marcado como confirmado para prevenir duplicados")
            
            // Enviar pago con arquitectura de seguridad completa
            viewModel.sendPaymentConfirmation(
                context = context,
                walletViewModel = walletVm,
                buyerAddress = buyerAddress,
                privateKey = privateKey
            )
            
            android.util.Log.d("SendScreen", "✅ sendPaymentConfirmation llamado UNA SOLA VEZ")
        }
    }
    
    // Observar cuando el vendedor envía su firma (SELLER_SIG en mensaje BLE)
    val pendingTransaction by viewModel.pendingTransaction.collectAsState()
    val receivedMessage by viewModel.receivedMessage.collectAsState()
    
    // CRÍTICO: Usar coroutineScope.launch para evitar "coroutine scope left the composition"
    LaunchedEffect(receivedMessage, pendingTransaction) {
        android.util.Log.d("SendScreen", "📊 [NAV] LaunchedEffect ejecutado:")
        android.util.Log.d("SendScreen", "  - receivedMessage: ${if (receivedMessage != null) "NO NULL (${receivedMessage!!.length} chars)" else "NULL"}")
        android.util.Log.d("SendScreen", "  - pendingTransaction: ${if (pendingTransaction != null) "NO NULL" else "NULL"}")
        android.util.Log.d("SendScreen", "  - hasNavigated: $hasNavigated")
        
        if (pendingTransaction != null && receivedMessage != null && !hasNavigated) {
            // Capturar datos INMEDIATAMENTE
            val capturedTransaction = pendingTransaction
            val capturedMessage = receivedMessage
            
            android.util.Log.d("SendScreen", "💾 [NAV] Datos capturados: ID=${capturedTransaction?.transactionId}, amount=${capturedTransaction?.amount}, msgLen=${capturedMessage?.length}")
            
            // Lanzar en coroutineScope que persiste entre recomposiciones
            coroutineScope.launch {
                try {
                    android.util.Log.d("SendScreen", "📨 [NAV] Procesando en coroutineScope...")
                    android.util.Log.d("SendScreen", "📨 [NAV] Primeros 100 chars: ${capturedMessage!!.take(100)}")
                    
                    val json = org.json.JSONObject(capturedMessage)
                    val type = json.optString("type")
                    android.util.Log.d("SendScreen", "📨 [NAV] JSON parseado, type='$type'")
                    
                    if (type == "SELLER_SIG") {
                        val sellerSig = json.optString("signature")
                        android.util.Log.d("SendScreen", "📝 [NAV] ✅ SELLER_SIG detectado! Firma: ${sellerSig.take(20)}...")
                        
                        // Validar que la firma no esté vacía
                        if (sellerSig.isEmpty() || sellerSig.length < 100) {
                            android.util.Log.e("SendScreen", "❌ [NAV] SELLER_SIG inválido o vacío (length=${sellerSig.length})")
                            return@launch
                        }
                        
                        // Marcar como navegado ANTES de llamar a handleSellerSignature
                        hasNavigated = true
                        android.util.Log.d("SendScreen", "🔒 [NAV] hasNavigated = true")
                        
                        // Completar voucher
                        android.util.Log.d("SendScreen", "📝 [NAV] Llamando handleSellerSignature...")
                        viewModel.handleSellerSignature(
                            context = context,
                            sellerSig = sellerSig,
                            voucherRepository = voucherRepository,
                            walletViewModel = walletViewModel
                        )
                        
                        android.util.Log.d("SendScreen", "✅ [NAV] handleSellerSignature completado")
                        
                        // Pequeño delay para asegurar que el voucher se guardó
                        kotlinx.coroutines.delay(300)
                        
                        // Navegar usando los datos capturados
                        if (capturedTransaction != null) {
                            android.util.Log.d("SendScreen", "🎯 [NAV] Navegando: ID=${capturedTransaction.transactionId}, amount=${capturedTransaction.amount}")
                            onPaymentSuccess(capturedTransaction.transactionId, capturedTransaction.amount)
                            android.util.Log.d("SendScreen", "✅ [NAV] Navegación completada")
                        } else {
                            android.util.Log.e("SendScreen", "❌ [NAV] capturedTransaction es NULL")
                        }
                    } else {
                        android.util.Log.w("SendScreen", "⚠️ [NAV] type no es SELLER_SIG: '$type'")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SendScreen", "❌ [NAV] Error en coroutineScope: ${e.message}")
                    android.util.Log.e("SendScreen", "❌ [NAV] Stack: ${e.stackTraceToString()}")
                }
            }
        } else {
            android.util.Log.d("SendScreen", "⏭️ [NAV] Condiciones no cumplidas")
        }
    }
    
    // Timeout para conexión BLE: Si no conecta en 30 segundos, cancelar
    LaunchedEffect(currentStep) {
        if (currentStep == SendStep.CONNECTING) {
            android.util.Log.d("SendScreen", "⏱️ Iniciando timeout de 30 segundos para conexión BLE")
            kotlinx.coroutines.delay(30000) // Esperar 30 segundos
            if (currentStep == SendStep.CONNECTING && !paymentConfirmed) {
                // Timeout de conexión
                android.util.Log.e("SendScreen", "⏰ TIMEOUT: No se completó el pago en 30 segundos")
                android.util.Log.e("SendScreen", "⏰ connectionState=$connectionState, isConnected=$isConnected")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "No se pudo conectar al vendedor. Tiempo agotado.",
                        duration = SnackbarDuration.Long
                    )
                }
                viewModel.disconnect() // Desconectar BLE
                pendingPaymentData = null
                currentStep = SendStep.SCANNING
            } else {
                android.util.Log.d("SendScreen", "✅ Pago completado antes del timeout")
            }
        }
    }
    
    // Fallback: Si no llega sellerSig en 70 segundos total, volver (el rollback ya se ejecutó en sendPaymentConfirmation)
    LaunchedEffect(paymentConfirmed) {
        if (paymentConfirmed && !hasNavigated) {
            kotlinx.coroutines.delay(70000) // Esperar 70 segundos (15 conexión + 60 timeout pago - 5 margen)
            if (!hasNavigated) {
                // Si no navegó después de todo este tiempo, hay un problema
                android.util.Log.w("SendScreen", "⚠️ Pago no completado después de 70s")
                if (errorMessage != null) {
                    // Si hay error, volver a scanning
                    currentStep = SendStep.SCANNING
                    paymentConfirmed = false
                    pendingPaymentData = null
                }
            }
        }
    }
    
    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.w("SendScreen", "⚠️ [DISPOSE] onDispose ejecutado! isConnected=$isConnected, paymentConfirmed=$paymentConfirmed, hasNavigated=$hasNavigated")
            if (isConnected) {
                android.util.Log.w("SendScreen", "🔌 [DISPOSE] Desconectando BLE...")
                viewModel.disconnect()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        // SnackbarHost para mostrar errores
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
        when (currentStep) {
            SendStep.SCANNING -> ScanQRView(
                onScanClick = {
                    // Verificar permisos antes de abrir scanner
                    if (PermissionManager.areClientPermissionsGranted(context)) {
                        val options = ScanOptions().apply {
                            setPrompt("Escanea el código QR del vendedor")
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                        }
                        scannerLauncher.launch(options)
                    } else {
                        permissionLauncher.launch(clientPermissions)
                    }
                },
                onCancel = onBack
            )
            
        SendStep.CONFIRMATION -> ConfirmationView(
            pendingAmount = pendingAmount,
            pendingReceiverName = pendingReceiverName,
            pendingConcept = pendingConcept,
            onConfirm = {
                android.util.Log.d("SendScreen", "🔘 Botón CONFIRMAR presionado")
                
                // ARQUITECTURA ROBUSTA: Pago atómico con descuento de shadow balance y timeout
                val payload = scannedPayload
                android.util.Log.d("SendScreen", "📦 Payload: ${if (payload != null) "OK" else "NULL"}")
                
                if (payload == null) {
                    android.util.Log.e("SendScreen", "❌ ERROR: payload es null")
                    return@ConfirmationView
                }
                
                android.util.Log.d("SendScreen", "💰 Verificando saldo: $pendingAmount AP")
                
                // Verificar shadow balance ANTES de proceder
                val canSpend = walletViewModel.canSpend(pendingAmount)
                android.util.Log.d("SendScreen", "💰 canSpend: $canSpend")
                
                if (!canSpend) {
                    // Mostrar error: saldo insuficiente
                    android.util.Log.e("SendScreen", "❌ SALDO INSUFICIENTE")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Saldo insuficiente para realizar el pago",
                            duration = SnackbarDuration.Long
                        )
                    }
                    return@ConfirmationView
                }
                
                android.util.Log.d("SendScreen", "✅ Saldo OK, continuando...")
                
                // Obtener address y privateKey del wallet
                // (se desbloqueará automáticamente si es necesario)
                try {
                    android.util.Log.d("SendScreen", "🔑 Obteniendo credenciales...")
                    val buyerAddress = WalletConfig.getCurrentAddress(context)
                    android.util.Log.d("SendScreen", "📍 BuyerAddress: $buyerAddress")
                    
                    android.util.Log.d("SendScreen", "🔓 Obteniendo privateKey (auto-unlock si es necesario)...")
                    val privateKey = WalletConfig.getCurrentPrivateKey(context)
                    android.util.Log.d("SendScreen", "🔐 PrivateKey obtenida (length: ${privateKey.length})")
                    
                    android.util.Log.d("SendScreen", "🔄 Preparando pago: buyer=$buyerAddress")
                    
                    // Guardar datos del pago para enviar cuando BLE esté listo
                    pendingPaymentData = Triple(buyerAddress, privateKey, walletViewModel)
                    android.util.Log.d("SendScreen", "💾 Datos guardados en pendingPaymentData")
                    
                    // Conectar a BLE - el pago se enviará automáticamente cuando esté conectado
                    android.util.Log.d("SendScreen", "📡 Llamando connectToHost...")
                    viewModel.connectToHost("Juan P.") // TODO: Obtener de perfil
                    
                    android.util.Log.d("SendScreen", "🔄 Cambiando a SendStep.CONNECTING...")
                    currentStep = SendStep.CONNECTING
                    android.util.Log.d("SendScreen", "✅ currentStep cambiado a CONNECTING")
                    
                } catch (e: Exception) {
                    android.util.Log.e("SendScreen", "❌ EXCEPCIÓN: ${e.message}", e)
                    e.printStackTrace()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Error: ${e.message}",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            },
            onCancel = {
                android.util.Log.w("SendScreen", "❌ [CANCEL] Usuario presionó Cancelar en SCANNING")
                viewModel.disconnect()
                currentStep = SendStep.SCANNING
            }
        )
            
            SendStep.CONNECTING -> ConnectingView(
                connectionState = connectionState,
                paymentTransaction = paymentTransaction,
                onCancel = {
                    android.util.Log.w("SendScreen", "❌ [CANCEL] Usuario presionó Cancelar en CONNECTING")
                    viewModel.disconnect()
                    onBack()
                }
            )
        }
    }
}

@Composable
fun ScanQRView(
    onScanClick: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dar AgroPuntos",
                    color = BuyerPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        
        // Network status indicator
        NetworkStatusIndicator(
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.End)
        )
        
        Spacer(modifier = Modifier.height(60.dp))
        
        // Ilustración del scanner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(horizontal = 24.dp)
                .border(
                    width = 4.dp,
                    color = CyanBlue,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(DarkCard, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Esquinas del marco de escaneo
            Box(modifier = Modifier.fillMaxSize()) {
                // Esquina superior izquierda
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina superior derecha
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina inferior izquierda
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina inferior derecha
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                
                // Texto central
                Text(
                    text = "Toca el botón para escanear",
                    color = LightSteelBlue,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Botón Escanear QR
        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BuyerPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Escanear código QR",
                fontSize = 24.sp,
                color = White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón Cancelar
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Cancelar",
                fontSize = 20.sp,
                color = White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConfirmationView(
    pendingAmount: Long,
    pendingReceiverName: String,
    pendingConcept: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirmar",
                    color = BuyerPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        
        // Network status indicator
        NetworkStatusIndicator(
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.End)
        )
        
        Spacer(modifier = Modifier.height(60.dp))
        
        // Información de pago (usa datos pendientes antes de confirmar)
        if (pendingAmount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Vas a dar",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${NumberFormatter.formatAmount(pendingAmount)} AP",
                        color = BuyerPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "Quien recibe",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pendingReceiverName,
                        color = White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Mostrar concepto solo si existe
                    if (!pendingConcept.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "Concepto",
                            color = LightSteelBlue,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pendingConcept,
                            color = White,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Botón Confirmar y dar AgroPuntos
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BuyerPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Confirmar y dar AgroPuntos",
                fontSize = 22.sp,
                color = White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón Cancelar
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Cancelar",
                fontSize = 20.sp,
                color = White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConnectingView(
    connectionState: ConnectionState,
    paymentTransaction: com.g22.offline_blockchain_payments.ble.model.PaymentTransaction?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dando AgroPuntos",
                    color = BuyerPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        
        // Offline indicator
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(CyanBlue, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline",
                color = LightSteelBlue,
                fontSize = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(80.dp))
        
        // Estado de conexión
        when (connectionState) {
            is ConnectionState.Scanning -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanBlue,
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Buscando a quien recibe...",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            is ConnectionState.Connecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanBlue,
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Conectando con el otro celular...",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            is ConnectionState.Connected -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanBlue,
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "¡Conectado! Enviando AgroPuntos...",
                    color = BuyerAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            is ConnectionState.Success -> {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FFB3)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = DarkNavy,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "¡AgroPuntos entregados!",
                    color = BuyerAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            is ConnectionState.Error -> {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B6B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "No se pudo conectar. Intenta de nuevo",
                    color = Color(0xFFFF6B6B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanBlue,
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Preparando...",
                    color = LightSteelBlue,
                    fontSize = 22.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Información de transacción
        paymentTransaction?.let { tx ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "AgroPuntos",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${NumberFormatter.formatAmount(tx.amount)} AP",
                        color = BuyerPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Quien recibe",
                                color = White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tx.receiverName,
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Botón Cancelar (solo si hay error)
        if (connectionState is ConnectionState.Error) {
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Volver",
                    fontSize = 32.sp,
                    color = White,
                    fontWeight = FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
