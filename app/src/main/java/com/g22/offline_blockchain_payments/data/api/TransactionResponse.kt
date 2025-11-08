package com.g22.offline_blockchain_payments.data.api

data class TransactionResponse(
    val offer_id: String,
    val tx_hash: String?,
    val onchain_status: String // CONFIRMED | PENDING | FAILED
)

