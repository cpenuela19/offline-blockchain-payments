package com.g22.offline_blockchain_payments.data.api

/**
 * Request para el endpoint /v1/vouchers/settle
 * Incluye las firmas criptográficas del comprador y vendedor
 */
data class SettleRequest(
    val offer_id: String,
    val amount_ap: String,
    val asset: String,
    val expiry: Long,
    val seller_address: String,
    val buyer_address: String,
    val seller_sig: String,
    val buyer_sig: String
)

