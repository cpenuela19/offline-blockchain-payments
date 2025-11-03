package com.g22.offline_blockchain_payments.data.api

data class VoucherResponse(
    val offer_id: String,
    val tx_hash: String?,
    val status: String
)

