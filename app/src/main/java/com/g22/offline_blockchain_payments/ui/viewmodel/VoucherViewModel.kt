package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.repository.VoucherRepository
import com.g22.offline_blockchain_payments.ui.data.Role
import kotlinx.coroutines.launch

class VoucherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VoucherRepository(application)
    
    val allVouchers = repository.getAllVouchers()
    
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

