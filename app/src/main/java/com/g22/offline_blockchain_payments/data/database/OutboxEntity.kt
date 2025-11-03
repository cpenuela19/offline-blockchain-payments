package com.g22.offline_blockchain_payments.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey
    val id: String,        // mismo offer_id del voucher
    val payload: String,   // JSON serializado del voucher (body POST)
    val attempts: Int,
    val nextAttemptAt: Long
)

