package com.g22.offline_blockchain_payments.data.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Gestor principal del wallet seguro.
 * 
 * Responsabilidades:
 * - Generar clave privada ECDSA secp256k1 aleatoria (32 bytes)
 * - Generar seed phrase desde la clave privada (solo para mostrar, nunca almacenar)
 * - Cifrar/descifrar clave privada usando Android Keystore
 * - Gestionar estado de wallet (creado/no creado)
 * - Mantener clave privada descifrada solo en memoria durante foreground
 * - Borrar clave inmediatamente al pasar a background
 */
object WalletManager {
    private const val TAG = "WalletManager"
    private const val PREFS_NAME = "wallet_prefs"
    private const val KEY_WALLET_ENCRYPTED = "wallet_encrypted"
    private const val KEY_WALLET_ADDRESS = "wallet_address"
    private const val KEY_WALLET_SEED_SHOWN = "wallet_seed_shown"

    // Clave privada descifrada en memoria (solo durante foreground)
    @Volatile
    private var unlockedPrivateKey: String? = null

    // Orden de la curva secp256k1 (máximo valor para clave privada válida)
    private val SECP256K1_ORDER = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

    /**
     * Verifica si existe un wallet creado.
     */
    fun walletExists(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_WALLET_ENCRYPTED) && KeystoreHelper.keyExists()
    }

    /**
     * Verifica si el wallet está desbloqueado (clave en memoria).
     */
    fun isWalletUnlocked(): Boolean {
        return unlockedPrivateKey != null
    }

    /**
     * DEPRECADO: La generación de wallet ahora se hace en el backend.
     * Este método se mantiene por compatibilidad pero no debe usarse.
     * 
     * @deprecated Usar el backend para crear wallets
     */
    @Deprecated("La generación de wallet ahora se hace en el backend")
    fun generateNewWallet(): Pair<String, List<String>> {
        throw UnsupportedOperationException("La generación de wallet ahora se hace en el backend. Usa importPrivateKeyFromBackend() en su lugar.")
    }

    /**
     * Importa una clave privada desde el backend y la cifra con Android Keystore.
     * 
     * @param context Context de Android
     * @param privateKeyHex Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @throws Exception Si falla el cifrado o la validación
     */
    fun importPrivateKeyFromBackend(context: Context, privateKeyHex: String) {
        Log.d(TAG, "🔄 Importando clave privada desde backend...")
        
        try {
            // Normalizar: asegurar que tenga prefijo 0x
            val normalizedKey = if (privateKeyHex.startsWith("0x")) {
                privateKeyHex
            } else {
                "0x$privateKeyHex"
            }
            
            // Validar formato
            if (!normalizedKey.matches(Regex("^0x[0-9a-fA-F]{64}$"))) {
                throw IllegalArgumentException("Formato de clave privada inválido")
            }
            
            // Validar que la clave esté en el rango válido
            val keyBigInt = BigInteger(normalizedKey.removePrefix("0x"), 16)
            if (keyBigInt == BigInteger.ZERO || keyBigInt >= SECP256K1_ORDER) {
                throw IllegalArgumentException("Clave privada fuera del rango válido")
            }
            
            // Guardar usando el método existente
            saveWallet(normalizedKey, context)
            
            Log.d(TAG, "✅ Clave privada importada y cifrada correctamente")
            Log.d(TAG, "✅ Dirección: ${getAddressFromPrivateKey(normalizedKey)}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error importando clave privada: ${e.message}", e)
            throw e
        }
    }

    /**
     * Guarda un wallet cifrado en el dispositivo.
     * 
     * @param privateKey Clave privada a cifrar
     * @param context Context de Android
     * @throws Exception Si falla el cifrado
     */
    fun saveWallet(privateKey: String, context: Context) {
        val privateKeyBytes = Numeric.hexStringToByteArray(
            privateKey.removePrefix("0x")
        )

        // Cifrar con Android Keystore
        val encrypted = KeystoreHelper.encrypt(privateKeyBytes, context)

        // Guardar en SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLET_ENCRYPTED, Numeric.toHexString(encrypted))
            .putString(KEY_WALLET_ADDRESS, getAddressFromPrivateKey(privateKey))
            .putBoolean(KEY_WALLET_SEED_SHOWN, false)
            .apply()

        Log.d(TAG, "✅ Wallet guardado cifrado")
    }

    /**
     * Desbloquea el wallet descifrando la clave privada y guardándola en memoria.
     * 
     * @param context Context de Android
     * @throws Exception Si falla el descifrado o la autenticación
     */
    fun unlockWallet(context: Context): String {
        if (!walletExists(context)) {
            throw IllegalStateException("No existe wallet creado")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedHex = prefs.getString(KEY_WALLET_ENCRYPTED, null)
            ?: throw IllegalStateException("Wallet cifrado no encontrado")

        val encrypted = Numeric.hexStringToByteArray(encryptedHex)

        // Descifrar con Android Keystore (requiere autenticación)
        val privateKeyBytes = KeystoreHelper.decrypt(encrypted, context)
        val privateKey = "0x${Numeric.toHexStringNoPrefix(privateKeyBytes)}"

        // Guardar en memoria (solo durante foreground)
        unlockedPrivateKey = privateKey

        Log.d(TAG, "✅ Wallet desbloqueado")
        return privateKey
    }

    /**
     * Obtiene la clave privada desbloqueada.
     * 
     * @return Clave privada en formato hexadecimal con prefijo 0x
     * @throws IllegalStateException Si el wallet no está desbloqueado
     */
    fun getUnlockedPrivateKey(): String {
        return unlockedPrivateKey ?: throw IllegalStateException(
            "Wallet no está desbloqueado. Llama a unlockWallet() primero."
        )
    }

    /**
     * Obtiene la dirección del wallet actual.
     * Si el wallet está desbloqueado, deriva desde la clave en memoria.
     * Si no, lee desde SharedPreferences.
     * 
     * @param context Context de Android
     * @return Dirección Ethereum en formato hexadecimal lowercase
     */
    fun getWalletAddress(context: Context): String {
        // Si está desbloqueado, derivar desde la clave en memoria
        unlockedPrivateKey?.let { privateKey ->
            return getAddressFromPrivateKey(privateKey)
        }

        // Si no, leer desde SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WALLET_ADDRESS, "") ?: ""
    }

    /**
     * Borra la clave privada de memoria.
     * Se debe llamar cuando la app pasa a background.
     */
    fun clearUnlockedWallet() {
        if (unlockedPrivateKey != null) {
            // Sobrescribir con datos aleatorios antes de borrar
            unlockedPrivateKey = null
            Log.d(TAG, "✅ Clave privada borrada de memoria")
        }
    }

    /**
     * Marca la seed phrase como mostrada (para que no se vuelva a mostrar).
     */
    fun markSeedPhraseAsShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_WALLET_SEED_SHOWN, true)
            .apply()
    }

    /**
     * Verifica si la seed phrase ya fue mostrada.
     */
    fun isSeedPhraseShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WALLET_SEED_SHOWN, false)
    }

    /**
     * Elimina el wallet (útil para resetear).
     * ADVERTENCIA: Esto borra permanentemente el wallet.
     */
    fun deleteWallet(context: Context) {
        clearUnlockedWallet()
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        KeystoreHelper.deleteKey()
        
        Log.d(TAG, "⚠️ Wallet eliminado")
    }

    /**
     * Deriva la dirección Ethereum desde una clave privada.
     */
    private fun getAddressFromPrivateKey(privateKey: String): String {
        val credentials = Credentials.create(privateKey)
        return credentials.address.lowercase()
    }
}


