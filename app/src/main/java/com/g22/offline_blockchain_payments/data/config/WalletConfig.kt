package com.g22.offline_blockchain_payments.data.config

import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys

/**
 * Configuración de wallets para la demo.
 *
 * NOTA IMPORTANTE:
 * - En producción, estas claves NO deben estar hardcodeadas.
 * - Para la demo académica, se usan las mismas claves que en el backend (.env):
 *
 *   PRIV_KEY_A -> BUYER_PRIVATE_KEY (Juan)
 *   PRIV_KEY_B -> SELLER_PRIVATE_KEY (María)
 */
object WalletConfig {

    // === Claves privadas (DEMO) ===
    // Estas DEBEN coincidir exactamente con PRIV_KEY_A y PRIV_KEY_B del backend (.env)

    // NOTA: Las claves están invertidas respecto a los roles en el backend
    // PRIV_KEY_A deriva a 0x8846... que usamos como BUYER
    // PRIV_KEY_B deriva a 0xef2a... que usamos como SELLER
    // PRIV_KEY_A (Juan / Buyer)
    const val BUYER_PRIVATE_KEY: String =
        "0xede9ad126ad894521fa6ee36fb8b5b01df22b250e3b334ec66a463267eaebdbc"

    // PRIV_KEY_B (María / Seller)
    const val SELLER_PRIVATE_KEY: String =
        "0x62f00f0389ee43016a60a5783cf05017e3c58e2959dd466471cf0287fc7088b6"

    // === Direcciones esperadas (del backend .env) ===
    // Estas son las direcciones que el backend espera para A_ADDRESS y B_ADDRESS
    // Se usan para validar que las claves privadas son correctas
    // NOTA: PRIV_KEY_A deriva a 0x8846... (SELLER), PRIV_KEY_B deriva a 0xef2a... (BUYER)
    // Por lo tanto, las claves están invertidas respecto a los roles
    private const val EXPECTED_BUYER_ADDRESS = "0x8846f77a51371269a9e84310cc978154adbf7cf8" // PRIV_KEY_A
    private const val EXPECTED_SELLER_ADDRESS = "0xef2a6965823679785813acc2bb8bec7872b660a0" // PRIV_KEY_B

    // === Direcciones derivadas de las claves privadas ===
    // Si todo está bien, estas deberían coincidir con las direcciones que usa el backend.
    // Solo se usan los fallback si ocurre algún error al derivar.

    val BUYER_ADDRESS: String by lazy {
        try {
            val credentials = Credentials.create(BUYER_PRIVATE_KEY)
            val derivedAddress = credentials.address.lowercase()
            
            // Validar que la dirección derivada coincida con la esperada
            if (derivedAddress != EXPECTED_BUYER_ADDRESS.lowercase()) {
                android.util.Log.w(
                    "WalletConfig",
                    "⚠️ ADVERTENCIA: BUYER_ADDRESS derivada ($derivedAddress) no coincide con la esperada ($EXPECTED_BUYER_ADDRESS)"
                )
            }
            
            derivedAddress
        } catch (e: Exception) {
            android.util.Log.e("WalletConfig", "❌ Error derivando BUYER_ADDRESS: ${e.message}")
            // Fallback histórico (no debería usarse si la clave es válida)
            EXPECTED_BUYER_ADDRESS
        }
    }

    val SELLER_ADDRESS: String by lazy {
        try {
            val credentials = Credentials.create(SELLER_PRIVATE_KEY)
            val derivedAddress = credentials.address.lowercase()
            
            // Validar que la dirección derivada coincida con la esperada
            if (derivedAddress != EXPECTED_SELLER_ADDRESS.lowercase()) {
                android.util.Log.w(
                    "WalletConfig",
                    "⚠️ ADVERTENCIA: SELLER_ADDRESS derivada ($derivedAddress) no coincide con la esperada ($EXPECTED_SELLER_ADDRESS)"
                )
            }
            
            derivedAddress
        } catch (e: Exception) {
            android.util.Log.e("WalletConfig", "❌ Error derivando SELLER_ADDRESS: ${e.message}")
            // Fallback histórico (no debería usarse si la clave es válida)
            EXPECTED_SELLER_ADDRESS
        }
    }

    /**
     * Obtiene la clave privada según el rol.
     *
     * @param isBuyer true si el rol es comprador (Juan), false si es vendedor (María).
     */
    fun getPrivateKeyForRole(isBuyer: Boolean): String {
        return if (isBuyer) BUYER_PRIVATE_KEY else SELLER_PRIVATE_KEY
    }

    /**
     * Obtiene la dirección según el rol.
     *
     * @param isBuyer true si el rol es comprador (Juan), false si es vendedor (María).
     */
    fun getAddressForRole(isBuyer: Boolean): String {
        return if (isBuyer) BUYER_ADDRESS else SELLER_ADDRESS
    }
}
