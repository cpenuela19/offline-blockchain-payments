package com.g22.offline_blockchain_payments.ble.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.ble.model.*
import com.g22.offline_blockchain_payments.ble.repository.BleRepository
import com.g22.offline_blockchain_payments.ble.util.BleConstants
import com.g22.offline_blockchain_payments.ble.util.QrGenerator
import com.g22.offline_blockchain_payments.data.crypto.EthereumSigner
import com.g22.offline_blockchain_payments.data.crypto.PaymentBase
import com.g22.offline_blockchain_payments.data.crypto.VoucherCanonicalizer
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * ViewModel que gestiona la comunicación BLE para pagos offline
 * Soporta dos modos:
 * - VENDEDOR (Host): Genera QR con datos de pago + inicia GATT Server
 * - COMPRADOR (Client): Escanea QR + conecta vía BLE + envía confirmación
 */
class PaymentBleViewModel(private val bleRepository: BleRepository) : ViewModel() {
    
    // ==================== ESTADOS VENDEDOR (Host) ====================
    
    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()
    
    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()
    
    // ==================== ESTADOS COMPRADOR (Client) ====================
    
    private val _scannedPayload = MutableStateFlow<PairingPayload?>(null)
    val scannedPayload: StateFlow<PairingPayload?> = _scannedPayload.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // ==================== ESTADOS COMPARTIDOS ====================
    
    private val _paymentTransaction = MutableStateFlow<PaymentTransaction?>(null)
    val paymentTransaction: StateFlow<PaymentTransaction?> = _paymentTransaction.asStateFlow()
    
    private val _pendingTransaction = MutableStateFlow<PaymentTransaction?>(null)
    val pendingTransaction: StateFlow<PaymentTransaction?> = _pendingTransaction.asStateFlow()
    
    private val _currentTransactionId = MutableStateFlow<String?>(null)
    val currentTransactionId: StateFlow<String?> = _currentTransactionId.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    val connectionState: StateFlow<ConnectionState> = bleRepository.connectionState
    val receivedMessage: StateFlow<String?> = bleRepository.receivedMessage
    
    private var currentSessionId: String? = null
    private var shouldSendPaymentOnConnect = false
    
    // Datos temporales antes de confirmar transacción (expuestos para UI)
    private val _pendingAmount = MutableStateFlow<Long>(0L)
    val pendingAmount: StateFlow<Long> = _pendingAmount.asStateFlow()

    private val _pendingReceiverName = MutableStateFlow<String>("")
    val pendingReceiverName: StateFlow<String> = _pendingReceiverName.asStateFlow()

    private val _pendingSenderName = MutableStateFlow<String>("")
    val pendingSenderName: StateFlow<String> = _pendingSenderName.asStateFlow()

    private val _pendingSellerAddress = MutableStateFlow<String>("")
    val pendingSellerAddress: StateFlow<String> = _pendingSellerAddress.asStateFlow()

    private val _pendingConcept = MutableStateFlow<String?>(null)
    val pendingConcept: StateFlow<String?> = _pendingConcept.asStateFlow()
    
