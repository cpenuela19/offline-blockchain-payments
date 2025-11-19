package com.g22.offline_blockchain_payments.data.api

/**
 * Request para crear un nuevo wallet
 */
data class CreateWalletRequest(
    val device_info: String? = null
)

/**
 * Response de creación de wallet
 */
data class CreateWalletResponse(
    val phrase10: List<String>,
    val address: String,
    val public_key: String,
    val session_token: String
)

/**
 * Request para login con frase de 10 palabras
 */
data class LoginViaPhraseRequest(
    val phrase10: List<String>
)

/**
 * Response de login con frase
 */
data class LoginViaPhraseResponse(
    val address: String,
    val public_key: String,
    val session_token: String
)

/**
 * Response de obtención de clave privada
 */
data class PrivateKeyResponse(
    val private_key: String
)

/**
 * Request para debug de identidad (académico/debug)
 */
data class IdentityDebugRequest(
    val phrase10: List<String>
)

/**
 * Response de debug de identidad
 */
data class IdentityDebugResponse(
    val address: String,
    val public_key: String,
    val private_key: String
)

