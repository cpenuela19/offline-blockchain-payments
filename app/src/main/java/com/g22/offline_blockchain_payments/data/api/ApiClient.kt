package com.g22.offline_blockchain_payments.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Para emulador Android: usar "http://10.0.2.2:3000"
    // Para dispositivo físico: usar la IP WiFi de tu máquina en la red local
    // En Windows: ejecuta `ipconfig` y busca "Adaptador de LAN inalámbrica Wi-Fi"
    // IP actual de tu PC: 192.168.10.4
    private const val BASE_URL = "http://192.168.10.4:3000" // IP WiFi correcta
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: VoucherApiService = retrofit.create(VoucherApiService::class.java)
}

