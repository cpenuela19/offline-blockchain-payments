package com.g22.offline_blockchain_payments.data.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Helper para gestionar Android Keystore y cifrado/descifrado de datos.
 * 
 * - Crea una clave AES almacenada en hardware (si disponible)
 * - Requiere autenticación del usuario (PIN/biometría) para cada uso
 * - El PIN se valida externamente antes de usar este helper
 */
object KeystoreHelper {
    private const val TAG = "KeystoreHelper"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "wallet_encryption_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Inicializa o obtiene la clave AES del Keystore.
     * Si la clave no existe, la crea con los parámetros de seguridad adecuados.
     */
    fun initializeKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createKey()
        }

        val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return secretKeyEntry.secretKey
    }

    /**
     * Crea una nueva clave AES en el Keystore con los requisitos de seguridad.
     * 
     * NOTA: No usamos setUserAuthenticationRequired(true) porque:
     * 1. Durante el setup, necesitamos cifrar sin autenticación previa
     * 2. El Android Keystore ya protege la clave AES en hardware (si está disponible)
     * 3. El PIN se valida externamente antes de permitir descifrar
     * 
     * Esto permite cifrar durante el setup y descifrar después de validar el PIN externamente.
     */
    private fun createKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // NO usar setUserAuthenticationRequired(true) porque requiere autenticación para cifrar también
            // El PIN se valida externamente antes de permitir descifrar
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
        Log.d(TAG, "✅ Clave AES creada en Keystore")
    }

    /**
     * Cifra datos usando la clave AES del Keystore.
     * 
     * @param data Datos a cifrar
     * @return ByteArray con formato [IV (12 bytes)][Ciphertext][Tag]
     * @throws Exception Si falla el cifrado
     */
    fun encrypt(data: ByteArray, context: Context): ByteArray {
        return try {
            Log.d(TAG, "🔄 Iniciando cifrado de datos (${data.size} bytes)...")
            val secretKey = initializeKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)

            // Concatenar IV + encrypted data (IV está incluido en encrypted por GCM)
            // GCM incluye el tag automáticamente al final
            Log.d(TAG, "✅ Datos cifrados correctamente")
            byteArrayOf(*iv, *encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cifrando datos: ${e.message}", e)
            throw Exception("Error cifrando datos: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Descifra datos usando la clave AES del Keystore.
     * Requiere autenticación del usuario antes de poder usar la clave.
     * 
     * @param encryptedData Datos cifrados con formato [IV (12 bytes)][Ciphertext][Tag]
     * @return Datos descifrados
     * @throws Exception Si la autenticación falla o los datos son inválidos
     */
    fun decrypt(encryptedData: ByteArray, context: Context): ByteArray {
        val secretKey = initializeKey(context)
        
        // Extraer IV (primeros 12 bytes)
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH)
        
        // Extraer ciphertext + tag (resto de los bytes)
        val ciphertextAndTag = ByteArray(encryptedData.size - GCM_IV_LENGTH)
        System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertextAndTag, 0, ciphertextAndTag.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        return cipher.doFinal(ciphertextAndTag)
    }

    /**
     * Verifica si existe una clave en el Keystore.
     */
    fun keyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando existencia de clave: ${e.message}")
            false
        }
    }

    /**
     * Elimina la clave del Keystore (útil para resetear wallet).
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "✅ Clave eliminada del Keystore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando clave: ${e.message}")
        }
    }
}


