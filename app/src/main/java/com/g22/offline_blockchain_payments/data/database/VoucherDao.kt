package com.g22.offline_blockchain_payments.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers ORDER BY createdAt DESC")
    fun getAllVouchers(): Flow<List<VoucherEntity>>
    
    @Query("SELECT * FROM vouchers WHERE id = :id")
    suspend fun getVoucherById(id: String): VoucherEntity?
    
    @Query("SELECT * FROM vouchers WHERE status = :status")
    suspend fun getVouchersByStatus(status: String): List<VoucherEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: VoucherEntity)
    
    @Update
    suspend fun updateVoucher(voucher: VoucherEntity)
    
    @Query("UPDATE vouchers SET status = :status, txHash = :txHash WHERE id = :id")
    suspend fun updateVoucherStatus(id: String, status: String, txHash: String?)
    
    @Query("UPDATE vouchers SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateVoucherError(id: String, status: String, error: String?)
}

