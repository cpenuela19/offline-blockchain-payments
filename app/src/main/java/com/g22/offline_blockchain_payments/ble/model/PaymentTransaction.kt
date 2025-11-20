package com.g22.offline_blockchain_payments.ble.model

import org.json.JSONObject

/**
 * Modelo de transacción de pago para intercambio vía BLE
 * 
 * IMPORTANTE: Este modelo ahora incluye las addresses de buyer y seller
 * para prevenir auto-transferencias y permitir validación correcta en el backend.
 */
data class PaymentTransaction(
    val transactionId: String,        // UUID único (offer_id)
    val amount: Long,                 // Monto en AP (tokens)
    val senderName: String,           // Nombre del pagador (buyer)
    val receiverName: String,         // Nombre del receptor (seller)
    val buyerAddress: String,         // Address del comprador (quien paga)
    val sellerAddress: String,        // Address del vendedor (quien recibe)
    val concept: String = "Pago offline", // Concepto del pago
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,            // ID de sesión BLE para validación
    val buyerSig: String? = null,     // Firma del comprador (opcional al inicio)
    val sellerSig: String? = null     // Firma del vendedor (opcional al inicio)
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("transactionId", transactionId)
            put("amount", amount)
            put("senderName", senderName)
            put("receiverName", receiverName)
            put("buyerAddress", buyerAddress)
            put("sellerAddress", sellerAddress)
            put("concept", concept)
            put("timestamp", timestamp)
            put("sessionId", sessionId)
            if (buyerSig != null) put("buyerSig", buyerSig)
            if (sellerSig != null) put("sellerSig", sellerSig)
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
                    buyerAddress = obj.getString("buyerAddress"),
                    sellerAddress = obj.getString("sellerAddress"),
                    concept = obj.optString("concept", "Pago offline"),
                    timestamp = obj.getLong("timestamp"),
                    sessionId = obj.getString("sessionId"),
                    buyerSig = obj.optString("buyerSig", null),
                    sellerSig = obj.optString("sellerSig", null)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

