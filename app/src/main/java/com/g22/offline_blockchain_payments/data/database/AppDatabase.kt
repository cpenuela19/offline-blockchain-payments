package com.g22.offline_blockchain_payments.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VoucherEntity::class, OutboxEntity::class, PendingVoucherEntity::class],
    version = 3, // Incrementado por PendingVoucherEntity
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherDao(): VoucherDao
    abstract fun outboxDao(): OutboxDao
    abstract fun pendingVoucherDao(): PendingVoucherDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "payments_database"
                )
                .fallbackToDestructiveMigration() // Para desarrollo: recrea la DB si hay cambios
                // TODO: Implementar migración real para producción (Migration de versión 1 a 2)
                // En producción, crear una Migration que preserve los datos existentes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

