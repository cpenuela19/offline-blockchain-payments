package com.g22.offline_blockchain_payments.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel para gestionar el saldo de AgroPuntos del usuario
 */
class WalletViewModel : ViewModel() {
    
    // Puntos disponibles (pueden ser usados inmediatamente)
    private val _availablePoints = MutableStateFlow(58200L)
    val availablePoints: StateFlow<Long> = _availablePoints.asStateFlow()
    
    // Puntos pendientes (recibidos offline, se sincronizarán con internet)
    private val _pendingPoints = MutableStateFlow(20000L)
    val pendingPoints: StateFlow<Long> = _pendingPoints.asStateFlow()
    
    /**
     * Resta puntos disponibles cuando el usuario envía un pago (comprador)
     */
    fun deductPoints(amount: Long): Boolean {
        return if (_availablePoints.value >= amount) {
            _availablePoints.value -= amount
            true
        } else {
            false // No hay suficientes puntos
        }
    }
    
    /**
     * Suma puntos pendientes cuando el usuario recibe un pago (vendedor)
     */
    fun addPendingPoints(amount: Long) {
        _pendingPoints.value += amount
    }
    
    /**
     * Sincroniza los puntos pendientes con la blockchain (cuando hay internet)
     * Los puntos pendientes se convierten en disponibles
     */
    fun syncPendingPoints() {
        _availablePoints.value += _pendingPoints.value
        _pendingPoints.value = 0
    }
    
    /**
     * Resetea el saldo a los valores iniciales (solo para testing)
     */
    fun reset() {
        _availablePoints.value = 58200L
        _pendingPoints.value = 20000L
    }
}

