package com.g22.offline_blockchain_payments.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingVoucherDao {
    /**
     * Inserta un nuevo voucher pendiente
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingVoucherEntity): Long
    
    /**
     * Obtiene todos los vouchers pendientes (no sincronizados)
     */
    @Query("SELECT * FROM pending_vouchers WHERE synced = 0 ORDER BY timestamp DESC")
    fun getAllPending(): Flow<List<PendingVoucherEntity>>
    
    /**
     * Obtiene la suma total de vouchers incoming pendientes
     */
    @Query("SELECT COALESCE(SUM(amountAp), 0) FROM pending_vouchers WHERE type = 'incoming' AND synced = 0")
    suspend fun getTotalIncomingPending(): Long
    
    /**
     * Obtiene la suma total de vouchers outgoing pendientes
     */
    @Query("SELECT COALESCE(SUM(amountAp), 0) FROM pending_vouchers WHERE type = 'outgoing' AND synced = 0")
    suspend fun getTotalOutgoingPending(): Long
    
    /**
     * Marca un voucher como sincronizado
     */
    @Query("UPDATE pending_vouchers SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    /**
     * Obtiene todos los vouchers pendientes (sin Flow, para procesamiento)
     */
    @Query("SELECT * FROM pending_vouchers WHERE synced = 0")
    suspend fun getAllPendingList(): List<PendingVoucherEntity>
    
    /**
     * Elimina todos los vouchers sincronizados (limpieza)
     * @return Número de filas eliminadas
     */
    @Query("DELETE FROM pending_vouchers WHERE synced = 1")
    suspend fun deleteSynced(): Int
    
    /**
     * Elimina el último voucher outgoing pendiente (usado para rollback)
     * Elimina el más reciente que coincida con el monto
     */
    @Query("DELETE FROM pending_vouchers WHERE id = (SELECT id FROM pending_vouchers WHERE type = 'outgoing' AND amountAp = :amountAp AND synced = 0 ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastOutgoingPending(amountAp: Long)
    
    /**
     * Elimina TODOS los vouchers outgoing pendientes no sincronizados.
     * Se usa cuando el balance real desde blockchain refleja que las transacciones ya se confirmaron.
     * @return Número de filas eliminadas
     */
    @Query("DELETE FROM pending_vouchers WHERE type = 'outgoing' AND synced = 0")
    suspend fun deleteAllOutgoingPending(): Int
}