    init {
        // Observar mensajes recibidos
        viewModelScope.launch {
            receivedMessage.collect { message ->
                message?.let {
                    try {
                        // Intentar parsear como PaymentTransaction
                        val transaction = PaymentTransaction.fromJson(it)
                        if (transaction != null) {
                            _paymentTransaction.value = transaction
                            _currentTransactionId.value = transaction.transactionId
                            addMessage("Transacción recibida: ${transaction.amount} AP", isSent = false)
                        } else {
                            // Si no es transacción, es mensaje simple
                            addMessage(it, isSent = false)
                        }
                    } catch (e: Exception) {
                        addMessage(it, isSent = false)
                    }
                }
            }
        }
        
        // Observar estado de conexión para enviar pago cuando servicios estén listos
        viewModelScope.launch {
            connectionState.collect { state ->
                Log.d(TAG, "📊 ConnectionState: $state")
                
                // Actualizar isConnected basado en el estado
                when (state) {
                    is ConnectionState.Success -> {
                        if (state.message.contains("Servicios descubiertos")) {
                            Log.d(TAG, "✅ BLE servicios listos, marcando isConnected = true")
                            _isConnected.value = true
                        }
                        // Servicios listos - el pago se envía manualmente desde UI
                        if (shouldSendPaymentOnConnect) {
                            Log.d(TAG, "Services discovered, ready for payment")
                            shouldSendPaymentOnConnect = false
                            // NOTA: sendPaymentConfirmation() ahora se llama desde SendScreen
                            // con walletViewModel, buyerAddress y privateKey como parámetros
                        }
                    }
                    is ConnectionState.Error, is ConnectionState.Idle -> {
                        Log.d(TAG, "❌ BLE error o idle, marcando isConnected = false")
                        _isConnected.value = false
                    }
                    else -> {
                        // Scanning, Connecting, Connected (sin servicios), Advertising
                        Log.d(TAG, "⏳ BLE en progreso: $state")
                    }
                }
            }
        }
    }
    
    // ==================== MÉTODOS VENDEDOR (Host) ====================
    
    /**
     * Inicia el modo Host (Vendedor)
     * @param amount Monto a cobrar en AP
     * @param receiverName Nombre del vendedor
     * @param sellerAddress Dirección del wallet del vendedor (incluida en QR)
     * @param concept Concepto del pago (opcional)
     * 
     * NOTA: El transactionId NO se genera aquí. Se genera cuando el comprador
     * confirma el pago, asegurando que solo existan IDs para transacciones reales.
     */
    fun startAsHost(amount: Long, receiverName: String, sellerAddress: String, concept: String? = null) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting as host (seller)...")
                
                // Guardar datos pendientes (sin crear transacción aún)
                _pendingAmount.value = amount
                _pendingReceiverName.value = receiverName
                _pendingConcept.value = concept
                
                // Generar session ID único
                currentSessionId = UUID.randomUUID().toString()
                Log.d(TAG, "Session ID generated: $currentSessionId")
                
                // Crear payload para el QR (SIN transactionId)
                val payload = PairingPayload(
                    serviceUuid = BleConstants.SERVICE_UUID.toString(),
                    sessionId = currentSessionId!!
                )
                
                // Crear contenido del QR: incluye sellerAddress para prevenir auto-transferencias
                val qrContent = createQrPaymentContent(payload, amount, receiverName, sellerAddress, concept)
                Log.d(TAG, "QR content created with sellerAddress (no transaction ID yet)")
                
                // Generar QR bitmap
                _qrBitmap.value = QrGenerator.generateQrBitmap(qrContent, 512)
                Log.d(TAG, "QR bitmap generated")
                
                // Iniciar GATT Server y advertising
                bleRepository.startGattServer()
                Log.d(TAG, "GATT Server started")
                
