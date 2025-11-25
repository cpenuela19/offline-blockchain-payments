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
    private val pendingBalanceRepository = PendingBalanceRepository(database)
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
    
    /**
     * Crea un voucher con settle (offline con firmas criptográficas).
     * Este método crea el voucher, lo firma con ambas claves, y lo guarda en outbox para sincronización.
     * 
     * @param role Rol del usuario (BUYER o SELLER)
     * @param amountAp Monto en AgroPuntos
     * @param counterparty Alias de la contraparte
     * @param expiry Timestamp de expiración (Unix timestamp)
     * @param offerId ID opcional del voucher (si viene de BLE/QR, usar el transactionId)
     * @return VoucherEntity creado
     */
    suspend fun createSettledVoucher(
        role: Role,
        amountAp: Long,
        counterparty: String,
        expiry: Long = System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60), // Por defecto: 7 días
        offerId: String? = null // Si es null, se genera uno nuevo
    ): VoucherEntity {
        val finalOfferId = offerId ?: UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis() / 1000
        
        // Obtener dirección del wallet actual (mismo wallet para buyer/seller)
        val walletAddress = WalletConfig.getCurrentAddress(context)
        
        // Crear payment base canónico
        val base = PaymentBase(
            asset = "AP",
            buyer_address = walletAddress,
            expiry = expiry,
            offer_id = finalOfferId,
            seller_address = walletAddress,
            amount_ap = amountAp.toString()
        )
        
        // Canonicalizar
        val canonical = VoucherCanonicalizer.canonicalizePaymentBase(base)
        Log.d("SettleVoucher", "Canonical: $canonical")
        
        // Firmar con la clave privada del wallet (mismo wallet para buyer/seller)
        val privateKey = WalletConfig.getCurrentPrivateKey(context)
        val buyerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
        val sellerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
        
        Log.d("SettleVoucher", "Buyer sig: ${buyerSig.take(20)}...")
        Log.d("SettleVoucher", "Seller sig: ${sellerSig.take(20)}...")
        
        // Crear voucher con todos los datos
        val voucher = VoucherEntity(
            id = finalOfferId,
            role = role,
            amountAp = amountAp,
            counterparty = counterparty,
            createdAt = createdAt,
            status = VoucherStatus.GUARDADO_SIN_SENAL,
            asset = "AP",
            expiry = expiry,
            buyerAddress = walletAddress,
            sellerAddress = walletAddress,
            buyerSig = buyerSig,
            sellerSig = sellerSig
        )
        
        // Persistir voucher
        voucherDao.insertVoucher(voucher)
        
        // Crear request de settle para outbox
        // NOTA: Esta función es legacy para self-transfers (buyer == seller)
        // No requiere permit real, así que se usan valores por defecto
        val settleRequest = SettleRequest(
            offer_id = finalOfferId,
            amount_ap = amountAp.toString(),
            asset = "AP",
            expiry = expiry,
            buyer_address = walletAddress,
            seller_address = walletAddress,
            buyer_sig = buyerSig,
            seller_sig = sellerSig,
            canonical = canonical,
            // Valores por defecto para permit (no aplicable en self-transfers)
            permit = com.g22.offline_blockchain_payments.data.api.PermitData(
                owner = "",
                spender = "",
                value = "",
                nonce = 0L,
                deadline = 0L
            ),
            permit_sig = com.g22.offline_blockchain_payments.data.api.PermitSignature(
                v = 0,
                r = "",
                s = ""
            )
        )
        
        // Verificar si ya existe un outbox item con este ID (evitar duplicados)
        val existingOutbox = outboxDao.getOutboxItemById(finalOfferId)
        if (existingOutbox == null) {
            val outboxItem = OutboxEntity(
                id = finalOfferId,
                payload = gson.toJson(settleRequest),
                attempts = 0,
                nextAttemptAt = System.currentTimeMillis()
            )
            
            outboxDao.insertOutboxItem(outboxItem)
            Log.d("SettleVoucher", "📦 Outbox item creado: $finalOfferId")
        } else {
            Log.d("SettleVoucher", "⚠️ Outbox item ya existe, omitiendo: $finalOfferId")
        }
        
        // Disparar sync inmediato si hay red (usar unique work para evitar duplicados)
        SyncWorker.enqueueOneTime(context)
        
        Log.d("SettleVoucher", "✅ Voucher con settle creado: $finalOfferId")
        
        return voucher
    }
    
    /**
     * Crea un voucher con settle usando addresses DISTINTAS y firmas ya creadas.
     * NUEVA ARQUITECTURA: Para pagos offline con BLE donde buyer y seller son wallets diferentes.
     * 
     * Esta función NO genera firmas, las recibe como parámetros.
     * Usa las addresses exactas intercambiadas vía QR/BLE.
     * 
     * CAPA 1: Shadow balance ya manejado por el ViewModel
     * CAPA 2: Firmas criptográficas ya verificadas
     * CAPA 3: Offer ID único recibido del comprador
     * 
     * @param role Rol del usuario local (BUYER o SELLER)
     * @param amountAp Monto en AgroPuntos
     * @param counterparty Alias de la contraparte
     * @param expiry Timestamp de expiración (Unix timestamp)
     * @param offerId UUID único de la transacción (offer_id)
     * @param buyerAddress Dirección del wallet del comprador
     * @param sellerAddress Dirección del wallet del vendedor (DEBE ser diferente a buyerAddress)
     * @param buyerSig Firma del comprador (ya generada)
     * @param sellerSig Firma del vendedor (ya generada)
     * @return VoucherEntity creado
     */
    suspend fun createSettledVoucherWithAddresses(
        role: String,
        amountAp: Long,
        counterparty: String,
        expiry: Long,
        offerId: String,
        buyerAddress: String,
        sellerAddress: String,
        buyerSig: String,
        sellerSig: String,
        // NUEVOS: Parámetros de permit EIP-2612
        permitOwner: String,
        permitSpender: String,
        permitValue: String,
        permitNonce: Long,
        permitDeadline: Long,
        permitV: Int,
        permitR: String,
        permitS: String
    ): VoucherEntity {
        val createdAt = System.currentTimeMillis() / 1000
        
        // Validación crítica: buyer y seller deben ser diferentes
        if (buyerAddress.lowercase() == sellerAddress.lowercase()) {
            throw IllegalArgumentException(
                "⚠️ SEGURIDAD: buyerAddress y sellerAddress deben ser diferentes. " +
                "Recibido: buyer=$buyerAddress, seller=$sellerAddress"
            )
        }
        
        Log.d("SettledVoucherWithAddresses", "📋 Creando voucher con addresses distintas")
        Log.d("SettledVoucherWithAddresses", "  Offer ID: $offerId")
        Log.d("SettledVoucherWithAddresses", "  Buyer: ${buyerAddress.take(10)}...")
        Log.d("SettledVoucherWithAddresses", "  Seller: ${sellerAddress.take(10)}...")
        Log.d("SettledVoucherWithAddresses", "  Amount: $amountAp AP")
        
        // Convertir role string a enum (para compatibilidad)
        val roleEnum = if (role.uppercase() == "BUYER") Role.BUYER else Role.SELLER
        
        // Crear voucher con addresses DISTINTAS (sin firmar, ya vienen firmadas)
        val voucher = VoucherEntity(
            id = offerId,
            role = roleEnum,
            amountAp = amountAp,
            counterparty = counterparty,
            createdAt = createdAt,
            status = VoucherStatus.GUARDADO_SIN_SENAL, // Listo para sincronizar
            asset = "AP",
            expiry = expiry,
            buyerAddress = buyerAddress,   // Address del comprador (diferente)
            sellerAddress = sellerAddress, // Address del vendedor (diferente)
            buyerSig = buyerSig,           // Firma ya creada por el comprador
            sellerSig = sellerSig,         // Firma ya creada por el vendedor
            // NUEVOS: Datos de permit EIP-2612
            permitOwner = permitOwner,
            permitSpender = permitSpender,
            permitValue = permitValue,
            permitNonce = permitNonce,
            permitDeadline = permitDeadline,
            permitV = permitV,
            permitR = permitR,
            permitS = permitS
        )
        
        // Persistir voucher
        voucherDao.insertVoucher(voucher)
        Log.d("SettledVoucherWithAddresses", "💾 Voucher guardado en DB local")
        
        // Reconstruir canonical para enviar al backend
        val base = com.g22.offline_blockchain_payments.data.crypto.PaymentBase(
            asset = "AP",
            buyer_address = buyerAddress,
            expiry = expiry,
            offer_id = offerId,
            seller_address = sellerAddress,
            amount_ap = amountAp.toString()
        )
        val canonical = com.g22.offline_blockchain_payments.data.crypto.VoucherCanonicalizer.canonicalizePaymentBase(base)
        
        // Crear request de settle para outbox (con addresses distintas y permit)
        val settleRequest = SettleRequest(
            offer_id = offerId,
            amount_ap = amountAp.toString(),
            asset = "AP",
            expiry = expiry,
            buyer_address = buyerAddress,
            seller_address = sellerAddress,
            buyer_sig = buyerSig,
            seller_sig = sellerSig,
            canonical = canonical,
            // NUEVOS: Datos de permit EIP-2612 como objetos anidados
            permit = com.g22.offline_blockchain_payments.data.api.PermitData(
                owner = permitOwner,
                spender = permitSpender,
                value = permitValue,
                nonce = permitNonce,
                deadline = permitDeadline
            ),
            permit_sig = com.g22.offline_blockchain_payments.data.api.PermitSignature(
                v = permitV,
                r = permitR,
                s = permitS
            )
        )
        
        // Verificar si ya existe un outbox item con este ID (CAPA 3: idempotencia)
        val existingOutbox = outboxDao.getOutboxItemById(offerId)
        if (existingOutbox == null) {
            val outboxItem = OutboxEntity(
                id = offerId,
                payload = gson.toJson(settleRequest),
                attempts = 0,
                nextAttemptAt = System.currentTimeMillis()
            )
            
            outboxDao.insertOutboxItem(outboxItem)
            Log.d("SettledVoucherWithAddresses", "📦 Outbox item creado: $offerId")
        } else {
            Log.d("SettledVoucherWithAddresses", "⚠️ Outbox item ya existe (idempotencia), omitiendo: $offerId")
        }
        
        // Disparar sync inmediato si hay red
        SyncWorker.enqueueOneTime(context)
        Log.d("SettledVoucherWithAddresses", "🚀 Sincronización encolada")
        
        Log.d("SettledVoucherWithAddresses", "✅ Voucher offline con addresses distintas creado exitosamente")
        
        return voucher
    }
    
    suspend fun getPendingOutboxItems(): List<OutboxEntity> {
        return outboxDao.getPendingOutboxItems(System.currentTimeMillis())
    }
    
    /**
     * Actualiza el nonce cacheado después de sincronizar exitosamente.
     * Esto mantiene el caché alineado con el estado real de la blockchain.
     */
    private suspend fun updateNonceCacheAfterSync(userAddress: String) {
        try {
            Log.d("VoucherRepository", "🔄 Actualizando nonce cacheado para $userAddress...")
            
            // Consultar el nonce real actual desde el blockchain
            val realNonce = com.g22.offline_blockchain_payments.data.crypto.NonceReader.getNonceAsLong(userAddress)
            
            // Actualizar el caché
            com.g22.offline_blockchain_payments.data.crypto.NonceReader.setCachedNonce(
                context = context,
                userAddress = userAddress,
                nonce = realNonce
            )
            
            Log.d("VoucherRepository", "✅ Nonce cacheado actualizado: $realNonce")
        } catch (e: Exception) {
            Log.w("VoucherRepository", "⚠️ No se pudo actualizar nonce cacheado: ${e.message}")
            // No es crítico, el caché se actualizará en el próximo pago online
        }
    }
    
    /**
     * Limpia los pending_vouchers después de sincronizar exitosamente.
     * 
     * ESTRATEGIA: Cuando un voucher se sincroniza exitosamente, el balance real en blockchain
     * ya refleja esa transacción. Por lo tanto, todos los pending_vouchers antiguos ya no
     * son necesarios y pueden ser limpiados.
     * 
     * Esto funciona porque:
     * 1. Los pending se crean al momento del pago (offline)
     * 2. Cuando el pago se confirma en blockchain, el balance real ya lo incluye
     * 3. Por lo tanto, mantener los pending causaría doble contabilización
     * 
     * IMPORTANTE: Esta función se ejecuta en el contexto del WorkManager (background thread)
     */
    private suspend fun onSyncSuccess() {
        try {
            // Obtener todos los pending_vouchers no sincronizados
            val pendingDao = database.pendingVoucherDao()
            val allPending = pendingDao.getAllPendingList()
            
            if (allPending.isEmpty()) {
                Log.d("VoucherRepository", "✅ No hay pending vouchers para limpiar")
                return
            }
            
            Log.d("VoucherRepository", "🧹 Limpiando ${allPending.size} pending vouchers...")
            
            // Marcar TODOS como sincronizados
            // (porque el balance real ahora refleja todas las transacciones confirmadas)
            allPending.forEach { pending ->
                pendingDao.markAsSynced(pending.id)
                Log.d("VoucherRepository", "  ✓ Marcado como synced: ${pending.id} (${pending.type}, ${pending.amountAp} AP)")
            }
            
            // Limpiar los sincronizados
            val deletedCount = pendingBalanceRepository.deleteSynced()
            
            Log.d("VoucherRepository", "🧹 Limpiados $deletedCount pending vouchers después de sync exitoso")
            Log.d("VoucherRepository", "📢 NOTA: WalletViewModel se actualizará automáticamente al observar cambios en pending_vouchers")
        } catch (e: Exception) {
            Log.e("VoucherRepository", "❌ Error limpiando pending vouchers: ${e.message}", e)
        }
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
            // Detectar si es un settle (tiene buyer_sig y seller_sig) o un voucher normal
            val isSettle = outboxItem.payload.contains("\"buyer_sig\"") && 
                          outboxItem.payload.contains("\"seller_sig\"")
            
            if (isSettle) {
                // Es un settle request
                val settleRequest: SettleRequest = gson.fromJson(outboxItem.payload, SettleRequest::class.java)
                val response = apiService.settleVoucher(settleRequest)
                
                Log.d("SyncVoucher", "📡 Respuesta del settle: HTTP ${response.code()}")
                
                when (response.code()) {
                    200 -> {
                        val body = response.body()
                        Log.d("SyncVoucher", "📦 Body recibido: status=${body?.status}, message=${body?.message}")
                        
                        if (body?.status == "queued" || body?.status == "already_settled") {
                            Log.d("SyncVoucher", "✅ Settle aceptado con status: ${body.status}")
                            
                            // El voucher fue aceptado, ahora consultar el tx_hash
                            val txResponse = apiService.getTransaction(voucher.id)
                            val txHash = if (txResponse.isSuccessful && txResponse.body() != null) {
                                txResponse.body()!!.tx_hash
                            } else {
                                null
                            }
                            
                            // Si tenemos tx_hash, marcar como SUBIDO_OK, si no, usar RECEIVED
                            val finalStatus = if (txHash != null) {
                                VoucherStatus.SUBIDO_OK
                            } else {
                                VoucherStatus.RECEIVED // Queued pero sin tx_hash aún
                            }
                            
                            voucherDao.updateVoucherStatus(
                                id = voucher.id,
                                status = finalStatus.name,
                                txHash = txHash
                            )
                            
                            // Solo eliminar de outbox si tenemos tx_hash (SUBIDO_OK)
                            if (txHash != null) {
                                outboxDao.deleteOutboxItemById(outboxItem.id)
                                Log.d("SyncVoucher", "✅ Settle sincronizado: ${voucher.id}, tx_hash: $txHash")
                                
                                // Limpiar pending vouchers ahora que el balance real está actualizado
                                onSyncSuccess()
                                
                                // Actualizar nonce cacheado con el valor real del blockchain
                                updateNonceCacheAfterSync(settleRequest.buyer_address)
                            } else {
                                Log.d("SyncVoucher", "⏳ Settle queued, esperando tx_hash: ${voucher.id}")
                            }
                            return true
                        } else {
                            Log.e("SyncVoucher", "❌ Status inesperado en settle: ${body?.status}")
                            Log.e("SyncVoucher", "❌ Body completo: $body")
                            Log.e("SyncVoucher", "❌ Message: ${body?.message}")
                            return false
                        }
                    }
                    409 -> {
                        // Idempotencia: duplicado, consultar estado automáticamente
                        Log.d("SyncVoucher", "⚠️ 409 Conflict (settle), consultando estado del voucher: ${voucher.id}")
                        val txResponse = apiService.getTransaction(voucher.id)
                        if (txResponse.isSuccessful && txResponse.body() != null) {
                            val txBody = txResponse.body()!!
                            val txHash = txBody.tx_hash
                            
                            // Actualizar con tx_hash si está disponible
                            voucherDao.updateVoucherStatus(
                                id = voucher.id,
                                status = if (txHash != null) VoucherStatus.SUBIDO_OK.name else VoucherStatus.RECEIVED.name,
                                txHash = txHash
                            )
                            outboxDao.deleteOutboxItemById(outboxItem.id)
                            Log.d("SyncVoucher", "✅ Voucher ya existe, estado actualizado: ${voucher.id}")
                            
                            // SIEMPRE limpiar pending vouchers cuando se confirma que el voucher ya fue procesado
                            // (incluso si tx_hash no está disponible todavía)
                            onSyncSuccess()
                            
                            // Actualizar nonce cacheado si tenemos tx_hash
                            if (txHash != null) {
                                updateNonceCacheAfterSync(settleRequest.buyer_address)
                            }
                            
                            return true
                        } else {
                            // Si falla la consulta, reintentar después
                            Log.w("SyncVoucher", "⚠️ No se pudo consultar estado del voucher duplicado, reintentando...")
                            return false
                        }
                    }
                    429 -> {
                        // Rate limit o límite de riesgo excedido
                        val errorMsg = try {
                            // Intentar parsear el body como SettleResponse
                            response.body()?.message
                                ?: run {
                                    // Si no hay body, intentar parsear errorBody
                                    val errorBodyStr = response.errorBody()?.string()
                                    if (errorBodyStr != null) {
                                        try {
                                            val errorJson = gson.fromJson(errorBodyStr, Map::class.java)
                                            (errorJson["message"] as? String) ?: "Límite de riesgo excedido o rate limit"
                                        } catch (e: Exception) {
                                            errorBodyStr
                                        }
                                    } else {
                                        "Límite de riesgo excedido o rate limit"
                                    }
                                }
                        } catch (e: Exception) {
                            "Límite de riesgo excedido o rate limit"
                        }
                        Log.w("SyncVoucher", "⚠️ 429 Rate Limit: $errorMsg para voucher ${voucher.id}")
                        
                        // Guardar error pero permitir reintento con backoff
                        voucherDao.updateVoucherError(
                            id = voucher.id,
                            status = VoucherStatus.GUARDADO_SIN_SENAL.name, // Revertir a estado inicial para reintentar
                            error = errorMsg
                        )
                        // NO eliminar de outbox, permitir reintento
                        return false // Retornar false para que el Worker reintente con backoff
                    }
                    422 -> {
                        // Error de validación de firmas, no reintentar
                        val errorMsg = response.body()?.message ?: "Error de validación de firmas"
                        voucherDao.updateVoucherError(
                            id = voucher.id,
                            status = VoucherStatus.ERROR.name,
                            error = errorMsg
                        )
                        outboxDao.deleteOutboxItemById(outboxItem.id)
                        Log.e("SyncVoucher", "❌ Error 422 en settle: $errorMsg")
                        return true
                    }
                    in 400..499 -> {
                        // Error de validación (excepto 422 y 429 que ya se manejaron), no reintentar
                        val errorMsg = try {
                            response.body()?.message ?: response.errorBody()?.string() ?: "Error de validación"
                        } catch (e: Exception) {
                            "Error de validación (código ${response.code()})"
                        }
                        voucherDao.updateVoucherError(
                            id = voucher.id,
                            status = VoucherStatus.ERROR.name,
                            error = "Error de validación: $errorMsg"
                        )
                        outboxDao.deleteOutboxItemById(outboxItem.id)
                        return true
                    }
                    else -> {
                        // Error 5xx u otro código, reintentar con backoff
                        val errorBody = try {
                            response.errorBody()?.string() ?: response.body()?.toString() ?: "Sin body"
                        } catch (e: Exception) {
                            "Error leyendo body: ${e.message}"
                        }
                        Log.e("SyncVoucher", "⚠️ Error HTTP ${response.code()} en settle")
                        Log.e("SyncVoucher", "⚠️ Error body: $errorBody")
                        return false
                    }
                }
            } else {
                // Es un voucher request normal
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
                        
                        // Limpiar pending vouchers después de sync exitoso
                        onSyncSuccess()
                        
                        return true
                    }
                    409 -> {
                        // Idempotencia: duplicado, consultar estado automáticamente
                        Log.d("SyncVoucher", "⚠️ 409 Conflict, consultando estado del voucher: ${voucher.id}")
                        val txResponse = apiService.getTransaction(voucher.id)
                        if (txResponse.isSuccessful && txResponse.body() != null) {
                            val txBody = txResponse.body()!!
                            val txHash = txBody.tx_hash
                            
                            // Actualizar con tx_hash si está disponible
                            voucherDao.updateVoucherStatus(
                                id = voucher.id,
                                status = if (txHash != null) VoucherStatus.SUBIDO_OK.name else VoucherStatus.RECEIVED.name,
                                txHash = txHash
                            )
                            outboxDao.deleteOutboxItemById(outboxItem.id)
                            Log.d("SyncVoucher", "✅ Voucher ya existe, estado actualizado: ${voucher.id}")
                            
                            // SIEMPRE limpiar pending vouchers cuando se confirma que el voucher ya fue procesado
                            // (incluso si tx_hash no está disponible todavía)
                            onSyncSuccess()
                            
                            return true
                        } else {
                            // Si falla la consulta, reintentar después
                            Log.w("SyncVoucher", "⚠️ No se pudo consultar estado del voucher duplicado, reintentando...")
                            return false
                        }
                    }
                    429 -> {
                        // Rate limit o límite de riesgo excedido
                        val errorMsg = try {
                            // Intentar parsear el body o errorBody
                            val errorBodyStr = response.errorBody()?.string()
                            if (errorBodyStr != null) {
                                try {
                                    val errorJson = gson.fromJson(errorBodyStr, Map::class.java)
                                    (errorJson["message"] as? String) ?: "Límite de riesgo excedido o rate limit"
                                } catch (e: Exception) {
                                    errorBodyStr
                                }
                            } else {
                                "Límite de riesgo excedido o rate limit"
                            }
                        } catch (e: Exception) {
                            "Límite de riesgo excedido o rate limit"
                        }
                        Log.w("SyncVoucher", "⚠️ 429 Rate Limit: $errorMsg para voucher ${voucher.id}")
                        
                        // Guardar error pero permitir reintento con backoff
                        voucherDao.updateVoucherError(
                            id = voucher.id,
                            status = VoucherStatus.GUARDADO_SIN_SENAL.name, // Revertir a estado inicial para reintentar
                            error = errorMsg
                        )
                        // NO eliminar de outbox, permitir reintento
                        return false // Retornar false para que el Worker reintente con backoff
                    }
                    in 400..499 -> {
                        // Error de validación (excepto 422 y 429 que ya se manejaron), no reintentar
                        val errorMsg = try {
                            response.errorBody()?.string() ?: "Error de validación"
                        } catch (e: Exception) {
                            "Error de validación (código ${response.code()})"
                        }
                        voucherDao.updateVoucherError(
                            id = voucher.id,
                            status = VoucherStatus.ERROR.name,
                            error = "Error de validación: $errorMsg"
                        )
                        outboxDao.deleteOutboxItemById(outboxItem.id)
                        return true
                    }
                    else -> {
                        // Error 5xx, reintentar con backoff
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            // Timeout o error de red, reintentar
            Log.e("SyncVoucher", "❌ Excepción en sync: ${e.message}", e)
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
            
            // Obtener dirección del wallet actual
            val walletAddress = WalletConfig.getCurrentAddress(context)
            
            val base = PaymentBase(
                asset = "AP",
                buyer_address = walletAddress,
                expiry = expiry,
                offer_id = offerId,
                seller_address = walletAddress,
                amount_ap = amountAp
            )
            
            // Canonicalizar (debe ser idéntico al backend)
            val canonical = VoucherCanonicalizer.canonicalizePaymentBase(base)
            Log.d("SettleDemo", "Canonical: $canonical")
            
            // Firmar con la clave privada del wallet (mismo wallet para buyer/seller)
            val privateKey = WalletConfig.getCurrentPrivateKey(context)
            val buyerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
            val sellerSig = EthereumSigner.signMessageEip191(canonical, privateKey)
            
            Log.d("SettleDemo", "Buyer sig: $buyerSig")
            Log.d("SettleDemo", "Seller sig: $sellerSig")
            
            // Crear request
            // NOTA: Esta función es legacy para testing de self-transfers
            // No requiere permit real, así que se usan valores por defecto
            val request = SettleRequest(
                offer_id = offerId,
                amount_ap = amountAp,
                asset = "AP",
                expiry = expiry,
                buyer_address = walletAddress,
                seller_address = walletAddress,
                buyer_sig = buyerSig,
                seller_sig = sellerSig,
                canonical = canonical,
                // Valores por defecto para permit (no aplicable en self-transfers)
                permit = com.g22.offline_blockchain_payments.data.api.PermitData(
                    owner = "",
                    spender = "",
                    value = "",
                    nonce = 0L,
                    deadline = 0L
                ),
                permit_sig = com.g22.offline_blockchain_payments.data.api.PermitSignature(
                    v = 0,
                    r = "",
                    s = ""
                )
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

