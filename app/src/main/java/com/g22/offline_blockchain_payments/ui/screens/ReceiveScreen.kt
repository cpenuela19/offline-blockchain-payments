package com.g22.offline_blockchain_payments.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ble.model.ConnectionState
import com.g22.offline_blockchain_payments.ble.permission.PermissionManager
import com.g22.offline_blockchain_payments.ble.viewmodel.PaymentBleViewModel
import com.g22.offline_blockchain_payments.data.config.WalletConfig
import com.g22.offline_blockchain_payments.data.repository.VoucherRepository
import com.g22.offline_blockchain_payments.ui.components.BalanceCard
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicator
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun ReceiveScreen(
    viewModel: PaymentBleViewModel,
    walletViewModel: com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel,
    onBack: () -> Unit,
    onPaymentReceived: (amount: Long, transactionId: String) -> Unit
) {
    val context = LocalContext.current
    val voucherRepository = remember { VoucherRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    var amountToReceive by remember { mutableStateOf(TextFieldValue("")) }
    var concept by remember { mutableStateOf("") }
    var showQR by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }
    
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val paymentTransaction by viewModel.paymentTransaction.collectAsState()
    
    // Solicitud de permisos BLE
    val blePermissions = remember { PermissionManager.getHostPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Permisos concedidos, iniciar host
            val amount = NumberFormatter.parseAmount(amountToReceive.text) ?: 0L
            val finalConcept = concept.trim().ifEmpty { null}
            val sellerAddress = WalletConfig.getCurrentAddress(context)
            viewModel.startAsHost(
                amount = amount, // Monto en AP (sin conversión)
                receiverName = "Marta Gomez", // TODO: Obtener de perfil de usuario
                sellerAddress = sellerAddress, // NUEVO: Address del vendedor para prevenir auto-transferencias
                concept = finalConcept
            )
            showQR = true
            hasNavigated = false // Reset del flag al generar nuevo QR
        }
    }
    
    // ARQUITECTURA ROBUSTA: Observar cuando se recibe pago del comprador
    // Verificar firma, firmar como vendedor, incrementar shadow balance, enviar respuesta
    LaunchedEffect(paymentTransaction) {
        paymentTransaction?.let { tx ->
            android.util.Log.d("ReceiveScreen", "📦 PaymentTransaction recibida: ID=${tx.transactionId}, Amount=${tx.amount}")
            
            // Solo procesar una vez y si tiene un transactionId válido
            if (!hasNavigated && tx.transactionId.isNotEmpty() && tx.buyerSig != null) {
                android.util.Log.d("ReceiveScreen", "🔐 Verificando firma del comprador...")
                
                try {
                    // Obtener address y privateKey del vendedor
                    // (se desbloqueará automáticamente si es necesario)
                    val sellerAddress = WalletConfig.getCurrentAddress(context)
                    val privateKey = WalletConfig.getCurrentPrivateKey(context)
                    
                    // Verificar firma del comprador y firmar como vendedor
                    val result = viewModel.verifyAndSignAsSeller(
                        receivedTransaction = tx,
                        walletViewModel = walletViewModel,
                        sellerAddress = sellerAddress,
                        privateKey = privateKey
                    )
                    
                    if (result.isSuccess) {
                        val sellerSig = result.getOrNull()!!
                        android.util.Log.d("ReceiveScreen", "✅ Firma del vendedor generada: ${sellerSig.take(20)}...")
                        
                        // Enviar sellerSig de vuelta al comprador vía BLE (formato JSON)
                        val sellerSigJson = """{"type":"SELLER_SIG","signature":"$sellerSig"}"""
                        android.util.Log.d("ReceiveScreen", "📤 [SEND] Preparando mensaje JSON para enviar")
                        android.util.Log.d("ReceiveScreen", "📤 [SEND] JSON a enviar: $sellerSigJson")
                        android.util.Log.d("ReceiveScreen", "📤 [SEND] Longitud del mensaje: ${sellerSigJson.length} chars")
                        
                        viewModel.sendMessage(sellerSigJson)
                        
                        android.util.Log.d("ReceiveScreen", "✅ [SEND] sendMessage() llamado exitosamente")
                        
                        // Guardar voucher localmente con ambas firmas y permit
                        voucherRepository.createSettledVoucherWithAddresses(
                            role = "seller",
                            amountAp = tx.amount,
                            counterparty = tx.senderName,
                            expiry = tx.expiry ?: (System.currentTimeMillis() / 1000 + 86400),
                            offerId = tx.transactionId,
                            buyerAddress = tx.buyerAddress,
                            sellerAddress = tx.sellerAddress,
                            buyerSig = tx.buyerSig ?: "",
                            sellerSig = sellerSig,
                            // Datos de permit
                            permitOwner = tx.permitOwner ?: "",
                            permitSpender = tx.permitSpender ?: "",
                            permitValue = tx.permitValue ?: "",
                            permitNonce = tx.permitNonce ?: 0L,
                            permitDeadline = tx.permitDeadline ?: 0L,
                            permitV = tx.permitV ?: 0,
                            permitR = tx.permitR ?: "",
                            permitS = tx.permitS ?: ""
                        )
                        android.util.Log.d("ReceiveScreen", "💾 Voucher guardado localmente")
                        
                        // Navegar a recibo
                        hasNavigated = true
                        kotlinx.coroutines.delay(500)
                        onPaymentReceived(tx.amount, tx.transactionId)
                        android.util.Log.d("ReceiveScreen", "🎯 Navegando a recibo...")
                        
                    } else {
                        // Firma inválida - enviar error al comprador
                        val error = result.exceptionOrNull()?.message ?: "Firma inválida"
                        android.util.Log.e("ReceiveScreen", "❌ Error: $error")
                        
                        viewModel.sendMessage("ERROR:$error")
                        android.util.Log.d("ReceiveScreen", "📤 Error enviado al comprador")
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("ReceiveScreen", "❌ Excepción: ${e.message}", e)
                    viewModel.sendMessage("ERROR:${e.message}")
                }
                
            } else if (!hasNavigated && tx.transactionId.isNotEmpty() && tx.buyerSig == null) {
                android.util.Log.w("ReceiveScreen", "⚠️ Transacción sin firma del comprador (flujo viejo?)")
            }
        } ?: run {
            android.util.Log.d("ReceiveScreen", "❌ PaymentTransaction is null")
        }
    }
    
    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            if (isHosting) {
                viewModel.stopHost()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
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
                    text = "Recibir AgroPuntos",
                    color = SellerPrimary,
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (!showQR) {
                // Primera pantalla: Ingresar monto
                ReceiveAmountInput(
                    walletViewModel = walletViewModel,
                    amountToReceive = amountToReceive,
                    concept = concept,
                    onAmountChange = { amountToReceive = it },
                    onConceptChange = { concept = it },
                    onGenerateQR = {
                        // Verificar permisos BLE
                        if (PermissionManager.areHostPermissionsGranted(context)) {
                            val amount = NumberFormatter.parseAmount(amountToReceive.text) ?: 0L
                            val finalConcept = concept.trim().ifEmpty { null }
                            val sellerAddress = WalletConfig.getCurrentAddress(context)
                            viewModel.startAsHost(
                                amount = amount, // Monto en AP (sin conversión)
                                receiverName = "Marta Gomez",
                                sellerAddress = sellerAddress, // NUEVO: Address del vendedor
                                concept = finalConcept
                            )
                            showQR = true
                        } else {
                            permissionLauncher.launch(blePermissions)
                        }
                    },
                    onCancel = onBack
                )
            } else {
                // Segunda pantalla: Mostrar QR y estado de conexión
                ReceiveQRDisplay(
                    amountToReceive = amountToReceive.text,
                    qrBitmap = qrBitmap,
                    connectionState = connectionState,
                    paymentTransaction = paymentTransaction,
                    onCancel = {
                        viewModel.stopHost()
                        showQR = false
                        onBack()
                    }
                )
            }
        }
    }
}

