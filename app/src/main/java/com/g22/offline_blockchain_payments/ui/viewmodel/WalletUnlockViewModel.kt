package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.wallet.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para desbloquear el wallet.
 * Gestiona la autenticación con PIN y biometría.
 */
class WalletUnlockViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UnlockState {
        object Initial : UnlockState()
        object Unlocking : UnlockState()
        object Unlocked : UnlockState()
        data class Error(val message: String) : UnlockState()
    }

    private val _unlockState = MutableStateFlow<UnlockState>(UnlockState.Initial)
    val unlockState: StateFlow<UnlockState> = _unlockState.asStateFlow()

    /**
     * Intenta desbloquear el wallet con PIN.
     * El PIN se valida externamente antes de usar Android Keystore.
     * 
     * @param pin PIN de 4 dígitos
     */
    fun unlockWithPin(pin: String) {
        // Validar formato de 4 dígitos
        if (!pin.matches(Regex("\\d{4}"))) {
            _unlockState.value = UnlockState.Error("El PIN debe tener 4 dígitos")
            return
        }

        viewModelScope.launch {
            try {
                _unlockState.value = UnlockState.Unlocking

                // El PIN se valida externamente (en este caso, solo verificamos formato)
                // El descifrado real requiere autenticación del usuario (PIN/biometría) en Android Keystore
                // Para usar la clave del Keystore, el sistema pedirá automáticamente autenticación
                
                // Intentar desbloquear (esto puede lanzar excepción si falla autenticación)
                WalletManager.unlockWallet(getApplication())

                _unlockState.value = UnlockState.Unlocked
            } catch (e: Exception) {
                _unlockState.value = UnlockState.Error(
                    when {
                        e.message?.contains("authentication", ignoreCase = true) == true -> 
                            "Autenticación requerida. Por favor, usa tu PIN o biometría."
                        e.message?.contains("Keystore", ignoreCase = true) == true -> 
                            "Error con Android Keystore: ${e.message}"
                        else -> 
                            "Error desbloqueando wallet: ${e.message}"
                    }
                )
            }
        }
    }

    /**
     * Intenta desbloquear el wallet usando biometría.
     * Esta función inicia el flujo biométrico, pero el desbloqueo real
     * se hace cuando el usuario completa la autenticación biométrica.
     * 
     * NOTA: El desbloqueo con biometría se debe llamar desde un callback
     * después de que BiometricPrompt autentique al usuario exitosamente.
     */
    fun unlockWithBiometric() {
        viewModelScope.launch {
            try {
                _unlockState.value = UnlockState.Unlocking

                // Desbloquear (el BiometricPrompt debe haber autenticado ya)
                // Si no hay autenticación previa, esto fallará
                WalletManager.unlockWallet(getApplication())

                _unlockState.value = UnlockState.Unlocked
            } catch (e: Exception) {
                _unlockState.value = UnlockState.Error(
                    "Error desbloqueando con biometría: ${e.message}"
                )
            }
        }
    }

    /**
     * Verifica si el wallet está desbloqueado.
     */
    fun isUnlocked(): Boolean {
        return WalletManager.isWalletUnlocked()
    }

    /**
     * Borra el wallet desbloqueado de memoria (cuando la app pasa a background).
     */
    fun onAppBackgrounded() {
        WalletManager.clearUnlockedWallet()
        _unlockState.value = UnlockState.Initial
    }

    /**
     * Verifica el estado del wallet al volver a foreground.
     */
    fun onAppForegrounded() {
        if (!WalletManager.isWalletUnlocked()) {
            _unlockState.value = UnlockState.Initial
        }
    }

    /**
     * Resetea el estado de desbloqueo.
     */
    fun reset() {
        _unlockState.value = UnlockState.Initial
    }
}

