package com.g22.offline_blockchain_payments.data.api

// ============================================================================
// NUEVOS ENDPOINTS - TRUE SELF-CUSTODY MODEL
// ============================================================================

/**
 * Request para registrar un nuevo wallet (solo datos públicos)
 * Backend NUNCA recibe palabras ni clave privada
 */
data class RegisterWalletRequest(
    val address: String,
    val public_key: String
)

/**
 * Response de registro de wallet
 */
data class RegisterWalletResponse(
    val success: Boolean,
    val session_token: String,
    val address: String
)

/**
 * Response de información de wallet (para restauración)
 */
data class WalletInfoResponse(
    val address: String,
    val public_key: String,
    val created_at: Long
)

/**
 * Request para login con dirección (para restauración)
 */
data class LoginWalletRequest(
    val address: String
)

/**
 * Response de login
 */
data class LoginWalletResponse(
    val session_token: String,
    val address: String
)

// ============================================================================
// ENDPOINTS ANTIGUOS - DEPRECATED (A ELIMINAR)
// ============================================================================

/**
 * @deprecated Backend ya no debe generar wallets
 * Request para crear un nuevo wallet
 */
@Deprecated("Backend no debe generar wallets - Usar RegisterWalletRequest")
data class CreateWalletRequest(
    val device_info: String? = null
)

/**
 * @deprecated Backend ya no debe generar wallets
 * Response de creación de wallet
 */
@Deprecated("Backend no debe generar wallets - Usar RegisterWalletResponse")
data class CreateWalletResponse(
    val phrase10: List<String>,
    val address: String,
    val public_key: String,
    val session_token: String
)

/**
 * @deprecated Backend ya no debe recibir frases
 * Request para login con frase de 10 palabras
 */
@Deprecated("Backend no debe recibir frases - Usar LoginWalletRequest")
data class LoginViaPhraseRequest(
    val phrase10: List<String>
)

/**
 * @deprecated Backend ya no debe recibir frases
 * Response de login con frase
 */
@Deprecated("Backend no debe recibir frases - Usar LoginWalletResponse")
data class LoginViaPhraseResponse(
    val address: String,
    val public_key: String,
    val session_token: String
)

/**
 * @deprecated Backend NUNCA debe enviar claves privadas
 * Response de obtención de clave privada
 */
@Deprecated("Backend NUNCA debe enviar claves privadas")
data class PrivateKeyResponse(
    val private_key: String
)

/**
 * @deprecated Endpoint peligroso - ELIMINAR
 * Request para debug de identidad (académico/debug)
 */
@Deprecated("Endpoint peligroso - ELIMINAR")
data class IdentityDebugRequest(
    val phrase10: List<String>
)

/**
 * @deprecated Endpoint peligroso - ELIMINAR
 * Response de debug de identidad
 */
@Deprecated("Endpoint peligroso - ELIMINAR")
data class IdentityDebugResponse(
    val address: String,
    val public_key: String,
    val private_key: String
)
