package com.g22.offline_blockchain_payments.ui.data

enum class VoucherStatus {
    GUARDADO_SIN_SENAL,
    ENVIANDO,
    RECEIVED,      // Voucher recibido/queued en servidor, esperando tx_hash
    SUBIDO_OK,
    ERROR
}

// Converters para Room
object VoucherStatusConverter {
    fun fromString(value: String): VoucherStatus {
        return VoucherStatus.valueOf(value)
    }
    
    fun toString(status: VoucherStatus): String {
        return status.name
    }
}

object RoleConverter {
    fun fromString(value: String): Role {
        return Role.valueOf(value)
    }
    
    fun toString(role: Role): String {
        return role.name
    }
}

