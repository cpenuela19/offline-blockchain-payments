package com.g22.offline_blockchain_payments.worker

import android.content.Context
import androidx.work.*
import com.g22.offline_blockchain_payments.data.repository.VoucherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val repository = VoucherRepository(context)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingItems = repository.getPendingOutboxItems()
            
            if (pendingItems.isEmpty()) {
                return@withContext Result.success()
            }
            
            var hasFailures = false
            
            for (item in pendingItems) {
                val success = repository.syncVoucher(item)
                
                if (!success) {
                    // Actualizar con backoff para reintento
                    repository.updateOutboxItemWithBackoff(item)
                    hasFailures = true
                }
            }
            
            if (hasFailures) {
                // Si hay fallos, programar reintento
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        private const val WORK_NAME = "sync_vouchers"
        
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        
        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
            
            // Usar enqueueUniqueWork para evitar múltiples workers simultáneos
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_vouchers_onetime",
                ExistingWorkPolicy.KEEP, // Si ya hay uno en cola, mantener el existente
                workRequest
            )
        }
    }
}

