package com.g22.offline_blockchain_payments.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad para almacenar vouchers pendientes de sincronización.
 * Se usa para calcular el shadow balance cuando la app está offline.
 */
@Entity(tableName = "pending_vouchers")
data class PendingVoucherEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,              // "incoming" | "outgoing"
    val amountAp: Long,
    val timestamp: Long,
    val synced: Boolean = false
)

