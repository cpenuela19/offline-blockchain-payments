package com.g22.offline_blockchain_payments.data.crypto

import android.util.Log
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger

object PermitSigner {
    private const val TAG = "PermitSigner"
    
    // Constantes del contrato
    private const val CONTRACT_ADDRESS = "0x2D9972CB971B42171f5836b7299b98898a5E7d6d"
    private const val MOTHER_ACCOUNT = "0xd3D738efE95AEBC39348DBE6dB5789187360a53d"
    private const val CHAIN_ID = 11155111L // Sepolia
    
    data class PermitData(
        val owner: String,
        val spender: String,
        val value: String,
        val nonce: Long,
        val deadline: Long
    )
    
    data class PermitSignature(
        val v: Int,
        val r: String,
        val s: String
    )
    
    /**
     * Firma un permit EIP-2612
     */
    fun signPermit(
        ownerAddress: String,
        valueInWei: String,
        nonce: Long,
        deadline: Long,
        privateKey: String
    ): Pair<PermitData, PermitSignature> {
        try {
            Log.d(TAG, "Firmando permit EIP-2612...")
            Log.d(TAG, "Owner: $ownerAddress")
            Log.d(TAG, "Spender: $MOTHER_ACCOUNT")
            Log.d(TAG, "Value: $valueInWei wei")
            Log.d(TAG, "Nonce: $nonce")
            Log.d(TAG, "Deadline: $deadline")
            
            val permitData = PermitData(
                owner = ownerAddress,
                spender = MOTHER_ACCOUNT,
                value = valueInWei,
                nonce = nonce,
                deadline = deadline
            )
            
            val credentials = Credentials.create(privateKey)
            
            // Domain Separator
            val domainSeparator = buildDomainSeparator()
            Log.d(TAG, "Domain Separator: $domainSeparator")
            
            // Struct Hash
            val structHash = buildPermitStructHash(permitData)
            Log.d(TAG, "Struct Hash: $structHash")
            
            // EIP-712 message: \x19\x01 + domainSeparator + structHash
            val messageBytes = Numeric.hexStringToByteArray(
                "0x1901" + 
                Numeric.cleanHexPrefix(domainSeparator) + 
                Numeric.cleanHexPrefix(structHash)
            )
            
            // CRÍTICO: Hash final del mensaje EIP-712
            val messageHash = Hash.sha3(messageBytes)
            
            Log.d(TAG, "Mensaje EIP-712 (pre-hash): ${Numeric.toHexString(messageBytes)}")
            Log.d(TAG, "Mensaje EIP-712 (hash): ${Numeric.toHexString(messageHash)}")
            
            // Firmar el hash (needToHash = false porque ya está hasheado)
            val signatureData = Sign.signMessage(messageHash, credentials.ecKeyPair, false)
            
            val signature = PermitSignature(
                v = signatureData.v[0].toInt(),
                r = Numeric.toHexString(signatureData.r),
                s = Numeric.toHexString(signatureData.s)
            )
            
            Log.d(TAG, "✅ Permit firmado exitosamente")
            Log.d(TAG, "v: ${signature.v}")
            Log.d(TAG, "r: ${signature.r}")
            Log.d(TAG, "s: ${signature.s}")
            
            return Pair(permitData, signature)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error firmando permit: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Construye el Domain Separator según EIP-712
     */
    private fun buildDomainSeparator(): String {
        val typeHash = "0x8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f"
        val nameHash = Hash.sha3String("AgroPuntos")
        val versionHash = Hash.sha3String("1")
        val chainIdHex = Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(CHAIN_ID), 64)
        val contractHex = Numeric.toHexStringWithPrefixZeroPadded(
            Numeric.toBigInt(CONTRACT_ADDRESS), 
            64
        )
        
        val encoded = typeHash + 
            Numeric.cleanHexPrefix(nameHash) + 
            Numeric.cleanHexPrefix(versionHash) + 
            Numeric.cleanHexPrefix(chainIdHex) + 
            Numeric.cleanHexPrefix(contractHex)
        
        return Hash.sha3(encoded)
    }
    
    /**
     * Construye el Struct Hash del Permit según EIP-2612
     */
    private fun buildPermitStructHash(data: PermitData): String {
        val typeHash = "0x6e71edae12b1b97f4d1f60370fef10105fa2faae0126114a169c64845d6126c9"
        val ownerHex = Numeric.toHexStringWithPrefixZeroPadded(
            Numeric.toBigInt(data.owner), 
            64
        )
        val spenderHex = Numeric.toHexStringWithPrefixZeroPadded(
            Numeric.toBigInt(data.spender), 
            64
        )
        val valueHex = Numeric.toHexStringWithPrefixZeroPadded(
            BigInteger(data.value), 
            64
        )
        val nonceHex = Numeric.toHexStringWithPrefixZeroPadded(
            BigInteger.valueOf(data.nonce), 
            64
        )
        val deadlineHex = Numeric.toHexStringWithPrefixZeroPadded(
            BigInteger.valueOf(data.deadline), 
            64
        )
        
        val encoded = typeHash + 
            Numeric.cleanHexPrefix(ownerHex) + 
            Numeric.cleanHexPrefix(spenderHex) + 
            Numeric.cleanHexPrefix(valueHex) + 
            Numeric.cleanHexPrefix(nonceHex) + 
            Numeric.cleanHexPrefix(deadlineHex)
        
        return Hash.sha3(encoded)
    }
}

