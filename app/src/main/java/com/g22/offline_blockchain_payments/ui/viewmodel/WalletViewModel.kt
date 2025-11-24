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
    
    private val database: AppDatabase
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
        database = AppDatabase.getDatabase(application)
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
     * shadow_balance = real_balance - outgoingPending
     * 
     * NOTA: incomingPending NO se suma al disponible.
     * Se muestra por separado como "pendiente" hasta que se confirme en blockchain.
     * 
     * Este valor se usa únicamente cuando la app está offline.
     */
    fun computeShadowBalance(
        realBalance: Long,
        outgoingPending: Long,
        incomingPending: Long  // No se usa, pero se mantiene por compatibilidad
    ): Long {
        // FIX: No sumar incomingPending para evitar doble contabilización
        // El incoming se muestra por separado en _pendingPoints
        return realBalance - outgoingPending
    }
    
    /**
     * Actualiza los totales de vouchers pendientes desde Room
     */
    private suspend fun updatePendingTotals() {
        val oldIncoming = _incomingPending.value
        val oldOutgoing = _outgoingPending.value
        
        android.util.Log.d("WalletViewModel", "🔄 [PENDING] updatePendingTotals llamado")
        android.util.Log.d("WalletViewModel", "  - OLD incoming: $oldIncoming, outgoing: $oldOutgoing")
        
        _incomingPending.value = repository.getTotalIncomingPending()
        _outgoingPending.value = repository.getTotalOutgoingPending()
        
        android.util.Log.d("WalletViewModel", "  - NEW incoming: ${_incomingPending.value}, outgoing: ${_outgoingPending.value}")
        
        // Si los pending bajaron significativamente, probablemente se sincronizaron
        // → Actualizar saldo real desde blockchain
        if (_isOnline.value) {
            val incomingDropped = oldIncoming > 0 && _incomingPending.value < oldIncoming
            val outgoingDropped = oldOutgoing > 0 && _outgoingPending.value < oldOutgoing
            
            android.util.Log.d("WalletViewModel", "  - incomingDropped: $incomingDropped, outgoingDropped: $outgoingDropped")
            
            if (incomingDropped || outgoingDropped) {
                android.util.Log.d("WalletViewModel", "🔄 [SYNC] Pending bajó, refrescando saldo real desde blockchain...")
                refreshRealBalance()
            } else {
                android.util.Log.d("WalletViewModel", "  - No hay cambios significativos en pending")
            }
        } else {
            android.util.Log.d("WalletViewModel", "  - Offline, no refrescando balance real")
            // Offline: recalcular shadow balance con los nuevos pending
            calculateShadowBalance()
        }
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
        android.util.Log.d("WalletViewModel", "🔄 [UI] updateDisplayedBalances llamado")
        android.util.Log.d("WalletViewModel", "  - isOnline: ${_isOnline.value}")
        android.util.Log.d("WalletViewModel", "  - realBalance: ${_realBalance.value}")
        android.util.Log.d("WalletViewModel", "  - shadowBalance: ${_shadowBalance.value}")
        android.util.Log.d("WalletViewModel", "  - incomingPending: ${_incomingPending.value}")
        android.util.Log.d("WalletViewModel", "  - outgoingPending: ${_outgoingPending.value}")
        
        if (_isOnline.value) {
            // Online: mostrar saldo real y pendientes incoming
            _availablePoints.value = _realBalance.value
            _pendingPoints.value = _incomingPending.value
            android.util.Log.d("WalletViewModel", "📊 [UI] ONLINE → availablePoints=${_availablePoints.value}, pendingPoints=${_pendingPoints.value}")
        } else {
            // Offline: mostrar shadow balance y pendientes incoming
            _availablePoints.value = _shadowBalance.value
            _pendingPoints.value = _incomingPending.value
            android.util.Log.d("WalletViewModel", "📊 [UI] OFFLINE → availablePoints=${_availablePoints.value}, pendingPoints=${_pendingPoints.value}")
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
                // Guardar balance anterior para comparación
                val oldRealBalance = _realBalance.value
                val oldOutgoingPending = _outgoingPending.value
                
                val response = ApiClient.apiService.getWalletBalance(address)
                if (response.isSuccessful && response.body() != null) {
                    val newRealBalance = response.body()!!.balance_ap
                    
                    android.util.Log.d("WalletViewModel", "🔄 [REFRESH] Balance actualizado: $oldRealBalance → $newRealBalance")
                    android.util.Log.d("WalletViewModel", "🔄 [REFRESH] outgoingPending actual: $oldOutgoingPending AP")
                    
                    // CRÍTICO: Si el balance real CAMBIÓ Y hay outgoingPending > 0, 
                    // significa que transacciones se confirmaron en blockchain
                    // Por lo tanto, debemos limpiar los outgoingPending para evitar doble deducción
                    // Esto aplica tanto cuando el balance BAJA (por envíos) como cuando SUBE (por recibos)
                    if (newRealBalance != oldRealBalance && oldOutgoingPending > 0) {
                        android.util.Log.d("WalletViewModel", "🧹 [REFRESH] Balance cambió de $oldRealBalance a $newRealBalance → limpiando outgoingPending ($oldOutgoingPending AP)")
                        val deletedCount = repository.deleteAllOutgoingPending()
                        android.util.Log.d("WalletViewModel", "✅ [REFRESH] Limpiados $deletedCount outgoing pending vouchers")
                        
                        // Actualizar totales de pending
                        updatePendingTotals()
                    }
                    
                    _realBalance.value = newRealBalance
                    _lastKnownRealBalance.value = newRealBalance
                    
                    // Guardar en SharedPreferences
                    prefs.edit().putLong(KEY_LAST_KNOWN_REAL_BALANCE, newRealBalance).apply()
                    
                    updateDisplayedBalances()
                    android.util.Log.d("WalletViewModel", "✅ Saldo real actualizado: $newRealBalance AP")
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
        android.util.Log.d("WalletViewModel", "➖ [PENDING] addOutgoingPending($amountAp)")
        repository.insertPending("outgoing", amountAp)
        android.util.Log.d("WalletViewModel", "➖ [PENDING] Outgoing insertado en DB")
        updatePendingTotals()
        if (!_isOnline.value) {
            calculateShadowBalance()
        }
        android.util.Log.d("WalletViewModel", "➖ [PENDING] Nueva outgoingPending total: ${_outgoingPending.value}")
    }
    
    /**
     * Agrega un voucher incoming (cuando el usuario recibe)
     */
    suspend fun addIncomingPending(amountAp: Long) {
        android.util.Log.d("WalletViewModel", "➕ [PENDING] addIncomingPending($amountAp)")
        repository.insertPending("incoming", amountAp)
        android.util.Log.d("WalletViewModel", "➕ [PENDING] Incoming insertado en DB")
        updatePendingTotals()
        if (!_isOnline.value) {
            calculateShadowBalance()
        }
        android.util.Log.d("WalletViewModel", "➕ [PENDING] Nueva incomingPending total: ${_incomingPending.value}")
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
        android.util.Log.d("WalletViewModel", "💸 [DEDUCT] deductPoints($amount) llamado")
        android.util.Log.d("WalletViewModel", "💸 [DEDUCT] canSpend($amount) = ${canSpend(amount)}")
        android.util.Log.d("WalletViewModel", "💸 [DEDUCT] isOnline = ${_isOnline.value}")
        
        return if (canSpend(amount)) {
            viewModelScope.launch {
                if (_isOnline.value) {
                    // Online: actualizar saldo real
                    android.util.Log.d("WalletViewModel", "💸 [DEDUCT] ONLINE: Refrescando saldo real")
                    refreshRealBalance()
                } else {
                    // Offline: agregar a outgoing y recalcular shadow
                    android.util.Log.d("WalletViewModel", "💸 [DEDUCT] OFFLINE: Agregando a outgoing pending")
                    addOutgoingPending(amount)
                }
            }
            android.util.Log.d("WalletViewModel", "💸 [DEDUCT] Retornando TRUE")
            true
        } else {
            android.util.Log.d("WalletViewModel", "💸 [DEDUCT] Retornando FALSE (saldo insuficiente)")
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
     * Revierte un descuento previo (rollback después de error)
     * A diferencia de addPendingPoints(), esto NO crea pending, solo revierte el outgoing
     */
    fun rollbackDeduction(amount: Long) {
        viewModelScope.launch {
            // Buscar y eliminar el outgoing pending correspondiente
            val pendingDao = database.pendingVoucherDao()
            val allOutgoing = pendingDao.getAllPendingList().filter { it.type == "outgoing" }
            
            // Encontrar el pending más reciente con este monto
            val toRemove = allOutgoing.lastOrNull { it.amountAp == amount }
            
            if (toRemove != null) {
                // Marcar como sincronizado para que se elimine
                pendingDao.markAsSynced(toRemove.id)
                repository.deleteSynced()
                updatePendingTotals()
                android.util.Log.d("WalletViewModel", "✅ Rollback: Outgoing de $amount AP eliminado")
            } else {
                android.util.Log.w("WalletViewModel", "⚠️ Rollback: No se encontró outgoing de $amount AP")
            }
            
            // Recalcular shadow balance
            if (!_isOnline.value) {
                calculateShadowBalance()
            } else {
                refreshRealBalance()
            }
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
    
    /**
     * Fuerza la actualización completa de balances después de sincronizar.
     * Llama a esto después de que un voucher se sincroniza exitosamente.
     * 
     * 1. Actualiza saldo real desde blockchain (que ahora incluye la TX)
     * 2. Recalcula totales de pending (que ahora excluye los sincronizados)
     * 3. Actualiza UI
     */
    fun forceRefreshAfterSync() {
        viewModelScope.launch {
            android.util.Log.d("WalletViewModel", "🔄 Forzando refresh completo después de sync...")
            
            // 1. Actualizar pending totals (los sincronizados ya fueron eliminados)
            updatePendingTotals()
            android.util.Log.d("WalletViewModel", "  Incoming pending: ${_incomingPending.value}")
            android.util.Log.d("WalletViewModel", "  Outgoing pending: ${_outgoingPending.value}")
            
            // 2. Si está online, actualizar saldo real desde blockchain
            if (_isOnline.value) {
                refreshRealBalance()
            } else {
                // Si está offline, recalcular shadow balance
                calculateShadowBalance()
            }
            
            android.util.Log.d("WalletViewModel", "✅ Refresh completo finalizado")
        }
    }
}
