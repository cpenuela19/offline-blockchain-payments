package com.g22.offline_blockchain_payments.data.config

import android.content.Context
import com.g22.offline_blockchain_payments.data.wallet.WalletManager

/**
 * Configuración de wallet seguro.
 * 
 * NOTA: Las claves privadas hardcodeadas han sido eliminadas.
 * El wallet ahora se gestiona de forma segura usando Android Keystore.
 * 
 * Un solo wallet por usuario (roles buyer/seller son contextuales, no criptográficos).
 */
object WalletConfig {

    /**
     * Obtiene la clave privada actual del wallet desbloqueado.
     * 
     * @param context Context de Android (necesario para obtener el wallet)
     * @return Clave privada en formato hexadecimal con prefijo 0x
     * @throws IllegalStateException Si el wallet no está desbloqueado
     */
    fun getCurrentPrivateKey(context: Context): String {
        if (!WalletManager.isWalletUnlocked()) {
            throw IllegalStateException(
                "Wallet no está desbloqueado. " +
                "Por favor, desbloquea el wallet antes de realizar operaciones."
            )
        }
        return WalletManager.getUnlockedPrivateKey()
    }

    /**
     * Obtiene la dirección del wallet actual.
     * 
     * @param context Context de Android
     * @return Dirección Ethereum en formato hexadecimal lowercase
     */
    fun getCurrentAddress(context: Context): String {
        return WalletManager.getWalletAddress(context)
    }

    /**
     * Obtiene la dirección del wallet actual (compatibilidad con código existente).
     * Usa el mismo wallet para buyer y seller (roles son contextuales).
     * 
     * @param context Context de Android
     * @param isBuyer Ignorado (mantiene compatibilidad con código existente)
     * @return Dirección del wallet actual
     */
    fun getAddressForRole(context: Context, isBuyer: Boolean): String {
        // Un solo wallet, roles son contextuales
        return getCurrentAddress(context)
    }

    // === Propiedades de compatibilidad (deprecated) ===
    // Se mantienen temporalmente para evitar errores de compilación,
    // pero deben migrarse a usar getCurrentAddress(context)
    
    /**
     * @deprecated Usar getCurrentAddress(context) en su lugar
     */
    @Deprecated("Usar getCurrentAddress(context)")
    val BUYER_ADDRESS: String
        get() {
            android.util.Log.w(
                "WalletConfig",
                "⚠️ ADVERTENCIA: BUYER_ADDRESS está deprecated. Usa getCurrentAddress(context)."
            )
            return ""
        }

    /**
     * @deprecated Usar getCurrentAddress(context) en su lugar
     */
    @Deprecated("Usar getCurrentAddress(context)")
    val SELLER_ADDRESS: String
        get() {
            android.util.Log.w(
                "WalletConfig",
                "⚠️ ADVERTENCIA: SELLER_ADDRESS está deprecated. Usa getCurrentAddress(context)."
            )
            return ""
        }
}
