package com.g22.offline_blockchain_payments.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VoucherEntity::class, OutboxEntity::class, PendingVoucherEntity::class],
    version = 4, // Incrementado para EIP-2612 (permit)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherDao(): VoucherDao
    abstract fun outboxDao(): OutboxDao
    abstract fun pendingVoucherDao(): PendingVoucherDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Migración 3 -> 4: Agregar campos de permit EIP-2612
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitOwner TEXT")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitSpender TEXT")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitValue TEXT")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitNonce INTEGER")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitDeadline INTEGER")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitV INTEGER")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitR TEXT")
                database.execSQL("ALTER TABLE vouchers ADD COLUMN permitS TEXT")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "payments_database"
                )
                .addMigrations(MIGRATION_3_4) // Migración para campos de permit
                .fallbackToDestructiveMigration() // Fallback si falla la migración
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

