package com.g22.offline_blockchain_payments.ble.model

data class Message(
    val text: String,
    val isSent: Boolean, // true = enviado por mí, false = recibido
    val timestamp: Long = System.currentTimeMillis()
)

