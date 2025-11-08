package com.g22.offline_blockchain_payments.data.api

data class VoucherRequest(
    val offer_id: String,
    val amount_ap: Long,
    val buyer_alias: String,
    val seller_alias: String,
    val created_at: Long
)

