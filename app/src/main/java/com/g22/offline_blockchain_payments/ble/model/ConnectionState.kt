package com.g22.offline_blockchain_payments.ble.model

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Advertising : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    data class Success(val message: String) : ConnectionState()
}

