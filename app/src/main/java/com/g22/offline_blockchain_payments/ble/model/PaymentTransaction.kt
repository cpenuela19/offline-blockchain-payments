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
    val sellerSig: String? = null,    // Firma del vendedor (opcional al inicio)
    val canonical: String? = null,    // Mensaje canónico firmado (para verificación)
    val expiry: Long? = null,         // Timestamp de expiración del voucher
    // NUEVOS: Datos de permit EIP-2612
    val permitOwner: String? = null,
    val permitSpender: String? = null,
    val permitValue: String? = null,
    val permitNonce: Long? = null,
    val permitDeadline: Long? = null,
    val permitV: Int? = null,
    val permitR: String? = null,
    val permitS: String? = null
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
            if (canonical != null) put("canonical", canonical)
            if (expiry != null) put("expiry", expiry)
            // Campos de permit
            if (permitOwner != null) put("permitOwner", permitOwner)
            if (permitSpender != null) put("permitSpender", permitSpender)
            if (permitValue != null) put("permitValue", permitValue)
            if (permitNonce != null) put("permitNonce", permitNonce)
            if (permitDeadline != null) put("permitDeadline", permitDeadline)
            if (permitV != null) put("permitV", permitV)
            if (permitR != null) put("permitR", permitR)
            if (permitS != null) put("permitS", permitS)
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
                    buyerSig = obj.optString("buyerSig").ifEmpty { null },
                    sellerSig = obj.optString("sellerSig").ifEmpty { null },
                    canonical = obj.optString("canonical").ifEmpty { null },
                    expiry = if (obj.has("expiry")) obj.getLong("expiry") else null,
                    // Campos de permit
                    permitOwner = obj.optString("permitOwner").ifEmpty { null },
                    permitSpender = obj.optString("permitSpender").ifEmpty { null },
                    permitValue = obj.optString("permitValue").ifEmpty { null },
                    permitNonce = if (obj.has("permitNonce")) obj.getLong("permitNonce") else null,
                    permitDeadline = if (obj.has("permitDeadline")) obj.getLong("permitDeadline") else null,
                    permitV = if (obj.has("permitV")) obj.getInt("permitV") else null,
                    permitR = obj.optString("permitR").ifEmpty { null },
                    permitS = obj.optString("permitS").ifEmpty { null }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

