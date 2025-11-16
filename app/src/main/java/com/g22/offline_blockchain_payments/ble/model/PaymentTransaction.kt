package com.g22.offline_blockchain_payments.ble.model

import org.json.JSONObject

/**
 * Modelo de transacción de pago para intercambio vía BLE
 */
data class PaymentTransaction(
    val transactionId: String,        // UUID único
    val amount: Long,                 // Monto en COP (centavos)
    val senderName: String,           // Nombre del pagador
    val receiverName: String,         // Nombre del receptor
    val concept: String = "Pago offline", // Concepto del pago
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String             // ID de sesión BLE para validación
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("transactionId", transactionId)
            put("amount", amount)
            put("senderName", senderName)
            put("receiverName", receiverName)
            put("concept", concept)
            put("timestamp", timestamp)
            put("sessionId", sessionId)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): PaymentTransaction? {
            return try {
                val obj = JSONObject(json)
                PaymentTransaction(
                    transactionId = obj.getString("transactionId"),
                    amount = obj.getLong("amount"),
                    senderName = obj.getString("senderName"),
                    receiverName = obj.getString("receiverName"),
                    concept = obj.optString("concept", "Pago offline"),
                    timestamp = obj.getLong("timestamp"),
                    sessionId = obj.getString("sessionId")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

