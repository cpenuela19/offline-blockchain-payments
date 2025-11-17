package com.g22.offline_blockchain_payments.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.data.RoleConverter
import com.g22.offline_blockchain_payments.ui.data.VoucherStatus
import com.g22.offline_blockchain_payments.ui.data.VoucherStatusConverter

@Entity(tableName = "vouchers")
@TypeConverters(VoucherTypeConverters::class)
data class VoucherEntity(
    @PrimaryKey
    val id: String,              // offer_id (uuid)
    val role: Role,              // BUYER | SELLER
    val amountAp: Long,
    val counterparty: String,    // alias otra parte
    val createdAt: Long,
    val status: VoucherStatus,   // GUARDADO_SIN_SENAL | ENVIANDO | SUBIDO_OK | ERROR
    val txHash: String? = null,
    val lastError: String? = null,
    // Campos para settle (offline con firmas)
    val asset: String? = null,           // "AP"
    val expiry: Long? = null,           // Timestamp de expiración
    val buyerAddress: String? = null,    // Dirección del comprador
    val sellerAddress: String? = null,   // Dirección del vendedor
    val buyerSig: String? = null,        // Firma del comprador
    val sellerSig: String? = null         // Firma del vendedor
)

class VoucherTypeConverters {
    @TypeConverter
    fun fromRole(value: String): Role {
        return RoleConverter.fromString(value)
    }
    
    @TypeConverter
    fun toRole(role: Role): String {
        return RoleConverter.toString(role)
    }
    
    @TypeConverter
    fun fromVoucherStatus(value: String): VoucherStatus {
        return VoucherStatusConverter.fromString(value)
    }
    
    @TypeConverter
    fun toVoucherStatus(status: VoucherStatus): String {
        return VoucherStatusConverter.toString(status)
    }
}

