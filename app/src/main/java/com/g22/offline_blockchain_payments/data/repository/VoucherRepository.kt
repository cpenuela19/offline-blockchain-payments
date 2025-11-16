package com.g22.offline_blockchain_payments.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.util.Log
import com.g22.offline_blockchain_payments.data.api.ApiClient
import com.g22.offline_blockchain_payments.data.api.SettleRequest
import com.g22.offline_blockchain_payments.data.api.VoucherRequest
import com.g22.offline_blockchain_payments.data.config.WalletConfig
import com.g22.offline_blockchain_payments.data.crypto.EthereumSigner
import com.g22.offline_blockchain_payments.data.crypto.PaymentBase
import com.g22.offline_blockchain_payments.data.crypto.VoucherCanonicalizer
import com.g22.offline_blockchain_payments.data.database.AppDatabase
import com.g22.offline_blockchain_payments.data.database.OutboxEntity
import com.g22.offline_blockchain_payments.data.database.VoucherEntity
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.data.VoucherStatus
import com.g22.offline_blockchain_payments.worker.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class VoucherRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val voucherDao = database.voucherDao()
    private val outboxDao = database.outboxDao()
    private val apiService = ApiClient.apiService
    private val gson = Gson()
    
    fun getAllVouchers(): Flow<List<VoucherEntity>> = voucherDao.getAllVouchers()
    
    suspend fun createVoucher(
        role: Role,
        amountAp: Long,
        counterparty: String,
        buyerAlias: String,
        sellerAlias: String
    ): VoucherEntity {
        val offerId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis() / 1000 // Unix timestamp
        
        val voucher = VoucherEntity(
            id = offerId,
            role = role,
            amountAp = amountAp,
            counterparty = counterparty,
            createdAt = createdAt,
            status = VoucherStatus.GUARDADO_SIN_SENAL
        )
        
        // Persistir voucher
        voucherDao.insertVoucher(voucher)
        
        // Crear outbox item
        val request = VoucherRequest(
            offer_id = offerId,
            amount_ap = amountAp,
            buyer_alias = buyerAlias,
            seller_alias = sellerAlias,
            created_at = createdAt
        )
        
        val outboxItem = OutboxEntity(
            id = offerId,
            payload = gson.toJson(request),
            attempts = 0,
            nextAttemptAt = System.currentTimeMillis()
        )
        
        outboxDao.insertOutboxItem(outboxItem)
        
        // Disparar sync inmediato si hay red
        SyncWorker.enqueueOneTime(context)
        
        return voucher
    }
    
    suspend fun getPendingOutboxItems(): List<OutboxEntity> {
        return outboxDao.getPendingOutboxItems(System.currentTimeMillis())
    }
    
    suspend fun syncVoucher(outboxItem: OutboxEntity): Boolean {
        val voucher = voucherDao.getVoucherById(outboxItem.id) ?: return false
        
        // Actualizar estado a ENVIANDO
        voucherDao.updateVoucherStatus(
            id = voucher.id,
            status = VoucherStatus.ENVIANDO.name,
            txHash = null
        )
        
        try {
            val request: VoucherRequest = gson.fromJson(outboxItem.payload, VoucherRequest::class.java)
            val response = apiService.createVoucher(request)
            
            when (response.code()) {
                200 -> {
                    val body = response.body()!!
                    voucherDao.updateVoucherStatus(
                        id = voucher.id,
                        status = VoucherStatus.SUBIDO_OK.name,
                        txHash = body.tx_hash
                    )
                    outboxDao.deleteOutboxItemById(outboxItem.id)
                    return true
                }
                409 -> {
                    // Idempotencia: duplicado, consultar estado
                    val txResponse = apiService.getTransaction(voucher.id)
                    if (txResponse.isSuccessful && txResponse.body() != null) {
                        val txBody = txResponse.body()!!
                        voucherDao.updateVoucherStatus(
                            id = voucher.id,
                            status = VoucherStatus.SUBIDO_OK.name,
                            txHash = txBody.tx_hash
                        )
                        outboxDao.deleteOutboxItemById(outboxItem.id)
                        return true
                    }
                    // Si falla consulta, reintentar
                    return false
                }
                in 400..499 -> {
                    // Error de validación, no reintentar
                    voucherDao.updateVoucherError(
                        id = voucher.id,
                        status = VoucherStatus.ERROR.name,
                        error = "Error de validación: ${response.message()}"
                    )
                    outboxDao.deleteOutboxItemById(outboxItem.id)
                    return true
                }
                else -> {
                    // Error 5xx, reintentar con backoff
                    return false
                }
            }
        } catch (e: Exception) {
            // Timeout o error de red, reintentar
            return false
        }
    }
    
    suspend fun updateOutboxItemWithBackoff(item: OutboxEntity) {
        val newAttempts = item.attempts + 1
        val backoffDelay = calculateBackoffDelay(newAttempts)
        val nextAttemptAt = System.currentTimeMillis() + backoffDelay
        
        val updatedItem = item.copy(
            attempts = newAttempts,
            nextAttemptAt = nextAttemptAt
        )
        
        outboxDao.updateOutboxItem(updatedItem)
        
        // Revertir estado a GUARDADO_SIN_SENAL si falló
        voucherDao.updateVoucherStatus(
            id = item.id,
            status = VoucherStatus.GUARDADO_SIN_SENAL.name,
            txHash = null
        )
    }
    
    private fun calculateBackoffDelay(attempts: Int): Long {
        // Backoff exponencial: 1m, 5m, 15m, 60m...
        return when (attempts) {
            1 -> 60_000L // 1 minuto
            2 -> 5 * 60_000L // 5 minutos
            3 -> 15 * 60_000L // 15 minutos
            else -> 60 * 60_000L // 60 minutos (máximo)
        }
    }
    
    /**
     * Método de prueba para crear y enviar un voucher con settle.
     * Usa el mismo payload que el vector de prueba del backend.
     * 
     * @return true si el servidor aceptó el voucher (status 200), false en caso contrario
     */
    suspend fun createSettledVoucherDemo(): Boolean {
        return try {
            // Usar el mismo payload que el vector de prueba
            val offerId = "550e8400-e29b-41d4-a716-446655440000"
            val amountAp = "50"
            val expiry = 1893456000L // Timestamp fijo para reproducibilidad
            
            val base = PaymentBase(
                asset = "AP",
                buyer_address = WalletConfig.BUYER_ADDRESS,
                expiry = expiry,
                offer_id = offerId,
                seller_address = WalletConfig.SELLER_ADDRESS,
                amount_ap = amountAp
            )
            
            // Canonicalizar (debe ser idéntico al backend)
            val canonical = VoucherCanonicalizer.canonicalizePaymentBase(base)
            Log.d("SettleDemo", "Canonical: $canonical")
            
            // Firmar con ambas claves
            val buyerSig = EthereumSigner.signMessageEip191(canonical, WalletConfig.BUYER_PRIVATE_KEY)
            val sellerSig = EthereumSigner.signMessageEip191(canonical, WalletConfig.SELLER_PRIVATE_KEY)
            
            Log.d("SettleDemo", "Buyer sig: $buyerSig")
            Log.d("SettleDemo", "Seller sig: $sellerSig")
            
            // Crear request
            val request = SettleRequest(
                offer_id = offerId,
                amount_ap = amountAp,
                asset = "AP",
                expiry = expiry,
                buyer_address = WalletConfig.BUYER_ADDRESS,
                seller_address = WalletConfig.SELLER_ADDRESS,
                buyer_sig = buyerSig,
                seller_sig = sellerSig
            )
            
            // Enviar al servidor
            val response = apiService.settleVoucher(request)
            
            Log.d("SettleDemo", "Response code: ${response.code()}")
            Log.d("SettleDemo", "Response body: ${response.body()}")
            
            when (response.code()) {
                200 -> {
                    val body = response.body()
                    if (body?.status == "queued" || body?.status == "already_settled") {
                        Log.d("SettleDemo", "✅ Voucher aceptado: ${body.status}")
                        true
                    } else {
                        Log.e("SettleDemo", "❌ Status inesperado: ${body?.status}")
                        false
                    }
                }
                422 -> {
                    Log.e("SettleDemo", "❌ Error de validación de firmas (422)")
                    Log.e("SettleDemo", "Error: ${response.body()?.message}")
                    false
                }
                else -> {
                    Log.e("SettleDemo", "❌ Error HTTP ${response.code()}: ${response.message()}")
                    Log.e("SettleDemo", "Error body: ${response.body()}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("SettleDemo", "❌ Excepción: ${e.message}", e)
            false
        }
    }
}

