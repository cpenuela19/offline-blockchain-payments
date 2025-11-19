package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.api.ApiClient
import com.g22.offline_blockchain_payments.data.database.AppDatabase
import com.g22.offline_blockchain_payments.data.repository.PendingBalanceRepository
import com.g22.offline_blockchain_payments.data.wallet.SessionManager
import com.g22.offline_blockchain_payments.ui.util.observeNetworkStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * ViewModel para gestionar el saldo de AgroPuntos del usuario.
 * Maneja saldo real (on-chain), shadow balance (offline) y vouchers pendientes.
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: PendingBalanceRepository
    private val prefs: SharedPreferences
    
    // Estado de conexión
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    // Saldo real (on-chain) - solo cuando está online
    private val _realBalance = MutableStateFlow(0L)
    val realBalance: StateFlow<Long> = _realBalance.asStateFlow()
    
    // Último saldo real conocido (guardado en SharedPreferences)
    private val _lastKnownRealBalance = MutableStateFlow(0L)
    val lastKnownRealBalance: StateFlow<Long> = _lastKnownRealBalance.asStateFlow()
    
    // Vouchers pendientes
    private val _incomingPending = MutableStateFlow(0L)
    val incomingPending: StateFlow<Long> = _incomingPending.asStateFlow()
    
    private val _outgoingPending = MutableStateFlow(0L)
    val outgoingPending: StateFlow<Long> = _outgoingPending.asStateFlow()
    
    // Shadow balance (calculado)
    private val _shadowBalance = MutableStateFlow(0L)
    val shadowBalance: StateFlow<Long> = _shadowBalance.asStateFlow()
    
    // Saldo disponible para mostrar en UI
    private val _availablePoints = MutableStateFlow(0L)
    val availablePoints: StateFlow<Long> = _availablePoints.asStateFlow()
    
    // Puntos pendientes para mostrar en UI
    private val _pendingPoints = MutableStateFlow(0L)
    val pendingPoints: StateFlow<Long> = _pendingPoints.asStateFlow()
    
    companion object {
        private const val PREFS_NAME = "wallet_balance_prefs"
        private const val KEY_LAST_KNOWN_REAL_BALANCE = "last_known_real_balance"
    }
    
    init {
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val database = AppDatabase.getDatabase(application)
        repository = PendingBalanceRepository(database)
        
        // Cargar último saldo conocido
        _lastKnownRealBalance.value = prefs.getLong(KEY_LAST_KNOWN_REAL_BALANCE, 0L)
        
        // Observar estado de red
        viewModelScope.launch {
            application.observeNetworkStatus().collect { online ->
                _isOnline.value = online
                if (online) {
                    // Cuando se conecta, actualizar saldo real
                    refreshRealBalance()
                } else {
                    // Cuando se desconecta, calcular shadow balance
                    calculateShadowBalance()
                }
            }
        }
        
        // Observar cambios en vouchers pendientes
        viewModelScope.launch {
            repository.getAllPending().collect { pendingList ->
                updatePendingTotals()
            }
        }
        
        // Calcular shadow balance cuando cambian los valores
        viewModelScope.launch {
            combine(
                _lastKnownRealBalance,
                _outgoingPending,
                _incomingPending,
                _isOnline
            ) { lastKnown, outgoing, incoming, online ->
                if (online) {
                    // Online: usar saldo real
                    _realBalance.value
                } else {
                    // Offline: calcular shadow balance
                    computeShadowBalance(lastKnown, outgoing, incoming)
                }
            }.collect { balance ->
                _shadowBalance.value = balance
                updateDisplayedBalances()
            }
        }
        
        // Inicializar
        viewModelScope.launch {
            updatePendingTotals()
            val isOnlineNow = application.observeNetworkStatus().first()
            if (isOnlineNow) {
                refreshRealBalance()
            } else {
                calculateShadowBalance()
            }
        }
    }
    
    /**
     * Calcula el shadow balance usando la fórmula:
     * shadow_balance = real_balance - outgoingPending + incomingPending
     * 
     * Este valor se usa únicamente cuando la app está offline.
     */
    fun computeShadowBalance(
        realBalance: Long,
        outgoingPending: Long,
        incomingPending: Long
    ): Long {
        return realBalance - outgoingPending + incomingPending
    }
    
    /**
     * Actualiza los totales de vouchers pendientes desde Room
     */
    private suspend fun updatePendingTotals() {
        _incomingPending.value = repository.getTotalIncomingPending()
        _outgoingPending.value = repository.getTotalOutgoingPending()
    }
    
    /**
     * Calcula el shadow balance usando el último saldo conocido
     */
    private suspend fun calculateShadowBalance() {
        val shadow = computeShadowBalance(
            _lastKnownRealBalance.value,
            _outgoingPending.value,
            _incomingPending.value
        )
        _shadowBalance.value = shadow
        updateDisplayedBalances()
    }
    
    /**
     * Actualiza los saldos mostrados en la UI
     */
    private fun updateDisplayedBalances() {
        if (_isOnline.value) {
            // Online: mostrar saldo real y pendientes incoming
            _availablePoints.value = _realBalance.value
            _pendingPoints.value = _incomingPending.value
        } else {
            // Offline: mostrar shadow balance y pendientes incoming
            _availablePoints.value = _shadowBalance.value
            _pendingPoints.value = _incomingPending.value
        }
    }
    
    /**
     * Refresca el saldo real desde el backend
     */
    fun refreshRealBalance() {
        viewModelScope.launch {
            if (!_isOnline.value) return@launch
            
            val address = SessionManager.getAddress(getApplication())
            if (address == null) {
                android.util.Log.w("WalletViewModel", "No hay address en sesión")
                return@launch
            }
            
            try {
                val response = ApiClient.apiService.getWalletBalance(address)
                if (response.isSuccessful && response.body() != null) {
                    val balance = response.body()!!.balance_ap
                    _realBalance.value = balance
                    _lastKnownRealBalance.value = balance
                    
                    // Guardar en SharedPreferences
                    prefs.edit().putLong(KEY_LAST_KNOWN_REAL_BALANCE, balance).apply()
                    
                    updateDisplayedBalances()
                    android.util.Log.d("WalletViewModel", "✅ Saldo real actualizado: $balance AP")
                } else {
                    android.util.Log.e("WalletViewModel", "❌ Error obteniendo saldo: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WalletViewModel", "❌ Error llamando al backend", e)
                // Si falla, usar último saldo conocido
                calculateShadowBalance()
            }
        }
    }
    
    /**
     * Agrega un voucher outgoing (cuando el usuario envía)
     */
    suspend fun addOutgoingPending(amountAp: Long) {
        repository.insertPending("outgoing", amountAp)
        updatePendingTotals()
        if (!_isOnline.value) {
            calculateShadowBalance()
        }
    }
    
    /**
     * Agrega un voucher incoming (cuando el usuario recibe)
     */
    suspend fun addIncomingPending(amountAp: Long) {
        repository.insertPending("incoming", amountAp)
        updatePendingTotals()
        if (!_isOnline.value) {
            calculateShadowBalance()
        }
    }
    
    /**
     * Valida si hay suficiente saldo para una operación (offline)
     */
    fun canSpend(amount: Long): Boolean {
        return if (_isOnline.value) {
            _realBalance.value >= amount
        } else {
            _shadowBalance.value >= amount
        }
    }
    
    /**
     * Resta puntos cuando el usuario envía un pago (comprador)
     * En offline, actualiza el shadow balance
     */
    fun deductPoints(amount: Long): Boolean {
        return if (canSpend(amount)) {
            viewModelScope.launch {
                if (_isOnline.value) {
                    // Online: actualizar saldo real
                    refreshRealBalance()
                } else {
                    // Offline: agregar a outgoing y recalcular shadow
                    addOutgoingPending(amount)
                }
            }
            true
        } else {
            false // No hay suficientes puntos
        }
    }
    
    /**
     * Suma puntos cuando el usuario recibe un pago (vendedor)
     */
    fun addPendingPoints(amount: Long) {
        viewModelScope.launch {
            addIncomingPending(amount)
        }
    }
    
    /**
     * Sincroniza vouchers pendientes con el backend
     * Marca como sincronizados cuando el backend confirma
     */
    fun syncPendingVouchers() {
        viewModelScope.launch {
            if (!_isOnline.value) return@launch
            
            // TODO: Implementar sincronización real con el backend
            // Por ahora, solo refrescamos el saldo real
            refreshRealBalance()
            
            // Después de sincronizar, actualizar pendientes
            updatePendingTotals()
        }
    }
}
