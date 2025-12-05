package com.g22.offline_blockchain_payments.ble.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.ble.model.*
import com.g22.offline_blockchain_payments.ble.repository.BleRepository
import com.g22.offline_blockchain_payments.ble.util.BleConstants
import com.g22.offline_blockchain_payments.ble.util.QrGenerator
import com.g22.offline_blockchain_payments.metrics.MetricsCollector
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    
    private val _currentTransactionId = MutableStateFlow<String?>(null)
    val currentTransactionId: StateFlow<String?> = _currentTransactionId.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
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
                if (state is ConnectionState.Success && 
                    state.message.contains("Servicios descubiertos") &&
                    shouldSendPaymentOnConnect &&
                    _isConnected.value) {
                    // Servicios listos, enviar pago INMEDIATAMENTE
                    Log.d(TAG, "Services discovered, sending payment confirmation")
                    shouldSendPaymentOnConnect = false
                    sendPaymentConfirmation()
                }
            }
        }
    }
    
    // ==================== MÉTODOS VENDEDOR (Host) ====================
    
    /**
     * Inicia el modo Host (Vendedor)
     * @param amount Monto a cobrar en AP
     * @param receiverName Nombre del vendedor
     * 
     * NOTA: El transactionId NO se genera aquí. Se genera cuando el comprador
     * confirma el pago, asegurando que solo existan IDs para transacciones reales.
     */
    fun startAsHost(amount: Long, receiverName: String, concept: String? = null) {
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
                
                // Crear contenido del QR: solo datos de emparejamiento, monto y concepto
                val qrContent = createQrPaymentContent(payload, amount, receiverName, concept)
                Log.d(TAG, "QR content created (no transaction ID yet)")
                
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
     */
    private fun createQrPaymentContent(payload: PairingPayload, amount: Long, receiverName: String, concept: String?): String {
        return org.json.JSONObject().apply {
            put("serviceUuid", payload.serviceUuid)
            put("sessionId", payload.sessionId)
            put("amount", amount)
            put("receiverName", receiverName)
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
                // Concepto opcional: puede no estar presente
                _pendingConcept.value = obj.optString("concept").ifEmpty { null }
                
                Log.d(TAG, "QR content processed - Amount: ${_pendingAmount.value}, Receiver: ${_pendingReceiverName.value}, Concept: ${_pendingConcept.value}")
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
                Log.d(TAG, "Connecting to host...")
                val serviceUuid = UUID.fromString(payload.serviceUuid)
                
                // Guardar nombre del comprador
                _pendingSenderName.value = senderName
                
                // Marcar que debe enviar pago cuando servicios estén listos
                shouldSendPaymentOnConnect = true
                
                // Registrar intento BLE
                MetricsCollector.recordBleAttempt(success = true)
                
                // Iniciar escaneo BLE
                bleRepository.startScan(serviceUuid) { device ->
                    // Dispositivo encontrado, conectar
                    Log.d(TAG, "Device found: ${device.address}")
                    bleRepository.connectToDevice(device)
                    _isConnected.value = true
                    // El pago se enviará automáticamente cuando los servicios sean descubiertos
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host", e)
                // Registrar fallo BLE
                MetricsCollector.recordBleAttempt(success = false)
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
     * Envía la confirmación de pago al vendedor
     * 
     * ESTE ES EL MOMENTO donde se genera el transactionId único.
     * Solo se crea cuando el comprador ha confirmado que quiere realizar el pago.
     */
    fun sendPaymentConfirmation() {
        val payload = _scannedPayload.value ?: return
        
        viewModelScope.launch {
            try {
                // AQUÍ se genera el transactionId - solo para transacciones confirmadas
                val transactionId = UUID.randomUUID().toString()
                Log.d(TAG, "💎 Transaction ID generated: $transactionId")
                
                // Iniciar timer para métricas (usando transactionId como clave)
                MetricsCollector.startOfflinePaymentTimer(transactionId)
                
                // Crear la transacción completa con el ID único
                val transaction = PaymentTransaction(
                    transactionId = transactionId,
                    amount = _pendingAmount.value,
                    senderName = _pendingSenderName.value,
                    receiverName = _pendingReceiverName.value,
                    concept = _pendingConcept.value ?: "Pago offline", // Usar concepto o default
                    sessionId = payload.sessionId
                )
                
                // Guardar en estado
                _paymentTransaction.value = transaction
                _currentTransactionId.value = transactionId
                
                // Enviar por BLE al vendedor
                val confirmationJson = transaction.toJson()
                bleRepository.writeEchoMessage(confirmationJson)
                addMessage("Confirmación enviada: ${transaction.amount} AP", isSent = true)
                Log.d(TAG, "✅ Payment confirmation sent with ID: $transactionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending payment confirmation", e)
                // Registrar fallo BLE
                MetricsCollector.recordBleAttempt(success = false)
            }
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

