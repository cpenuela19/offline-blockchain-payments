package com.g22.offline_blockchain_payments.data.database

import androidx.room.*

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE nextAttemptAt <= :currentTime ORDER BY nextAttemptAt ASC")
    suspend fun getPendingOutboxItems(currentTime: Long): List<OutboxEntity>
    
    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getOutboxItemById(id: String): OutboxEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxItem(item: OutboxEntity)
    
    @Update
    suspend fun updateOutboxItem(item: OutboxEntity)
    
    @Delete
    suspend fun deleteOutboxItem(item: OutboxEntity)
    
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteOutboxItemById(id: String)
}

