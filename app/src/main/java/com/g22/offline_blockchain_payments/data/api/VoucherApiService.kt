package com.g22.offline_blockchain_payments.data.api

import retrofit2.Response
import retrofit2.http.*

interface VoucherApiService {
    @POST("/v1/vouchers")
    suspend fun createVoucher(
        @Body request: VoucherRequest
    ): Response<VoucherResponse>
    
    @GET("/v1/tx/{offer_id}")
    suspend fun getTransaction(
        @Path("offer_id") offerId: String
    ): Response<TransactionResponse>
    
    @GET("/v1/balance/{alias}")
    suspend fun getBalance(
        @Path("alias") alias: String
    ): Response<BalanceResponse>
    
    @GET("/v1/wallet/balance")
    suspend fun getWalletBalance(
        @Query("address") address: String
    ): Response<WalletBalanceResponse>
    
    @POST("/v1/vouchers/settle")
    suspend fun settleVoucher(
        @Body request: SettleRequest
    ): Response<SettleResponse>
    
    // ═══════════════════════════════════════════════════════════════
    // NUEVOS WALLET ENDPOINTS - TRUE SELF-CUSTODY MODEL
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Registra un nuevo wallet (solo datos públicos)
     * Backend NUNCA recibe palabras ni clave privada
     */
    @POST("/wallet/register")
    suspend fun registerWallet(
        @Body request: RegisterWalletRequest
    ): Response<RegisterWalletResponse>
    
    /**
     * Obtiene información de un wallet existente (para restauración)
     */
    @GET("/wallet/info")
    suspend fun getWalletInfo(
        @Query("address") address: String
    ): Response<WalletInfoResponse>
    
    /**
     * Login con dirección (para restauración)
     */
    @POST("/wallet/login")
    suspend fun loginWallet(
        @Body request: LoginWalletRequest
    ): Response<LoginWalletResponse>
    
    // ═══════════════════════════════════════════════════════════════
    // ENDPOINTS ANTIGUOS - DEPRECATED (A ELIMINAR)
    // ═══════════════════════════════════════════════════════════════
    
    @Deprecated("Backend no debe generar wallets")
    @POST("/wallet/create")
    suspend fun createWallet(
        @Body request: CreateWalletRequest
    ): Response<CreateWalletResponse>
    
    @Deprecated("Backend no debe recibir frases")
    @POST("/auth/login-via-phrase")
    suspend fun loginViaPhrase(
        @Body request: LoginViaPhraseRequest
    ): Response<LoginViaPhraseResponse>
    
    @Deprecated("Backend NUNCA debe enviar claves privadas")
    @GET("/wallet/private-key")
    suspend fun getPrivateKey(
        @Header("X-Session-Token") sessionToken: String
    ): Response<PrivateKeyResponse>
    
    @Deprecated("Endpoint peligroso - ELIMINAR")
    @POST("/wallet/identity-debug")
    suspend fun identityDebug(
        @Body request: IdentityDebugRequest
    ): Response<IdentityDebugResponse>
}

data class BalanceResponse(
    val alias: String,
    val balance_ap: Long
)

data class WalletBalanceResponse(
    val balance_ap: Long
)

