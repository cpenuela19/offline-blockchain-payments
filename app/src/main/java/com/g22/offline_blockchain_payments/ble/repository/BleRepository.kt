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
                if (currentMessage.startsWith("{") && currentMessage.endsWith("}")) {
                    try {
                        // Validar que es un JSON válido
                        org.json.JSONObject(currentMessage)
                        
                        // Mensaje completo y válido
                        Log.d(TAG, "🟢 SERVER: ✅ COMPLETE MESSAGE: $currentMessage")
                        _receivedMessage.value = currentMessage
                        _connectionState.value = ConnectionState.Success("Mensaje recibido completo")
                        
                        // Limpiar buffer para el siguiente mensaje
                        messageBuffer.clear()
                    } catch (e: Exception) {
                        Log.d(TAG, "🟢 SERVER: JSON incomplete or invalid, waiting for more fragments...")
                    }
                } else {
                    Log.d(TAG, "🟢 SERVER: Waiting for more fragments... (current: ${currentMessage.take(50)}...)")
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
                    Log.d(TAG, "Connected to GATT server, discovering services...")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Error("Desconectado del servidor")
                    Log.d(TAG, "Disconnected from GATT server")
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "Services discovered: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
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
                _connectionState.value = ConnectionState.Success("Mensaje enviado con éxito")
                Log.d(TAG, "🔵 CLIENT: Write SUCCESS - message delivered to server")
            } else {
                _connectionState.value = ConnectionState.Error("Error al enviar mensaje: $status")
                Log.e(TAG, "🔵 CLIENT: Write FAILED with status: $status")
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
    fun writeEchoMessage(message: String) {
        Log.d(TAG, "🔵 writeEchoMessage called with ${message.length} bytes")
        
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
        characteristic.value = message.toByteArray()
        
        val success = gattClient?.writeCharacteristic(characteristic) ?: false
        
        if (!success) {
            Log.e(TAG, "❌ writeCharacteristic returned false!")
            _connectionState.value = ConnectionState.Error("Error al escribir característica")
        } else {
            Log.d(TAG, "✅ writeCharacteristic returned true, waiting for callback...")
        }
        
        Log.d(TAG, "📤 Writing message: $message")
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
        gattClient?.close()
        gattClient = null
        connectedDevice = null
        _connectionState.value = ConnectionState.Idle
        Log.d(TAG, "Disconnected from device")
    }
    
    companion object {
        private const val TAG = "BleRepository"
    }
}

