package com.g22.offline_blockchain_payments.data.api

/**
 * Request para el endpoint /v1/vouchers/settle
 * Incluye las firmas criptográficas del comprador y vendedor
 * NUEVOS: Incluye datos de permit EIP-2612 para gasless approve
 */
data class SettleRequest(
    val offer_id: String,
    val amount_ap: String,
    val asset: String,
    val expiry: Long,
    val seller_address: String,
    val buyer_address: String,
    val seller_sig: String,
    val buyer_sig: String,
    val canonical: String,  // Mensaje canónico para validar firmas
    // NUEVOS: Datos de permit EIP-2612 como objetos anidados
    val permit: PermitData,
    val permit_sig: PermitSignature
)

/**
 * Datos del permit EIP-2612
 */
data class PermitData(
    val owner: String,
    val spender: String,
    val value: String,
    val nonce: Long,
    val deadline: Long
)

/**
 * Firma del permit EIP-2612
 */
data class PermitSignature(
    val v: Int,
    val r: String,
    val s: String
)

