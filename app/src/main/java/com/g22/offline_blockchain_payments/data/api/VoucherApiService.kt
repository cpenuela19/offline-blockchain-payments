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
    
    @POST("/v1/vouchers/settle")
    suspend fun settleVoucher(
        @Body request: SettleRequest
    ): Response<SettleResponse>
    
    // ─────────────────────────── Wallet Endpoints ───────────────────────────
    
    @POST("/wallet/create")
    suspend fun createWallet(
        @Body request: CreateWalletRequest
    ): Response<CreateWalletResponse>
    
    @POST("/auth/login-via-phrase")
    suspend fun loginViaPhrase(
        @Body request: LoginViaPhraseRequest
    ): Response<LoginViaPhraseResponse>
    
    @GET("/wallet/private-key")
    suspend fun getPrivateKey(
        @Header("X-Session-Token") sessionToken: String
    ): Response<PrivateKeyResponse>
}

data class BalanceResponse(
    val alias: String,
    val balance_ap: Long
)

