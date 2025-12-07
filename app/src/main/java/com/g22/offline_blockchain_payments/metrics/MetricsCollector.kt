package com.g22.offline_blockchain_payments.metrics

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter

/**
 * Singleton para recolectar métricas del sistema en tiempo real.
 * Almacena métricas en memoria y permite exportarlas a JSON.
 */
object MetricsCollector {
    private const val TAG = "MetricsCollector"
    
    // Métricas en memoria
    private val offlinePaymentTimes = mutableListOf<Long>() // tiempos en ms
    private val voucherSizes = mutableListOf<Int>() // tamaños en bytes
    private val syncTimes = mutableListOf<Long>() // tiempos en ms
    private var bleFailures = 0
    private var bleAttempts = 0
    
    // Mapa para rastrear tiempos de inicio de pagos por transactionId
    private val paymentStartTimes = mutableMapOf<String, Long>() // transactionId -> timestamp
    
    /**
     * Inicia el timer para un pago offline (llamado cuando se inicia el pago)
     * @param transactionId ID único de la transacción
     */
    fun startOfflinePaymentTimer(transactionId: String) {
        synchronized(paymentStartTimes) {
            paymentStartTimes[transactionId] = System.currentTimeMillis()
            Log.d(TAG, "⏱️ Payment timer started for transaction: $transactionId")
        }
    }
    
    /**
     * Completa el timer de un pago offline y registra el tiempo (llamado cuando el voucher se guarda)
     * @param transactionId ID único de la transacción (debe ser el mismo offerId del voucher)
     */
    fun completeOfflinePaymentTimer(transactionId: String) {
        synchronized(paymentStartTimes) {
            val startTime = paymentStartTimes.remove(transactionId)
            if (startTime != null) {
                val duration = System.currentTimeMillis() - startTime
                synchronized(offlinePaymentTimes) {
                    offlinePaymentTimes.add(duration)
                }
                Log.d(TAG, "📊 Offline payment time recorded: ${duration}ms for transaction: $transactionId (total: ${offlinePaymentTimes.size})")
            } else {
                Log.w(TAG, "⚠️ No start time found for transaction: $transactionId")
            }
        }
    }
    
    /**
     * Registra el tiempo de un pago offline completado (en milisegundos)
     * Método legacy - usar startOfflinePaymentTimer/completeOfflinePaymentTimer en su lugar
     */
    fun recordOfflinePaymentTime(timeMs: Long) {
        synchronized(offlinePaymentTimes) {
            offlinePaymentTimes.add(timeMs)
            Log.d(TAG, "📊 Offline payment time recorded: ${timeMs}ms (total: ${offlinePaymentTimes.size})")
        }
    }
    
    /**
     * Registra el tamaño de un voucher firmado (en bytes)
     */
    fun recordVoucherSize(sizeBytes: Int) {
        synchronized(voucherSizes) {
            voucherSizes.add(sizeBytes)
            Log.d(TAG, "📊 Voucher size recorded: ${sizeBytes} bytes (total: ${voucherSizes.size})")
        }
    }
    
    /**
     * Registra el tiempo de una sincronización completada (en milisegundos)
     */
    fun recordSyncTime(timeMs: Long) {
        synchronized(syncTimes) {
            syncTimes.add(timeMs)
            Log.d(TAG, "📊 Sync time recorded: ${timeMs}ms (total: ${syncTimes.size})")
        }
    }
    
    /**
     * Registra un intento BLE (exitoso o fallido)
     */
    fun recordBleAttempt(success: Boolean) {
        synchronized(this) {
            bleAttempts++
            if (!success) {
                bleFailures++
            }
            Log.d(TAG, "📊 BLE attempt recorded: success=$success (attempts: $bleAttempts, failures: $bleFailures)")
        }
    }
    
