package com.g22.offline_blockchain_payments.data.wallet

import org.bitcoinj.core.Utils
import org.bitcoinj.crypto.MnemonicCode
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.security.SecureRandom

/**
 * Generador de seed phrases BIP39.
 * 
 * IMPORTANTE:
 * - La seed phrase es SOLO una copia de seguridad de la clave privada
 * - NO se usa para derivar la clave privada (no BIP32/BIP44)
 * - La seed phrase solo se muestra una vez al usuario y NUNCA se almacena
 * - La clave privada se genera aleatoriamente de forma independiente
 */
object SeedPhraseGenerator {
    private const val ENTROPY_BITS = 128 // Para 12 palabras BIP39
    private val mnemonicCode = MnemonicCode()

    /**
     * Genera una seed phrase (12 palabras) desde una clave privada existente.
     * 
     * NOTA: Esta función convierte la clave privada a entropía para generar el mnemonic,
     * pero el mnemonic NO se usa para derivar la clave privada. Es solo una representación
     * legible para copia de seguridad.
     * 
     * @param privateKey Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @return Lista de 12 palabras (mnemonic BIP39)
     */
    fun generateSeedPhraseFromPrivateKey(privateKey: String): List<String> {
        // Limpiar prefijo 0x si existe
        val cleanKey = privateKey.removePrefix("0x")
        val keyBytes = Numeric.hexStringToByteArray(cleanKey)

        // Convertir los 32 bytes de la clave privada a 128 bits de entropía
        // Tomar los primeros 16 bytes (128 bits) para el mnemonic
        val entropy = ByteArray(16)
        val bytesToCopy = minOf(16, keyBytes.size)
        System.arraycopy(keyBytes, 0, entropy, 0, bytesToCopy)
        
        // Asegurar que tenemos exactamente 128 bits
        // Si la clave tiene menos de 16 bytes, usar hash para completar
        if (keyBytes.size < 16) {
            val hash = Utils.sha256hash160(keyBytes)
            System.arraycopy(hash, 0, entropy, 0, 16)
        }

        return mnemonicCode.toMnemonic(entropy)
    }

    /**
     * Genera una seed phrase (12 palabras) desde entropía aleatoria.
     * Útil para generar seed phrase independiente (aunque no se use para derivar clave).
     * 
     * @return Lista de 12 palabras (mnemonic BIP39)
     */
    fun generateRandomSeedPhrase(): List<String> {
        val random = SecureRandom()
        val entropy = ByteArray(ENTROPY_BITS / 8) // 16 bytes = 128 bits
        random.nextBytes(entropy)
        return mnemonicCode.toMnemonic(entropy)
    }

    /**
     * Valida una seed phrase (verifica que las palabras sean válidas).
     * 
     * @param words Lista de palabras del mnemonic
     * @return true si la seed phrase es válida
     */
    fun validateSeedPhrase(words: List<String>): Boolean {
        return try {
            if (words.size != 12) {
                return false
            }
            mnemonicCode.check(words)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formatea la seed phrase como string legible (palabras separadas por espacios).
     */
    fun formatSeedPhrase(words: List<String>): String {
        return words.joinToString(" ")
    }
}


