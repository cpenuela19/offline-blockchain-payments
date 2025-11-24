package com.g22.offline_blockchain_payments.ble.repository

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.g22.offline_blockchain_payments.ble.model.ConnectionState
import com.g22.offline_blockchain_payments.ble.util.BleConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class BleRepository(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattClient: BluetoothGatt? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _receivedMessage = MutableStateFlow<String?>(null)
    val receivedMessage: StateFlow<String?> = _receivedMessage.asStateFlow()
    
    private var echoCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    
    // Buffer para mensajes fragmentados BLE
    private val messageBuffer = StringBuilder()
    
    // Para esperar escrituras BLE asíncronas
    private var writeCompletion: CompletableDeferred<Boolean>? = null
    
    // GATT Server callbacks (Host mode)
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(TAG, "Server connection state changed: $newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = ConnectionState.Connected
                    messageBuffer.clear() // Limpiar buffer al conectar
                    Log.d(TAG, "Device connected: ${device?.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    _connectionState.value = ConnectionState.Advertising
                    messageBuffer.clear() // Limpiar buffer al desconectar
                    Log.d(TAG, "Device disconnected")
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(TAG, "Read request for characteristic: ${characteristic?.uuid}")
            
            if (characteristic?.uuid == BleConstants.ECHO_CHAR_UUID) {
                val value = echoCharacteristic?.value ?: "ready".toByteArray()
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    value
                )
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            
            Log.d(TAG, "🟢 SERVER: onCharacteristicWriteRequest called!")
            Log.d(TAG, "🟢 SERVER: Device: ${device?.address}")
            Log.d(TAG, "🟢 SERVER: Characteristic: ${characteristic?.uuid}")
            Log.d(TAG, "🟢 SERVER: ResponseNeeded: $responseNeeded")
            Log.d(TAG, "🟢 SERVER: Value length: ${value?.size} bytes")
            
            if (characteristic?.uuid == BleConstants.ECHO_CHAR_UUID && value != null) {
                val fragment = value.decodeToString()
                Log.d(TAG, "🟢 SERVER: Fragment received: $fragment")
                
                // Guardar el valor para eco
                echoCharacteristic?.value = value
                
                // Acumular fragmentos
                messageBuffer.append(fragment)
                Log.d(TAG, "🟢 SERVER: Buffer size: ${messageBuffer.length} chars")
                
                // Responder al cliente
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value
                    )
                }
                
                // Verificar si el mensaje JSON está completo
                val currentMessage = messageBuffer.toString()
                Log.d(TAG, "🟢 SERVER: Buffer actual (${currentMessage.length} chars): ${currentMessage.take(100)}...")
                
                if (currentMessage.startsWith("{") && currentMessage.endsWith("}")) {
                    try {
                        // Validar que es un JSON válido
                        org.json.JSONObject(currentMessage)
                        
                        // Mensaje completo y válido
                        Log.d(TAG, "🟢 SERVER: ✅ JSON VÁLIDO Y COMPLETO")
                        Log.d(TAG, "🟢 SERVER: Mensaje completo: $currentMessage")
                        
                        _receivedMessage.value = currentMessage
                        Log.d(TAG, "🟢 SERVER: ✅ _receivedMessage.value ACTUALIZADO")
                        
                        _connectionState.value = ConnectionState.Success("Mensaje recibido completo")
                        
                        // Limpiar buffer para el siguiente mensaje
                        messageBuffer.clear()
                        Log.d(TAG, "🟢 SERVER: Buffer limpiado para el próximo mensaje")
                    } catch (e: Exception) {
                        Log.e(TAG, "🟢 SERVER: ❌ JSON incompleto o inválido: ${e.message}")
                        Log.d(TAG, "🟢 SERVER: Contenido del buffer: $currentMessage")
                    }
                } else {
                    Log.d(TAG, "🟢 SERVER: ⏳ Esperando más fragmentos... (inicio: ${currentMessage.take(10)}, fin: ${currentMessage.takeLast(10)})")
                    _connectionState.value = ConnectionState.Connected
                }
            } else {
                Log.e(TAG, "❌ SERVER: Wrong characteristic UUID or null value")
            }
        }
    }
    
    // GATT Client callbacks (Client mode)
    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "Client connection state changed: $newState, status: $status")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected
                    Log.d(TAG, "Connected to GATT server, requesting MTU...")
                    // Solicitar MTU más grande (512 bytes) para enviar más datos por paquete
                    val mtuRequested = gatt?.requestMtu(512) ?: false
                    Log.d(TAG, "MTU request: $mtuRequested")
                    if (!mtuRequested) {
                        // Si falla, descubrir servicios directamente
                        Log.d(TAG, "MTU request failed, discovering services...")
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Error("Desconectado del servidor")
                    Log.d(TAG, "Disconnected from GATT server")
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "MTU changed: $mtu bytes, status: $status")
            // Después de cambiar MTU, descubrir servicios
            Log.d(TAG, "Discovering services after MTU change...")
            gatt?.discoverServices()
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "Services discovered: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // CRÍTICO: Habilitar notificaciones para recibir mensajes del servidor
                val service = gatt?.getService(BleConstants.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.ECHO_CHAR_UUID)
                
                if (characteristic != null) {
                    Log.d(TAG, "🔔 CLIENT: Habilitando notificaciones para recibir mensajes del servidor...")
                    
                    // Paso 1: Habilitar notificaciones localmente
                    val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                    Log.d(TAG, "🔔 CLIENT: setCharacteristicNotification = $notificationEnabled")
                    
                    // Paso 2: Escribir el descriptor CCC para habilitar notificaciones en el servidor
                    val descriptor = characteristic.getDescriptor(BleConstants.NOTIFICATION_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val writeSuccess = gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "🔔 CLIENT: writeDescriptor (ENABLE_NOTIFICATION) = $writeSuccess")
                    } else {
                        Log.e(TAG, "🔔 CLIENT: ❌ Descriptor CCC no encontrado")
                    }
                } else {
                    Log.e(TAG, "🔔 CLIENT: ❌ Característica ECHO no encontrada")
                }
                
                _connectionState.value = ConnectionState.Success("Servicios descubiertos")
            } else {
                _connectionState.value = ConnectionState.Error("Error descubriendo servicios")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "🔵 CLIENT: onCharacteristicWrite callback!")
            Log.d(TAG, "🔵 CLIENT: Status: $status (${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED"})")
            Log.d(TAG, "🔵 CLIENT: Characteristic: ${characteristic?.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "🔵 CLIENT: Write SUCCESS - message delivered to server")
                writeCompletion?.complete(true)
            } else {
                Log.e(TAG, "🔵 CLIENT: Write FAILED with status: $status")
                _connectionState.value = ConnectionState.Error("Error al enviar mensaje: $status")
                writeCompletion?.complete(false)
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(TAG, "Characteristic read status: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic?.value?.decodeToString() ?: ""
                _receivedMessage.value = value
                _connectionState.value = ConnectionState.Success("Mensaje recibido: $value")
            } else {
                _connectionState.value = ConnectionState.Error("Error al leer característica")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic?.uuid == BleConstants.ECHO_CHAR_UUID) {
                val message = characteristic.value?.decodeToString() ?: ""
                _receivedMessage.value = message
                Log.d(TAG, "Characteristic changed (notification): $message")
            }
        }
    }
    
    // Advertising callback
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising started successfully")
            _connectionState.value = ConnectionState.Advertising
        }
        
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos de advertising muy grandes (Error 1)"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados advertisers activos (Error 2)"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising ya iniciado (Error 3)"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno del sistema (Error 4)"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada (Error 5)"
                else -> "Error desconocido ($errorCode)"
            }
            Log.e(TAG, "Advertising failed: $errorMessage")
            _connectionState.value = ConnectionState.Error("Error advertising: $errorMessage")
        }
    }
    
    // Scan callback
    private var scanCallback: ScanCallback? = null
    
    // HOST METHODS
    
    @SuppressLint("MissingPermission")
    fun startGattServer() {
        try {
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter is null")
                _connectionState.value = ConnectionState.Error("Bluetooth no disponible")
                return
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth is not enabled")
                _connectionState.value = ConnectionState.Error("Bluetooth desactivado")
                return
            }
            
            Log.d(TAG, "Starting GATT Server...")
            
            // Crear servicio GATT
            val service = BluetoothGattService(
                BleConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            // Crear característica de eco con permisos de lectura y escritura
            echoCharacteristic = BluetoothGattCharacteristic(
                BleConstants.ECHO_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                        BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            echoCharacteristic?.value = "ready".toByteArray()
            
            service.addCharacteristic(echoCharacteristic)
            
            // Abrir GATT Server
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            gattServer?.addService(service)
            
            Log.d(TAG, "GATT Server started")
            
            // Iniciar advertising
            startAdvertising()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT server", e)
            _connectionState.value = ConnectionState.Error("Error al iniciar servidor: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        // CRÍTICO: Limpiar mensaje recibido al iniciar nueva sesión de advertising
        _receivedMessage.value = null
        Log.d(TAG, "🧹 receivedMessage limpiado al iniciar advertising")
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Cannot start advertising: Bluetooth adapter is null")
            _connectionState.value = ConnectionState.Error("Bluetooth no disponible")
            return
        }
        
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising not supported")
            _connectionState.value = ConnectionState.Error("Advertising no soportado en este dispositivo")
            return
        }
        
        Log.d(TAG, "Starting BLE advertising...")
        
        // Configuración más compatible con diferentes dispositivos
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0) // Sin timeout
            .build()
        
        // Data mínimo para evitar error DATA_TOO_LARGE
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleConstants.SERVICE_PARCEL_UUID)
            .build()
        
        // Scan response vacío
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .build()
        
        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }
    
    @SuppressLint("MissingPermission")
    fun stopGattServer() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        advertiser = null
        _connectionState.value = ConnectionState.Idle
        Log.d(TAG, "GATT Server stopped")
    }
    
    // CLIENT METHODS
    
    @SuppressLint("MissingPermission")
    fun startScan(serviceUuid: UUID, onDeviceFound: (BluetoothDevice) -> Unit) {
        // CRÍTICO: Limpiar mensaje recibido al iniciar nueva sesión de escaneo
        _receivedMessage.value = null
        Log.d(TAG, "🧹 receivedMessage limpiado al iniciar scan")
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Cannot start scan: Bluetooth adapter is null")
            _connectionState.value = ConnectionState.Error("Bluetooth no disponible")
            return
        }
        
        scanner = bluetoothAdapter.bluetoothLeScanner
        
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            _connectionState.value = ConnectionState.Error("Scanner no disponible")
            return
        }
        
        _connectionState.value = ConnectionState.Scanning
        
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(serviceUuid))
            .build()
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.device?.let { device ->
                    Log.d(TAG, "Device found: ${device.address}")
                    stopScan()
                    onDeviceFound(device)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "Scan failed: $errorCode")
                _connectionState.value = ConnectionState.Error("Error al escanear: $errorCode")
            }
        }
        
        scanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "Scan started for UUID: $serviceUuid")
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        Log.d(TAG, "Scan stopped")
    }
    
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.Connecting
        gattClient = device.connectGatt(context, false, gattClientCallback)
        Log.d(TAG, "Connecting to device: ${device.address}")
    }
    
    @SuppressLint("MissingPermission")
    suspend fun writeEchoMessage(message: String) {
        try {
            Log.d(TAG, "🔵 writeEchoMessage called with ${message.length} bytes")
            Log.d(TAG, "🔵 TAG is: $TAG (para filtrar logs)")
            
            if (gattClient == null) {
                Log.e(TAG, "❌ gattClient is null!")
                _connectionState.value = ConnectionState.Error("Cliente BLE no inicializado")
                return
            }
            
            Log.d(TAG, "✅ gattClient is not null")
            
            val service = gattClient?.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "❌ Service not found!")
                _connectionState.value = ConnectionState.Error("Servicio no encontrado")
                return
            }
            Log.d(TAG, "✅ Service found")
            
            val characteristic = service.getCharacteristic(BleConstants.ECHO_CHAR_UUID)
            if (characteristic == null) {
                Log.e(TAG, "❌ Characteristic not found!")
                _connectionState.value = ConnectionState.Error("Característica no encontrada")
                return
            }
            Log.d(TAG, "✅ Characteristic found")
        
        // Asegurar que se usa WRITE_TYPE_DEFAULT para que el servidor responda
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        
        // Fragmentar mensaje si es muy grande (límite BLE ~200 bytes para mayor estabilidad)
        val maxChunkSize = 200
        val messageBytes = message.toByteArray()
        Log.d(TAG, "🔧 Max chunk size: $maxChunkSize bytes")
        
        if (messageBytes.size <= maxChunkSize) {
            // Mensaje pequeño, enviar directo
            Log.d(TAG, "📤 Enviando mensaje completo (${messageBytes.size} bytes)")
            
            writeCompletion = CompletableDeferred()
            characteristic.value = messageBytes
            Log.d(TAG, "🔵 Llamando gattClient?.writeCharacteristic()...")
            val initiated = gattClient?.writeCharacteristic(characteristic) ?: false
            Log.d(TAG, "🔵 writeCharacteristic() returned: $initiated")
            
            if (!initiated) {
                Log.e(TAG, "❌ writeCharacteristic returned false!")
                _connectionState.value = ConnectionState.Error("Error al escribir característica")
                return
            }
            
            // Esperar el callback con timeout
            Log.d(TAG, "⏳ Esperando callback onCharacteristicWrite (max 30s)...")
            val success = withTimeoutOrNull(30000) {
                writeCompletion?.await() ?: false
            } ?: false
            Log.d(TAG, "🔵 Callback recibido, success=$success")
            if (success) {
                Log.d(TAG, "✅ Mensaje enviado exitosamente")
                _connectionState.value = ConnectionState.Success("Mensaje enviado con éxito")
                Log.i(TAG, "🎉 [writeEchoMessage] COMPLETADO EXITOSAMENTE - mensaje único enviado")
            } else {
                Log.e(TAG, "❌ Escritura falló en callback o timeout")
                _connectionState.value = ConnectionState.Error("Timeout esperando callback BLE")
            }
        } else {
            // Mensaje grande, fragmentar
            val chunks = messageBytes.asSequence().chunked(maxChunkSize).toList()
            Log.d(TAG, "📦 Fragmentando mensaje en ${chunks.size} chunks")
            
            for ((index, chunk) in chunks.withIndex()) {
                val chunkBytes = chunk.toByteArray()
                Log.d(TAG, "📤 Enviando chunk ${index + 1}/${chunks.size} (${chunkBytes.size} bytes)")
                
                writeCompletion = CompletableDeferred()
                characteristic.value = chunkBytes
                Log.d(TAG, "🔵 Llamando writeCharacteristic para chunk ${index + 1}...")
                val initiated = gattClient?.writeCharacteristic(characteristic) ?: false
                Log.d(TAG, "🔵 writeCharacteristic() returned: $initiated")
                
                if (!initiated) {
                    Log.e(TAG, "❌ Chunk ${index + 1} - writeCharacteristic returned false!")
                    _connectionState.value = ConnectionState.Error("Error al escribir chunk ${index + 1}")
                    return
                }
                
                // Esperar el callback antes de enviar el siguiente chunk (timeout 30s)
                Log.d(TAG, "⏳ Esperando callback para chunk ${index + 1} (max 30s)...")
                val success = withTimeoutOrNull(30000) {
                    writeCompletion?.await() ?: false
                } ?: false
                Log.d(TAG, "🔵 Callback recibido para chunk ${index + 1}, success=$success")
                if (!success) {
                    Log.e(TAG, "❌ Chunk ${index + 1} falló en callback o timeout!")
                    _connectionState.value = ConnectionState.Error("Error al escribir chunk ${index + 1}")
                    return
                }
                
                Log.d(TAG, "✅ Chunk ${index + 1} enviado exitosamente")
                
                // Delay entre chunks para que el servidor procese (150ms)
                kotlinx.coroutines.delay(150)
            }
            
            Log.d(TAG, "✅ Todos los chunks enviados exitosamente")
            _connectionState.value = ConnectionState.Success("Mensaje fragmentado enviado")
            Log.i(TAG, "🎉 [writeEchoMessage] COMPLETADO EXITOSAMENTE - ${chunks.size} chunks enviados")
        }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción en writeEchoMessage: ${e.message}", e)
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            _connectionState.value = ConnectionState.Error("Excepción BLE: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun readEchoMessage() {
        val service = gattClient?.getService(BleConstants.SERVICE_UUID)
        if (service == null) {
            _connectionState.value = ConnectionState.Error("Servicio no encontrado")
            return
        }
        
        val characteristic = service.getCharacteristic(BleConstants.ECHO_CHAR_UUID)
        if (characteristic == null) {
            _connectionState.value = ConnectionState.Error("Característica no encontrada")
            return
        }
        
        val success = gattClient?.readCharacteristic(characteristic) ?: false
        
        if (!success) {
            _connectionState.value = ConnectionState.Error("Error al leer característica")
        }
        
        Log.d(TAG, "Reading characteristic")
    }
    
    @SuppressLint("MissingPermission")
    fun sendMessageToClient(message: String) {
        val device = connectedDevice
        if (device == null) {
            Log.e(TAG, "No connected device to send message")
            _connectionState.value = ConnectionState.Error("No hay dispositivo conectado")
            return
        }
        
        val characteristic = echoCharacteristic
        if (characteristic == null) {
            Log.e(TAG, "Echo characteristic not available")
            return
        }
        
        characteristic.value = message.toByteArray()
        // Notificar al cliente
        gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        Log.d(TAG, "Message sent to client: $message")
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.w(TAG, "🔌 disconnect() llamado!")
        Log.w(TAG, "🔌 Stack trace: ${Thread.currentThread().stackTrace.take(8).joinToString("\n")}")
        gattClient?.close()
        gattClient = null
        connectedDevice = null
        _connectionState.value = ConnectionState.Idle
        // CRÍTICO: NO limpiar receivedMessage aquí porque podría contener SELLER_SIG que aún no se procesó
        // Se limpia al iniciar nueva sesión (startScan/startAdvertising)
        Log.d(TAG, "Disconnected from device")
    }
    
    companion object {
        private const val TAG = "BleRepository"
    }
}