                _isHosting.value = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting host", e)
                _isHosting.value = false
            }
        }
    }
    
    /**
     * Crea el contenido del QR que incluye datos BLE y de pago (SIN transactionId)
     * El transactionId se generará cuando el comprador confirme.
     * 
     * @param sellerAddress Dirección del wallet del vendedor (CAPA: prevención de auto-transferencias)
     */
    private fun createQrPaymentContent(payload: PairingPayload, amount: Long, receiverName: String, sellerAddress: String, concept: String?): String {
        return org.json.JSONObject().apply {
            put("serviceUuid", payload.serviceUuid)
            put("sessionId", payload.sessionId)
            put("amount", amount)
            put("receiverName", receiverName)
            put("sellerAddress", sellerAddress) // NUEVO: address del vendedor
            // Concepto opcional: solo incluir si no es null
            if (concept != null) {
                put("concept", concept)
            }
            // NO incluir transactionId aquí - se genera al confirmar
        }.toString()
    }
    
    /**
     * Detiene el modo Host
     */
    fun stopHost() {
        viewModelScope.launch {
            bleRepository.stopGattServer()
            _qrBitmap.value = null
            _isHosting.value = false
            _messages.value = emptyList()
            _paymentTransaction.value = null
            currentSessionId = null
        }
    }
    
    // ==================== MÉTODOS COMPRADOR (Client) ====================
    
    /**
     * Procesa el contenido del QR escaneado (modo Cliente)
     * Extrae los datos del pago pero NO crea el transactionId aún.
     */
    fun processQrContent(qrContent: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing QR content...")
                val obj = org.json.JSONObject(qrContent)
                
                // Extraer payload BLE
                val payload = PairingPayload(
                    serviceUuid = obj.getString("serviceUuid"),
                    sessionId = obj.getString("sessionId")
                )
                _scannedPayload.value = payload
                
                // Guardar datos pendientes (sin crear transacción ni ID aún)
                _pendingAmount.value = obj.getLong("amount")
                _pendingReceiverName.value = obj.getString("receiverName")
                _pendingSellerAddress.value = obj.getString("sellerAddress") // NUEVO: address del vendedor
                // Concepto opcional: puede no estar presente
                _pendingConcept.value = obj.optString("concept").ifEmpty { null }
                
                Log.d(TAG, "QR content processed - Amount: ${_pendingAmount.value}, Receiver: ${_pendingReceiverName.value}, Seller: ${_pendingSellerAddress.value}, Concept: ${_pendingConcept.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing QR content", e)
            }
        }
    }
    
    /**
     * Conecta al Host (Vendedor) y envía confirmación de pago
     * @param senderName Nombre del comprador
     */
    fun connectToHost(senderName: String) {
        val payload = _scannedPayload.value ?: return
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔍 Connecting to host...")
                val serviceUuid = UUID.fromString(payload.serviceUuid)
                
                // Guardar nombre del comprador
                _pendingSenderName.value = senderName
                
                // Marcar que debe enviar pago cuando servicios estén listos
                shouldSendPaymentOnConnect = true
                
                // Iniciar escaneo BLE
                Log.d(TAG, "🔍 Starting BLE scan...")
                bleRepository.startScan(serviceUuid) { device ->
                    // Dispositivo encontrado, conectar
                    Log.d(TAG, "📱 Device found: ${device.address}, connecting...")
                    bleRepository.connectToDevice(device)
                    // NO establecer isConnected aquí todavía
                    // Se establecerá cuando connectionState sea Success
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error connecting to host", e)
                _isConnected.value = false
            }
        }
    }
    
    /**
     * Establece una PaymentTransaction ya creada (usado cuando se confirma el pago antes de BLE)
     */
    fun setPaymentTransaction(transaction: PaymentTransaction) {
        _paymentTransaction.value = transaction
        _currentTransactionId.value = transaction.transactionId
        Log.d(TAG, "💎 PaymentTransaction establecida: ${transaction.transactionId}")
    }
    
    /**
     * Envía la confirmación de pago al vendedor con descuento atómico.
     * CAPA 1 (Shadow Balance) + CAPA 2 (Firma) + CAPA 3 (Offer ID Único)
     * 
     * Flujo:
     * 1. Verifica shadow balance ANTES de enviar
     * 2. Descuenta INMEDIATAMENTE (atómico)
     * 3. Genera offerId único y firma como buyer
     * 4. Envía vía BLE con timeout
     * 5. Si timeout/error → ROLLBACK automático
     * 
     * @param context Contexto de Android (para caché de nonces)
     * @param walletViewModel Para verificar/descontar/revertir shadow balance
     * @param buyerAddress Dirección del wallet del comprador
     * @param privateKey Clave privada del comprador (para firmar)
     */
    fun sendPaymentConfirmation(
        context: android.content.Context,
        walletViewModel: WalletViewModel,
        buyerAddress: String,
        privateKey: String
    ) {
        val payload = _scannedPayload.value ?: return
        val sellerAddress = _pendingSellerAddress.value
        val amount = _pendingAmount.value
        
        if (sellerAddress.isEmpty()) {
            _errorMessage.value = "Error: No se pudo obtener la dirección del vendedor"
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Iniciando pago: $amount AP")
                
                // 1. CAPA 1: Verificar shadow balance ANTES (previene doble gasto offline)
                if (!walletViewModel.canSpend(amount)) {
                    _errorMessage.value = "Saldo insuficiente"
                    Log.e(TAG, "❌ Saldo insuficiente: no se puede gastar $amount AP")
                    return@launch
                }
                
                // 2. DESCUENTO INMEDIATO (manejo atómico)
                walletViewModel.deductPoints(amount)
                Log.d(TAG, "💰 Shadow balance descontado: -$amount AP")
                
                // 3. CAPA 3: Generar offerId único (previene procesamiento duplicado)
                val offerId = UUID.randomUUID().toString()
                Log.d(TAG, "💎 Offer ID generado: $offerId")
                
                // 4. Crear canonical (formato estandarizado para firma)
                val expiry = (System.currentTimeMillis() / 1000) + (24 * 60 * 60) // 24 horas
                val base = PaymentBase(
                    asset = "AP",
                    buyer_address = buyerAddress,
                    expiry = expiry,
                    offer_id = offerId,
                    seller_address = sellerAddress,
                    amount_ap = amount.toString()
                )
                val canonical = VoucherCanonicalizer.canonicalizePaymentBase(base)
                Log.d(TAG, "📋 Canonical creado (length: ${canonical.length})")
                
                // 5. CAPA 2: Firmar como buyer (autenticación criptográfica)
                val buyerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
                Log.d(TAG, "✍️ Firma del comprador generada")
                
                // NUEVO: 6. Generar permit EIP-2612
                Log.d(TAG, "📝 Generando permit EIP-2612...")
                val deadline = (System.currentTimeMillis() / 1000) + (24 * 60 * 60) // 24 horas
                val valueInWei = (amount.toBigInteger() * java.math.BigInteger.TEN.pow(18)).toString()
                
                // CRÍTICO: Obtener nonce con sistema de caché
                val nonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getNonceWithCache(
                    context = context,
                    userAddress = buyerAddress,
                    isOnline = walletViewModel.isOnline.value
                )
                
                Log.d(TAG, "📊 Nonce para permit: $nonce")
                
                val (permitData, permitSig) = com.g22.offline_blockchain_payments.data.crypto.PermitSigner.signPermit(
                    ownerAddress = buyerAddress,
                    valueInWei = valueInWei,
                    nonce = nonce,
                    deadline = deadline,
                    privateKey = privateKey
                )
                
                Log.d(TAG, "✅ Permit generado: v=${permitSig.v}")
                
                // CRÍTICO: Incrementar nonce INMEDIATAMENTE después de generar permit
                // Esto asegura que múltiples pagos offline usen nonces diferentes
                // incluso si el comprador no recibe la sellerSig de vuelta
                com.g22.offline_blockchain_payments.data.crypto.NonceReader.incrementCachedNonce(
                    context = context,
                    userAddress = buyerAddress
                )
                Log.d(TAG, "⬆️ Nonce incrementado localmente (después de generar permit)")
                
                // 7. Crear PaymentTransaction con addresses DISTINTAS, firmas y permit
                val transaction = PaymentTransaction(
                    transactionId = offerId,
                    amount = amount,
                    senderName = _pendingSenderName.value,
                    receiverName = _pendingReceiverName.value,
                    buyerAddress = buyerAddress,
                    sellerAddress = sellerAddress,
                    concept = _pendingConcept.value ?: "Pago offline",
                    sessionId = payload.sessionId,
                    buyerSig = buyerSig,
                    sellerSig = null, // Aún no tiene la firma del vendedor
                    canonical = canonical,
                    expiry = expiry,
                    // NUEVOS: Datos de permit
                    permitOwner = permitData.owner,
                    permitSpender = permitData.spender,
                    permitValue = permitData.value,
                    permitNonce = permitData.nonce,
                    permitDeadline = permitData.deadline,
                    permitV = permitSig.v,
                    permitR = permitSig.r,
                    permitS = permitSig.s
                )
                
                // 7. Guardar en estado pendiente (para posible rollback)
                _pendingTransaction.value = transaction
                _currentTransactionId.value = offerId
                Log.d(TAG, "📦 Transacción guardada como pendiente")
                
                // 8. Enviar vía BLE con timeout de 60 segundos
                val confirmationJson = transaction.toJson()
                Log.d(TAG, "📡 Enviando pago vía BLE...")
                Log.d(TAG, "📏 JSON size: ${confirmationJson.length} bytes")
                Log.d(TAG, "📄 JSON preview: ${confirmationJson.take(100)}...")
                
                val bleSuccess = withTimeoutOrNull(60000) { // 60 segundos
                    Log.d(TAG, "🔵 Llamando bleRepository.writeEchoMessage()...")
                    try {
                        bleRepository.writeEchoMessage(confirmationJson)
                        Log.d(TAG, "✅ writeEchoMessage completado SIN excepción")
                        addMessage("Confirmación enviada: $amount AP", isSent = true)
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Excepción en writeEchoMessage: ${e.message}", e)
                        false
                    }
                }
                
                if (bleSuccess == null) {
                    // TIMEOUT: ROLLBACK AUTOMÁTICO (puntos + nonce)
                    Log.e(TAG, "⏰ TIMEOUT: writeEchoMessage no completó en 60s")
                    walletViewModel.rollbackDeduction(amount) // Revertir descuento
                    
                    // ROLLBACK de nonce (decrementar para compensar el incremento previo)
                    val currentNonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getCachedNonce(context, buyerAddress)
                    if (currentNonce > 0) {
                        com.g22.offline_blockchain_payments.data.crypto.NonceReader.setCachedNonce(context, buyerAddress, currentNonce - 1)
                        Log.d(TAG, "⬇️ Nonce revertido: $currentNonce → ${currentNonce - 1}")
                    }
                    
                    _errorMessage.value = "Timeout: vendedor no respondió"
                    _pendingTransaction.value = null
                    _currentTransactionId.value = null
                } else if (bleSuccess == false) {
                    // ERROR: ROLLBACK AUTOMÁTICO (puntos + nonce)
                    Log.e(TAG, "❌ writeEchoMessage completó pero con error")
                    walletViewModel.rollbackDeduction(amount) // Revertir descuento
                    
                    // ROLLBACK de nonce
                    val currentNonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getCachedNonce(context, buyerAddress)
                    if (currentNonce > 0) {
                        com.g22.offline_blockchain_payments.data.crypto.NonceReader.setCachedNonce(context, buyerAddress, currentNonce - 1)
                        Log.d(TAG, "⬇️ Nonce revertido: $currentNonce → ${currentNonce - 1}")
                    }
                    
                    _errorMessage.value = "Error enviando pago"
                    _pendingTransaction.value = null
                    _currentTransactionId.value = null
                } else {
                    Log.d(TAG, "✅ Pago enviado exitosamente, esperando firma del vendedor...")
                }
                
            } catch (e: Exception) {
                // ERROR: ROLLBACK AUTOMÁTICO (puntos + nonce)
                Log.e(TAG, "❌ Error enviando pago: ${e.message}", e)
                walletViewModel.rollbackDeduction(amount)
                
                // ROLLBACK de nonce
                val currentNonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getCachedNonce(context, buyerAddress)
                if (currentNonce > 0) {
                    com.g22.offline_blockchain_payments.data.crypto.NonceReader.setCachedNonce(context, buyerAddress, currentNonce - 1)
                    Log.d(TAG, "⬇️ Nonce revertido: $currentNonce → ${currentNonce - 1}")
                }
                
                _errorMessage.value = "Error: ${e.message}"
                _pendingTransaction.value = null
                _currentTransactionId.value = null
            }
        }
    }
    
    /**
     * Verifica la firma del buyer y firma como seller.
     * CAPA 2: Autenticación criptográfica bidireccional.
     * 
     * Esta función la invoca el VENDEDOR cuando recibe una PaymentTransaction del comprador.
     * 
     * Flujo:
     * 1. Verifica que la firma del buyer sea válida
     * 2. Si es inválida → rechaza y envía error al comprador (trigger rollback)
     * 3. Si es válida → firma con clave del seller
     * 4. Incrementa shadow balance (recibió pago)
     * 5. Devuelve sellerSig para completar el voucher
     * 
     * @param receivedTransaction Transacción recibida del comprador vía BLE
     * @param walletViewModel Para incrementar shadow balance
     * @param sellerAddress Dirección del wallet del vendedor
     * @param privateKey Clave privada del vendedor (para firmar)
     * @return Result.success(sellerSig) si todo ok, Result.failure(error) si falla verificación
     */
    fun verifyAndSignAsSeller(
        receivedTransaction: PaymentTransaction,
        walletViewModel: WalletViewModel,
        sellerAddress: String,
        privateKey: String
    ): Result<String> {
        return try {
            Log.d(TAG, "🔍 Vendedor verificando pago recibido...")
            
            // Validar que canonical existe
            val canonical = receivedTransaction.canonical
            if (canonical.isNullOrEmpty()) {
                Log.e(TAG, "❌ Canonical vacío o nulo")
                return Result.failure(Exception("Canonical no encontrado en la transacción"))
            }
            
            // Validar que buyerSig existe
            val buyerSig = receivedTransaction.buyerSig
            if (buyerSig.isNullOrEmpty()) {
                Log.e(TAG, "❌ Firma del comprador vacía o nula")
                return Result.failure(Exception("Firma del comprador no encontrada"))
            }
            
            // 1. CAPA 2: Verificar firma del comprador criptográficamente
            Log.d(TAG, "🔐 Verificando firma del comprador...")
            val isValid = EthereumSigner.verifySignatureEip191(
                message = canonical,
                signature = buyerSig,
                expectedAddress = receivedTransaction.buyerAddress
            )
            
            if (!isValid) {
                Log.e(TAG, "❌ FIRMA INVÁLIDA del comprador")
                return Result.failure(Exception("Firma del comprador inválida - transacción rechazada"))
            }
            
            Log.d(TAG, "✅ Firma del comprador VÁLIDA")
            
            // 2. Firmar como vendedor (CAPA 2: segunda firma)
            Log.d(TAG, "✍️ Vendedor firmando la transacción...")
            val sellerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
            Log.d(TAG, "✅ Firma del vendedor generada")
            
            // 3. Incrementar shadow balance (recibió pago offline)
            walletViewModel.addPendingPoints(receivedTransaction.amount)
            Log.d(TAG, "💰 Shadow balance incrementado: +${receivedTransaction.amount} AP")
            
            // 4. Devolver firma del vendedor
            Result.success(sellerSig)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando/firmando: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Maneja la firma del vendedor recibida vía BLE.
     * Esta función la invoca el COMPRADOR cuando recibe la sellerSig del vendedor.
     * 
     * Flujo:
     * 1. Completa el voucher con ambas firmas
     * 2. Guarda en VoucherRepository con addresses distintas
     * 3. Estado final: GUARDADO_SIN_SENAL (listo para sincronizar)
     * 4. Si falla guardar → ROLLBACK del shadow balance
     * 
     * @param context Contexto de Android (para caché de nonces)
     * @param sellerSig Firma del vendedor recibida vía BLE
     * @param voucherRepository Para guardar el voucher completo
     * @param walletViewModel Para rollback si falla
     */
    suspend fun handleSellerSignature(
        context: android.content.Context,
        sellerSig: String,
        voucherRepository: com.g22.offline_blockchain_payments.data.repository.VoucherRepository,
        walletViewModel: WalletViewModel
    ) {
        val transaction = _pendingTransaction.value
        if (transaction == null) {
            Log.e(TAG, "❌ No hay transacción pendiente")
            _errorMessage.value = "Error: No hay transacción pendiente"
            return
        }
        
        try {
            Log.d(TAG, "📝 Completando voucher con firma del vendedor...")
            
            // 1. Completar voucher con firma del vendedor
            val completeTransaction = transaction.copy(sellerSig = sellerSig)
            _paymentTransaction.value = completeTransaction
            Log.d(TAG, "✅ Voucher completado con ambas firmas")
            
            // 2. Guardar en VoucherRepository con addresses distintas y permit
            voucherRepository.createSettledVoucherWithAddresses(
                role = "buyer",
                amountAp = transaction.amount,
                counterparty = transaction.receiverName,
                expiry = transaction.expiry ?: (System.currentTimeMillis() / 1000 + 86400),
                offerId = transaction.transactionId,
                buyerAddress = transaction.buyerAddress,
                sellerAddress = transaction.sellerAddress,
                buyerSig = transaction.buyerSig ?: "",
                sellerSig = sellerSig,
                // NUEVOS: Datos de permit
                permitOwner = transaction.permitOwner ?: "",
                permitSpender = transaction.permitSpender ?: "",
                permitValue = transaction.permitValue ?: "",
                permitNonce = transaction.permitNonce ?: 0L,
                permitDeadline = transaction.permitDeadline ?: 0L,
                permitV = transaction.permitV ?: 0,
                permitR = transaction.permitR ?: "",
                permitS = transaction.permitS ?: ""
            )
            Log.d(TAG, "💾 Voucher guardado en base de datos local")
            
            // 3. Limpiar estado pendiente
            _pendingTransaction.value = null
            
            // NOTA: El nonce ya se incrementó en sendPaymentConfirmation()
            // después de generar el permit. No es necesario incrementarlo aquí.
            
            // 4. Mostrar éxito
            _successMessage.value = "Pago completado exitosamente"
            Log.d(TAG, "🎉 Pago offline completado con éxito")
            
        } catch (e: Exception) {
            // Si falla guardar: ROLLBACK del shadow balance + nonce
            Log.e(TAG, "❌ Error guardando voucher: ${e.message}", e)
            walletViewModel.rollbackDeduction(transaction.amount) // Revertir descuento
            
            // ROLLBACK de nonce (ya se incrementó en sendPaymentConfirmation)
            val currentNonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getCachedNonce(context, transaction.buyerAddress)
            if (currentNonce > 0) {
                com.g22.offline_blockchain_payments.data.crypto.NonceReader.setCachedNonce(context, transaction.buyerAddress, currentNonce - 1)
                Log.d(TAG, "⬇️ Nonce revertido: $currentNonce → ${currentNonce - 1}")
            }
            
            _errorMessage.value = "Error guardando voucher: ${e.message}"
            _pendingTransaction.value = null
        }
    }
    
    /**
     * Envía un mensaje genérico
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            if (_isHosting.value) {
                bleRepository.sendMessageToClient(text)
            } else if (_isConnected.value) {
                bleRepository.writeEchoMessage(text)
            }
            addMessage(text, isSent = true)
        }
    }
    
    /**
     * Desconecta del Host
     */
    fun disconnect() {
        viewModelScope.launch {
            bleRepository.disconnect()
            bleRepository.stopScan()
            _isConnected.value = false
            _messages.value = emptyList()
            _scannedPayload.value = null
            _paymentTransaction.value = null
            shouldSendPaymentOnConnect = false
        }
    }
    
    // ==================== MÉTODOS PRIVADOS ====================
    
    private fun addMessage(text: String, isSent: Boolean) {
        _messages.value = _messages.value + Message(text, isSent)
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_isHosting.value) {
            bleRepository.stopGattServer()
        }
        if (_isConnected.value) {
            bleRepository.disconnect()
        }
    }
    
    companion object {
        private const val TAG = "PaymentBleViewModel"
    }
}

