package com.g22.offline_blockchain_payments.data.repository

import com.g22.offline_blockchain_payments.data.database.AppDatabase
import com.g22.offline_blockchain_payments.data.database.PendingVoucherEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar vouchers pendientes y cálculos de shadow balance.
 */
class PendingBalanceRepository(private val database: AppDatabase) {
    private val dao = database.pendingVoucherDao()
    
    /**
     * Obtiene todos los vouchers pendientes como Flow
     */
    fun getAllPending(): Flow<List<PendingVoucherEntity>> {
        return dao.getAllPending()
    }
    
    /**
     * Obtiene la suma total de vouchers incoming pendientes
     */
    suspend fun getTotalIncomingPending(): Long {
        return dao.getTotalIncomingPending()
    }
    
    /**
     * Obtiene la suma total de vouchers outgoing pendientes
     */
    suspend fun getTotalOutgoingPending(): Long {
        return dao.getTotalOutgoingPending()
    }
    
    /**
     * Inserta un nuevo voucher pendiente
     */
    suspend fun insertPending(type: String, amountAp: Long, timestamp: Long = System.currentTimeMillis()): Long {
        val entity = PendingVoucherEntity(
            type = type,
            amountAp = amountAp,
            timestamp = timestamp,
            synced = false
        )
        return dao.insert(entity)
    }
    
    /**
     * Marca un voucher como sincronizado
     */
    suspend fun markAsSynced(id: Long) {
        dao.markAsSynced(id)
    }
    
    /**
     * Elimina todos los vouchers sincronizados (limpieza)
     * @return Número de filas eliminadas
     */
    suspend fun deleteSynced(): Int {
        return dao.deleteSynced()
    }
    
    /**
     * Elimina el último voucher outgoing pendiente (usado para rollback)
     */
    suspend fun deleteLastOutgoingPending(amountAp: Long) {
        dao.deleteLastOutgoingPending(amountAp)
    }
    
    /**
     * Elimina TODOS los vouchers outgoing pendientes no sincronizados.
     * Se usa cuando el balance real desde blockchain refleja que las transacciones ya se confirmaron.
     */
    suspend fun deleteAllOutgoingPending(): Int {
        return dao.deleteAllOutgoingPending()
    }
}

