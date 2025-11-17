package com.g22.offline_blockchain_payments.data.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.nio.charset.StandardCharsets

/**
 * Utilidades para firmar mensajes según el estándar EIP-191 (Ethereum).
 * 
 * Implementa el mismo formato que ethers.js signMessage():
 * - Prefijo: "\x19Ethereum Signed Message:\n" + length
 * - Hash del mensaje prefijado
 * - Firma ECDSA
 */
object EthereumSigner {
    
    /**
     * Firma un mensaje usando el estándar EIP-191 (Ethereum Signed Message).
     * 
     * @param message Mensaje canónico a firmar
     * @param privateKey Clave privada en formato "0x..." o sin prefijo
     * @return Firma en formato "0x..." (65 bytes: r + s + v)
     */
    fun signMessageEip191(message: String, privateKey: String): String {
        val credentials = Credentials.create(privateKey)
        
        // Crear el prefijo EIP-191: "\x19Ethereum Signed Message:\n" + length
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val prefix = "\u0019Ethereum Signed Message:\n${messageBytes.size}"
        val prefixBytes = prefix.toByteArray(StandardCharsets.UTF_8)
        
        // Concatenar prefijo + mensaje
        val prefixedMessage = ByteArray(prefixBytes.size + messageBytes.size)
        System.arraycopy(prefixBytes, 0, prefixedMessage, 0, prefixBytes.size)
        System.arraycopy(messageBytes, 0, prefixedMessage, prefixBytes.size, messageBytes.size)
        
        // Hash del mensaje prefijado
        val messageHash = Hash.sha3(prefixedMessage)
        
        // Firmar el hash
        val signatureData = Sign.signMessage(messageHash, credentials.ecKeyPair, false)
        
        // Construir la firma en formato 0x + r + s + v
        // signatureData.r y signatureData.s son ByteArray, necesitamos convertirlos a BigInteger
        val r = Numeric.toBigInt(signatureData.r)
        val s = Numeric.toBigInt(signatureData.s)
        
        // v es un ByteArray en SignatureData, extraemos el primer byte
        val vByte: Byte = signatureData.v[0]
        
        val signature = ByteArray(65)
        System.arraycopy(Numeric.toBytesPadded(r, 32), 0, signature, 0, 32)
        System.arraycopy(Numeric.toBytesPadded(s, 32), 0, signature, 32, 32)
        signature[64] = vByte
        
        return Numeric.toHexString(signature)
    }
    
    /**
     * Obtiene la dirección Ethereum desde una clave privada.
     * 
     * @param privateKey Clave privada en formato "0x..." o sin prefijo
     * @return Dirección Ethereum en formato "0x..." (lowercase)
     */
    fun getAddressFromPrivateKey(privateKey: String): String {
        val credentials = Credentials.create(privateKey)
        return credentials.address.lowercase()
    }
    
    /**
     * Recupera la dirección desde una firma y mensaje (para verificación).
     * 
     * @param message Mensaje original
     * @param signature Firma en formato "0x..."
     * @return Dirección que firmó el mensaje
     */
    fun recoverAddress(message: String, signature: String): String {
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val prefix = "\u0019Ethereum Signed Message:\n${messageBytes.size}"
        val prefixBytes = prefix.toByteArray(StandardCharsets.UTF_8)
        
        val prefixedMessage = ByteArray(prefixBytes.size + messageBytes.size)
        System.arraycopy(prefixBytes, 0, prefixedMessage, 0, prefixBytes.size)
        System.arraycopy(messageBytes, 0, prefixedMessage, prefixBytes.size, messageBytes.size)
        
        val messageHash = Hash.sha3(prefixedMessage)
        val signatureBytes = Numeric.hexStringToByteArray(signature)
        
        val r = Numeric.toBigInt(signatureBytes.sliceArray(0..31))
        val s = Numeric.toBigInt(signatureBytes.sliceArray(32..63))
        val v = signatureBytes[64].toInt()
        
        val signatureData = Sign.SignatureData(
            v.toByte(),
            Numeric.toBytesPadded(r, 32),
            Numeric.toBytesPadded(s, 32)
        )
        
        val publicKey = Sign.signedMessageHashToKey(messageHash, signatureData)
        val address = "0x" + Keys.getAddress(publicKey)
        
        return address.lowercase()
    }
}

