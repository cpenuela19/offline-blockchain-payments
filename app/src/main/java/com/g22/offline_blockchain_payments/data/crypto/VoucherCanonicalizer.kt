package com.g22.offline_blockchain_payments.data.crypto

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Canonicaliza el payment base para garantizar que comprador y vendedor
 * firmen exactamente el mismo mensaje.
 * 
 * Esta implementación debe ser IDÉNTICA a la del backend (JavaScript).
 * Orden de campos: asset, buyer_address, expiry, offer_id, seller_address, amount_ap
 */
object VoucherCanonicalizer {
    private val gson = Gson()
    
    /**
     * Canonicaliza el payment base según el estándar del backend.
     * 
     * @param base Objeto con los campos: asset, buyer_address, expiry, offer_id, seller_address, amount_ap
     * @return String JSON canónico (sin espacios, orden fijo)
     */
    fun canonicalizePaymentBase(base: PaymentBase): String {
        // Validar campos requeridos
        require(base.offer_id.isNotBlank()) { "MISSING_offer_id" }
        require(base.amount_ap.isNotBlank()) { "MISSING_amount_ap" }
        require(base.asset.isNotBlank()) { "MISSING_asset" }
        require(base.expiry > 0) { "MISSING_expiry" }
        require(base.seller_address.isNotBlank()) { "MISSING_seller_address" }
        require(base.buyer_address.isNotBlank()) { "MISSING_buyer_address" }
        
        // Validar formato de amount_ap (solo números, opcionalmente con punto decimal)
        val amountStr = base.amount_ap.trim()
        require(amountStr.matches(Regex("^\\d+(\\.\\d+)?$"))) { "BAD_AMOUNT_FORMAT" }
        
        // Crear payload en orden fijo (igual que backend)
        val payload = JsonObject().apply {
            addProperty("asset", base.asset)
            addProperty("buyer_address", base.buyer_address.lowercase())
            addProperty("expiry", base.expiry)
            addProperty("offer_id", base.offer_id)
            addProperty("seller_address", base.seller_address.lowercase())
            addProperty("amount_ap", amountStr)
        }
        
        // Retornar JSON sin espacios (compacto)
        return gson.toJson(payload)
    }
}

/**
 * Data class para el payment base
 */
data class PaymentBase(
    val asset: String,
    val buyer_address: String,
    val expiry: Long,
    val offer_id: String,
    val seller_address: String,
    val amount_ap: String
)