@Composable
fun ReceiveAmountInput(
    walletViewModel: com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel,
    amountToReceive: TextFieldValue,
    concept: String,
    onAmountChange: (TextFieldValue) -> Unit,
    onConceptChange: (String) -> Unit,
    onGenerateQR: () -> Unit,
    onCancel: () -> Unit
) {
    val availablePoints by walletViewModel.availablePoints.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Saldo local
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "AgroPuntos disponibles",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${NumberFormatter.formatAmount(availablePoints)} AP",
                    color = SellerPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Monto a cobrar (editable)
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "AgroPuntos a recibir",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Input field for amount
                OutlinedTextField(
                    value = amountToReceive,
                    onValueChange = { newValue ->
                        // Solo permitir números y puntos
                        val numbersOnly = newValue.text.replace(".", "")
                        if (numbersOnly.isEmpty() || numbersOnly.all { it.isDigit() }) {
                            // Formatear el valor con puntos como separadores de miles
                            val formatted = NumberFormatter.formatAmountString(numbersOnly)
                            // Mantener el cursor al final del texto
                            onAmountChange(
                                TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBlue,
                        unfocusedBorderColor = CardDarkBlue,
                        cursorColor = CyanBlue,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    suffix = {
                        Text(
                            text = " AP",
                            fontSize = 48.sp,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Campo para el concepto (opcional)
        OutlinedTextField(
            value = concept,
            onValueChange = onConceptChange,
            label = { 
                Text(
                    "Concepto (opcional)",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp
                    )
                ) 
            },
            placeholder = { 
                Text(
                    "Ej: Venta de café",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp
                    )
                ) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SellerPrimary,
                focusedLabelColor = SellerPrimary,
                unfocusedBorderColor = CardDarkBlue,
                cursorColor = SellerPrimary,
                focusedTextColor = White,
                unfocusedTextColor = White
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Texto de ayuda si no hay monto
        val isAmountValid = NumberFormatter.parseAmount(amountToReceive.text)?.let { it > 0 } ?: false
        
        if (!isAmountValid && amountToReceive.text.isNotEmpty()) {
            Text(
                text = "Debes ingresar un monto mayor a cero",
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else if (amountToReceive.text.isEmpty()) {
            Text(
                text = "Ingresa los AgroPuntos a recibir",
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Botón Generar QR (ahora inicia BLE)
        Button(
            onClick = onGenerateQR,
            enabled = isAmountValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SellerPrimary,
                disabledContainerColor = CardDarkBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Generar código QR",
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
fun ReceiveQRDisplay(
    amountToReceive: String,
    qrBitmap: android.graphics.Bitmap?,
    connectionState: ConnectionState,
    paymentTransaction: com.g22.offline_blockchain_payments.ble.model.PaymentTransaction?,
    onCancel: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val qrSize = minOf(280.dp, screenHeight * 0.32f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Monto a cobrar
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "AgroPuntos a recibir",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$amountToReceive AP",
                    color = SellerPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Estado de conexión
        ConnectionStatusCard(connectionState)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // QR Code display (real con ZXing)
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Código QR para recibir",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code real generado con ZXing
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Código QR de pago",
                        modifier = Modifier.size(qrSize)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = SellerPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Generando QR...",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "El otro celular debe escanear este código",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
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
fun ConnectionStatusCard(state: ConnectionState) {
    val (statusText, statusColor) = when (state) {
        is ConnectionState.Idle -> "Iniciando..." to LightSteelBlue
        is ConnectionState.Advertising -> "Esperando que el otro celular escanee..." to SellerPrimary
        is ConnectionState.Connected -> "¡El otro celular se conectó!" to SellerAccent
        is ConnectionState.Success -> "¡AgroPuntos recibidos!" to SellerAccent
        is ConnectionState.Error -> state.message to Color(0xFFFF6B6B)
        else -> "Preparando..." to LightSteelBlue
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
