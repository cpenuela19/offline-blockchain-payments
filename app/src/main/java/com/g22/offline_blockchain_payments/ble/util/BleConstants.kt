package com.g22.offline_blockchain_payments.ble.util

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    // UUID único del servicio BLE
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
    
    // UUID de la característica de eco (corregido para ser hexadecimal válido)
    val ECHO_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56781234EC40")
    
    // UUID estándar del descriptor para notificaciones (Client Characteristic Configuration)
    val NOTIFICATION_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // ParcelUuid para uso en advertising y scanning
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)
    
    // Timeout para operaciones BLE
    const val SCAN_TIMEOUT_MS = 10000L // 10 segundos
    const val CONNECTION_TIMEOUT_MS = 10000L // 10 segundos
}

