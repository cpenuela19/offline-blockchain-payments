package com.g22.offline_blockchain_payments.data.api

/**
 * Response del endpoint /v1/vouchers/settle
 */
data class SettleResponse(
    val status: String, // "queued" | "already_settled"
    val tx_hash: String? = null,
    val error_code: String? = null,
    val message: String? = null
)

