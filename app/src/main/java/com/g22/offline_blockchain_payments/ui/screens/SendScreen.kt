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
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicator
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

enum class SendStep {
    SCANNING,
    CONFIRMATION,
    CONNECTING
}

@Composable
fun SendScreen(
    viewModel: PaymentBleViewModel,
    onBack: () -> Unit,
    onPaymentSuccess: (transactionId: String, amount: Long) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(SendStep.SCANNING) }
    var paymentConfirmed by remember { mutableStateOf(false) }
    
    val scannedPayload by viewModel.scannedPayload.collectAsState()
    val paymentTransaction by viewModel.paymentTransaction.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    
    // Datos pendientes antes de confirmar transacción
    val pendingAmount by viewModel.pendingAmount.collectAsState()
    val pendingReceiverName by viewModel.pendingReceiverName.collectAsState()
    val pendingConcept by viewModel.pendingConcept.collectAsState()
    
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
    
    // Navegar al recibo cuando el pago se confirma (independientemente de BLE)
    // BLE es opcional, solo para notificar al vendedor
    var hasNavigated by remember { mutableStateOf(false) }
    
    LaunchedEffect(paymentConfirmed, paymentTransaction, connectionState, isConnected) {
        if (paymentConfirmed && paymentTransaction != null && !hasNavigated) {
            // Esperar un poco para intentar conectar vía BLE (opcional)
            kotlinx.coroutines.delay(2000)
            
            // Si BLE se conectó exitosamente, esperar a que se envíe el mensaje
            if (connectionState is ConnectionState.Success && isConnected) {
                kotlinx.coroutines.delay(1000) // Dar tiempo para enviar por BLE
            }
            
            // Navegar al recibo (el voucher ya se creó cuando se confirmó)
            paymentTransaction?.let { tx ->
                hasNavigated = true
                onPaymentSuccess(tx.transactionId, tx.amount)
            }
        }
    }
    
    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            if (isConnected) {
                viewModel.disconnect()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
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
                // Generar transactionId y crear PaymentTransaction inmediatamente
                val transactionId = java.util.UUID.randomUUID().toString()
                val payload = scannedPayload
                if (payload != null) {
                    val tx = com.g22.offline_blockchain_payments.ble.model.PaymentTransaction(
                        transactionId = transactionId,
                        amount = pendingAmount,
                        senderName = "Juan P.", // TODO: Obtener de perfil
                        receiverName = pendingReceiverName,
                        concept = pendingConcept ?: "Pago offline",
                        sessionId = payload.sessionId
                    )
                    // Guardar en el ViewModel
                    viewModel.setPaymentTransaction(tx)
                    paymentConfirmed = true
                    
                    // Crear voucher inmediatamente (offline) - esto se hace en MainActivity.onPaymentSuccess
                    // No llamar onPaymentSuccess aquí, se llamará desde LaunchedEffect
                    
                    // Intentar conectar vía BLE (opcional, para notificar al vendedor)
                    viewModel.connectToHost("Juan P.")
                    currentStep = SendStep.CONNECTING
                }
            },
            onCancel = {
                viewModel.disconnect()
                currentStep = SendStep.SCANNING
            }
        )
            
            SendStep.CONNECTING -> ConnectingView(
                connectionState = connectionState,
                paymentTransaction = paymentTransaction,
                onCancel = {
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
