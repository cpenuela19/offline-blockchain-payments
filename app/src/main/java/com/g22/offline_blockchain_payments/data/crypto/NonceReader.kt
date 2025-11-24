package com.g22.offline_blockchain_payments.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

/**
 * Consulta y cachea el nonce actual de un usuario desde el contrato ERC20Permit.
 * 
 * Sistema de caché para pagos offline:
 * - Cachea el último nonce conocido en SharedPreferences
 * - Incrementa localmente por cada transacción offline
 * - Actualiza con el nonce real cuando se sincroniza
 */
object NonceReader {
    private const val TAG = "NonceReader"
    
    // Configuración del contrato
    private const val CONTRACT_ADDRESS = "0x2D9972CB971B42171f5836b7299b98898a5E7d6d"
    private const val RPC_URL = "https://sepolia.infura.io/v3/6eb72d4783fd4263b9bb4b32f43cb574"
    
    // SharedPreferences para caché
    private const val PREFS_NAME = "nonce_cache"
    private const val KEY_PREFIX = "nonce_"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Obtiene el nonce actual del usuario desde el contrato
     * 
     * @param userAddress Dirección del usuario (owner)
     * @return Nonce actual (BigInteger)
     * @throws Exception Si falla la consulta RPC
     */
    suspend fun getNonce(userAddress: String): BigInteger = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📡 Consultando nonce para $userAddress...")
            
            val web3j = Web3j.build(HttpService(RPC_URL))
            
            // Construir la llamada a nonces(address)
            val function = Function(
                "nonces",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<Uint256>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            // Hacer la llamada eth_call
            val ethCall = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    userAddress,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()
            
            if (ethCall.hasError()) {
                val errorMsg = "Error en RPC: ${ethCall.error.message}"
                Log.e(TAG, "❌ $errorMsg")
                throw Exception(errorMsg)
            }
            
            // Decodificar la respuesta
            val result = ethCall.value
            val decodedResult = FunctionReturnDecoder.decode(result, function.outputParameters)
            
            if (decodedResult.isEmpty()) {
                throw Exception("Respuesta vacía del contrato")
            }
            
            val nonce = (decodedResult[0].value as BigInteger)
            
            Log.d(TAG, "✅ Nonce actual: $nonce")
            
            web3j.shutdown()
            
            return@withContext nonce
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo nonce: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Obtiene el nonce como Long (para compatibilidad con PermitSigner)
     */
    suspend fun getNonceAsLong(userAddress: String): Long {
        val nonce = getNonce(userAddress)
        
        if (nonce > BigInteger.valueOf(Long.MAX_VALUE)) {
            throw IllegalStateException("Nonce demasiado grande: $nonce")
        }
        
        return nonce.toLong()
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // FUNCIONES DE CACHÉ (para pagos offline)
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Obtiene el nonce cacheado localmente (sin consultar blockchain)
     * @return Nonce cacheado o 0 si no existe
     */
    fun getCachedNonce(context: Context, userAddress: String): Long {
        val prefs = getPrefs(context)
        val key = KEY_PREFIX + userAddress.lowercase()
        val cached = prefs.getLong(key, 0L)
        Log.d(TAG, "📦 Nonce cacheado para $userAddress: $cached")
        return cached
    }
    
    /**
     * Guarda el nonce en caché local
     */
    fun setCachedNonce(context: Context, userAddress: String, nonce: Long) {
        val prefs = getPrefs(context)
        val key = KEY_PREFIX + userAddress.lowercase()
        prefs.edit().putLong(key, nonce).apply()
        Log.d(TAG, "💾 Nonce cacheado guardado para $userAddress: $nonce")
    }
    
    /**
     * Incrementa el nonce cacheado (para transacciones offline)
     * @return El nuevo nonce después de incrementar
     */
    fun incrementCachedNonce(context: Context, userAddress: String): Long {
        val current = getCachedNonce(context, userAddress)
        val newNonce = current + 1
        setCachedNonce(context, userAddress, newNonce)
        Log.d(TAG, "⬆️ Nonce incrementado para $userAddress: $current → $newNonce")
        return newNonce
    }
    
    /**
     * Obtiene el nonce con estrategia de caché:
     * - Online: Consulta blockchain y actualiza caché
     * - Offline: Usa caché local
     * 
     * @param context Contexto de Android
     * @param userAddress Dirección del usuario
     * @param isOnline Si hay conexión a internet
     * @return Nonce actual (real o cacheado)
     */
    suspend fun getNonceWithCache(
        context: Context,
        userAddress: String,
        isOnline: Boolean
    ): Long {
        return if (isOnline) {
            try {
                Log.d(TAG, "🌐 Online: Consultando nonce real desde blockchain...")
                val realNonce = getNonceAsLong(userAddress)
                
                // Actualizar caché con el valor real
                setCachedNonce(context, userAddress, realNonce)
                
                Log.d(TAG, "✅ Nonce real obtenido y cacheado: $realNonce")
                realNonce
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error consultando nonce, usando caché: ${e.message}")
                // Fallback a caché si falla la consulta
                getCachedNonce(context, userAddress)
            }
        } else {
            Log.d(TAG, "📴 Offline: Usando nonce cacheado")
            getCachedNonce(context, userAddress)
        }
    }
    
    /**
     * Resetea el caché de nonce (útil para testing o migración)
     */
    fun clearNonceCache(context: Context, userAddress: String? = null) {
        val prefs = getPrefs(context)
        if (userAddress != null) {
            val key = KEY_PREFIX + userAddress.lowercase()
            prefs.edit().remove(key).apply()
            Log.d(TAG, "🗑️ Caché eliminado para $userAddress")
        } else {
            prefs.edit().clear().apply()
            Log.d(TAG, "🗑️ Caché de nonces completamente limpiado")
        }
    }
}

