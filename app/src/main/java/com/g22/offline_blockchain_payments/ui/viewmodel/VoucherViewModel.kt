package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.repository.VoucherRepository
import com.g22.offline_blockchain_payments.ui.data.Role
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoucherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VoucherRepository(application)
    
    val allVouchers = repository.getAllVouchers()
    
    // Estado para el resultado del test de settle
    private val _settleTestResult = MutableStateFlow<String?>(null)
    val settleTestResult: StateFlow<String?> = _settleTestResult
    
    fun createVoucher(
        role: Role,
        amountAp: Long,
        counterparty: String,
        buyerAlias: String = "Juan",
        sellerAlias: String = "Marta"
    ) {
        viewModelScope.launch {
            repository.createVoucher(
                role = role,
                amountAp = amountAp,
                counterparty = counterparty,
                buyerAlias = buyerAlias,
                sellerAlias = sellerAlias
            )
        }
    }
    
    /**
     * Crea un voucher con settle (offline con firmas criptográficas).
     * El voucher se guarda localmente y se sincroniza cuando hay conexión.
     */
    fun createSettledVoucher(
        role: Role,
        amountAp: Long,
        counterparty: String,
        expiry: Long? = null,
        offerId: String? = null
    ) {
        viewModelScope.launch {
            repository.createSettledVoucher(
                role = role,
                amountAp = amountAp,
                counterparty = counterparty,
                expiry = expiry ?: (System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60)),
                offerId = offerId
            )
        }
    }
    
    /**
     * Método de prueba para el endpoint /v1/vouchers/settle
     * Solo para testing durante el desarrollo (debug builds)
     */
    fun testSettleVoucher() {
        viewModelScope.launch {
            _settleTestResult.value = "Probando..."
            val result = repository.createSettledVoucherDemo()
            _settleTestResult.value = if (result) {
                "✅ Éxito: Voucher aceptado por el servidor"
            } else {
                "❌ Error: Revisa los logs para más detalles"
            }
        }
    }
    
    fun clearSettleTestResult() {
        _settleTestResult.value = null
    }
}

class VoucherViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoucherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoucherViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

