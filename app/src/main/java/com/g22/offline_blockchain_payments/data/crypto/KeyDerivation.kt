package com.g22.offline_blockchain_payments.data.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derivación de claves privadas desde seed phrases de 10 palabras.
 * 
 * IMPORTANTE:
 * - Usa PBKDF2WithHmacSHA256 (100,000 iteraciones)
 * - Salt fijo: "agropuntos-v1-salt"
 * - Debe ser IDÉNTICO al backend para que las mismas palabras generen la misma clave
 */
object KeyDerivation {
    
    private const val SALT = "agropuntos-v1-salt"
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256 // 32 bytes = 256 bits
    
    /**
     * Deriva una clave privada desde un seed phrase de 10 palabras.
     * 
     * @param phrase10 Lista de 10 palabras en español
     * @return Clave privada en formato hexadecimal con prefijo 0x
     */
    fun derivePrivateKeyFromPhrase(phrase10: List<String>): String {
        // Unir las palabras con espacios
        val phraseString = phrase10.joinToString(" ")
        
        // Derivar 32 bytes usando PBKDF2
        val spec = PBEKeySpec(
            phraseString.toCharArray(),
            SALT.toByteArray(Charsets.UTF_8),
            ITERATIONS,
            KEY_LENGTH
        )
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        
        // Asegurar que son exactamente 32 bytes
        require(keyBytes.size == 32) { "Key derivation must produce 32 bytes" }
        
        // Convertir a BigInteger (clave privada)
        val privateKeyBigInt = BigInteger(1, keyBytes)
        
        // Validar que esté en el rango válido de secp256k1
        // El orden de secp256k1 es aproximadamente 2^256 / 1.27
        val secp256k1Order = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
            16
        )
        
        val validPrivateKey = if (privateKeyBigInt >= secp256k1Order) {
            // Si está fuera del rango, hacer módulo con el orden
            privateKeyBigInt.mod(secp256k1Order)
        } else {
            privateKeyBigInt
        }
        
        // Convertir a hex con prefijo 0x
        return "0x" + validPrivateKey.toString(16).padStart(64, '0')
    }
    
    /**
     * Obtiene la dirección Ethereum desde una clave privada.
     * 
     * @param privateKey Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @return Dirección Ethereum en formato checksummed (0x...)
     */
    fun getAddressFromPrivateKey(privateKey: String): String {
        val cleanKey = privateKey.removePrefix("0x")
        val keyPair = ECKeyPair.create(BigInteger(cleanKey, 16))
        val address = Keys.getAddress(keyPair)
        return "0x$address"
    }
    
    /**
     * Obtiene la clave pública desde una clave privada.
     * 
     * @param privateKey Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @return Clave pública en formato hexadecimal sin comprimir (0x04...)
     */
    fun getPublicKeyFromPrivateKey(privateKey: String): String {
        val cleanKey = privateKey.removePrefix("0x")
        val keyPair = ECKeyPair.create(BigInteger(cleanKey, 16))
        
        // Obtener la clave pública completa (x e y coordinadas)
        val publicKeyBytes = ByteArray(65)
        publicKeyBytes[0] = 0x04 // Prefijo para clave pública sin comprimir
        
        val xBytes = keyPair.publicKey.toByteArray()
        val yBytes = publicKeyBytes.copyOfRange(33, 65) // Esto será calculado por web3j
        
        // Web3j maneja automáticamente la generación completa
        val publicKeyBigInt = keyPair.publicKey
        
        return "0x04" + publicKeyBigInt.toString(16).padStart(128, '0')
    }
    
    /**
     * Crea un objeto Credentials desde una clave privada.
     * Útil para firmar transacciones con web3j.
     * 
     * @param privateKey Clave privada en formato hexadecimal (con o sin prefijo 0x)
     * @return Objeto Credentials de web3j
     */
    fun createCredentials(privateKey: String): Credentials {
        val cleanKey = privateKey.removePrefix("0x")
        val keyPair = ECKeyPair.create(BigInteger(cleanKey, 16))
        return Credentials.create(keyPair)
    }
    
    /**
     * Verifica que una frase de 10 palabras genere consistentemente la misma clave.
     * Útil para testing.
     * 
     * @param phrase10 Lista de 10 palabras
     * @return true si la derivación es determinística
     */
    fun verifyDeterminism(phrase10: List<String>): Boolean {
        val key1 = derivePrivateKeyFromPhrase(phrase10)
        val key2 = derivePrivateKeyFromPhrase(phrase10)
        return key1 == key2
    }
}