    /**
     * Exporta todas las métricas a un archivo JSON en almacenamiento externo
     * @return File del archivo JSON creado
     */
    fun exportToJson(context: Context): File {
        Log.d(TAG, "📤 Iniciando exportación de métricas...")
        
        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
        
        // Crear estructura de datos para exportar
        val metricsData = mapOf(
            "offline_payment_times_ms" to synchronized(offlinePaymentTimes) { offlinePaymentTimes.toList() },
            "voucher_sizes_bytes" to synchronized(voucherSizes) { voucherSizes.toList() },
            "sync_times_ms" to synchronized(syncTimes) { syncTimes.toList() },
            "ble_failures" to synchronized(this) { bleFailures },
            "ble_attempts" to synchronized(this) { bleAttempts },
            "total_offline_payments" to synchronized(offlinePaymentTimes) { offlinePaymentTimes.size },
            "total_vouchers_measured" to synchronized(voucherSizes) { voucherSizes.size },
            "total_syncs_measured" to synchronized(syncTimes) { syncTimes.size },
            "export_timestamp" to System.currentTimeMillis(),
            "export_date" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        
        Log.d(TAG, "📊 Métricas a exportar: ${metricsData.size} campos")
        Log.d(TAG, "   - Pagos offline: ${metricsData["total_offline_payments"]}")
        Log.d(TAG, "   - Vouchers medidos: ${metricsData["total_vouchers_measured"]}")
        Log.d(TAG, "   - Syncs medidos: ${metricsData["total_syncs_measured"]}")
        Log.d(TAG, "   - Intentos BLE: ${metricsData["ble_attempts"]}")
        Log.d(TAG, "   - Fallos BLE: ${metricsData["ble_failures"]}")
        
        // Obtener directorio de almacenamiento externo
        val externalDir = context.getExternalFilesDir(null)
        Log.d(TAG, "📁 ExternalFilesDir: ${externalDir?.absolutePath ?: "NULL"}")
        
        val metricsDir = if (externalDir != null) {
            File(externalDir, "metrics").apply {
                if (!exists()) {
                    val created = mkdirs()
                    Log.d(TAG, "📁 Creando carpeta metrics: $created (${this.absolutePath})")
                } else {
                    Log.d(TAG, "📁 Carpeta metrics ya existe: ${this.absolutePath}")
                }
            }
        } else {
            // Fallback a almacenamiento interno
            File(context.filesDir, "metrics").apply {
                if (!exists()) {
                    val created = mkdirs()
                    Log.d(TAG, "📁 Creando carpeta metrics (internal): $created (${this.absolutePath})")
                } else {
                    Log.d(TAG, "📁 Carpeta metrics ya existe (internal): ${this.absolutePath}")
                }
            }
        }
        
        // Crear archivo con timestamp
        val timestamp = System.currentTimeMillis()
        val fileName = "metrics_${timestamp}.json"
        val metricsFile = File(metricsDir, fileName)
        
        Log.d(TAG, "📄 Creando archivo: ${metricsFile.absolutePath}")
        
        // Escribir JSON
        try {
            FileWriter(metricsFile).use { writer ->
                gson.toJson(metricsData, writer)
            }
            Log.d(TAG, "✅ Archivo JSON escrito exitosamente (${metricsFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error escribiendo archivo JSON: ${e.message}", e)
            throw e
        }
        
        // Verificar que el archivo se creó
        if (metricsFile.exists()) {
            Log.d(TAG, "✅ Metrics exported to: ${metricsFile.absolutePath}")
            Log.d(TAG, "✅ File size: ${metricsFile.length()} bytes")
            Log.d(TAG, "✅ Can read: ${metricsFile.canRead()}")
        } else {
            Log.e(TAG, "❌ ERROR: Archivo NO existe después de escribirlo!")
        }
        
        return metricsFile
    }
    
    /**
     * Obtiene estadísticas básicas (sin exportar)
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "offline_payment_count" to synchronized(offlinePaymentTimes) { offlinePaymentTimes.size },
            "offline_payment_avg_ms" to synchronized(offlinePaymentTimes) {
                if (offlinePaymentTimes.isEmpty()) 0.0
                else offlinePaymentTimes.average()
            },
            "voucher_count" to synchronized(voucherSizes) { voucherSizes.size },
            "voucher_avg_bytes" to synchronized(voucherSizes) {
                if (voucherSizes.isEmpty()) 0.0
                else voucherSizes.average()
            },
            "sync_count" to synchronized(syncTimes) { syncTimes.size },
            "sync_avg_ms" to synchronized(syncTimes) {
                if (syncTimes.isEmpty()) 0.0
                else syncTimes.average()
            },
            "ble_attempts" to synchronized(this) { bleAttempts },
            "ble_failures" to synchronized(this) { bleFailures },
            "ble_success_rate" to synchronized(this) {
                if (bleAttempts == 0) 0.0
                else ((bleAttempts - bleFailures).toDouble() / bleAttempts * 100)
            }
        )
    }
    
    /**
     * Lista todos los archivos de métricas exportados
     * @return Lista de archivos JSON en la carpeta metrics
     */
    fun listExportedFiles(context: Context): List<File> {
        val externalDir = context.getExternalFilesDir(null)
        val metricsDir = if (externalDir != null) {
            File(externalDir, "metrics")
        } else {
            File(context.filesDir, "metrics")
        }
        
        if (!metricsDir.exists()) {
            Log.d(TAG, "📁 Carpeta metrics no existe todavía")
            return emptyList()
        }
        
        val files = metricsDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        Log.d(TAG, "📁 Archivos de métricas encontrados: ${files.size}")
        files.forEach { file ->
            Log.d(TAG, "   - ${file.name} (${file.length()} bytes)")
        }
        
        return files
    }
    
    /**
     * Limpia todas las métricas (útil para pruebas)
     */
    fun clear() {
        synchronized(offlinePaymentTimes) {
            offlinePaymentTimes.clear()
        }
        synchronized(voucherSizes) {
            voucherSizes.clear()
        }
        synchronized(syncTimes) {
            syncTimes.clear()
        }
        synchronized(paymentStartTimes) {
            paymentStartTimes.clear()
        }
        synchronized(this) {
            bleFailures = 0
            bleAttempts = 0
        }
        Log.d(TAG, "🧹 Metrics cleared")
    }
}

