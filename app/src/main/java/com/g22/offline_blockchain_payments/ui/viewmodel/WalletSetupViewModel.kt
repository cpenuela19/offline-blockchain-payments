package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.wallet.SeedPhraseGenerator
import com.g22.offline_blockchain_payments.data.wallet.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de setup de wallet.
 * Gestiona la generación de wallet, seed phrase y configuración de PIN.
 */
class WalletSetupViewModel(application: Application) : AndroidViewModel(application) {

    sealed class SetupState {
        object Initial : SetupState()
        data class WalletGenerated(val seedPhrase: List<String>) : SetupState()
        data class SeedPhraseConfirmed(val seedPhrase: List<String>) : SetupState()
        object PinSet : SetupState()
        object Completed : SetupState()
        data class Error(val message: String) : SetupState()
    }

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Initial)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    private var generatedPrivateKey: String? = null
    private var generatedSeedPhrase: List<String>? = null
    private var pendingPin: String? = null

    /**
     * Genera un nuevo wallet (clave privada + seed phrase).
     */
    fun generateWallet() {
        android.util.Log.d("WalletSetupViewModel", "🟢 generateWallet() llamado")
        viewModelScope.launch {
            try {
                android.util.Log.d("WalletSetupViewModel", "🔄 Generando wallet...")
                val (privateKey, seedPhrase) = WalletManager.generateNewWallet()
                generatedPrivateKey = privateKey
                generatedSeedPhrase = seedPhrase
                android.util.Log.d("WalletSetupViewModel", "✅ Wallet generado, seed phrase: ${seedPhrase.size} palabras")
                _setupState.value = SetupState.WalletGenerated(seedPhrase)
            } catch (e: Exception) {
                android.util.Log.e("WalletSetupViewModel", "❌ Error generando wallet: ${e.message}", e)
                _setupState.value = SetupState.Error("Error generando wallet: ${e.message}")
            }
        }
    }

    /**
     * Confirma que el usuario ha guardado la seed phrase.
     */
    fun confirmSeedPhrase() {
        val seedPhrase = generatedSeedPhrase
        if (seedPhrase != null) {
            _setupState.value = SetupState.SeedPhraseConfirmed(seedPhrase)
        } else {
            _setupState.value = SetupState.Error("Seed phrase no generada")
        }
    }

    /**
     * Valida y guarda el PIN de 4 dígitos.
     */
    fun setPin(pin: String, confirmPin: String): Boolean {
        // Validar formato de 4 dígitos
        if (!pin.matches(Regex("\\d{4}"))) {
            _setupState.value = SetupState.Error("El PIN debe tener 4 dígitos")
            return false
        }

        // Validar confirmación
        if (pin != confirmPin) {
            _setupState.value = SetupState.Error("Los PINs no coinciden")
            return false
        }

        pendingPin = pin
        _setupState.value = SetupState.PinSet
        return true
    }

    /**
     * Completa el setup guardando el wallet cifrado.
     * NOTA: El PIN no se usa para cifrar, solo se valida externamente.
     * El cifrado se hace con Android Keystore.
     */
    fun completeSetup() {
        viewModelScope.launch {
            try {
                val privateKey = generatedPrivateKey
                if (privateKey == null) {
                    _setupState.value = SetupState.Error("Clave privada no generada")
                    return@launch
                }

                if (pendingPin == null) {
                    _setupState.value = SetupState.Error("PIN no configurado")
                    return@launch
                }

                android.util.Log.d("WalletSetupViewModel", "🔄 Guardando wallet cifrado...")
                // Guardar wallet cifrado (el PIN ya fue validado, no se usa para cifrar)
                WalletManager.saveWallet(privateKey, getApplication())
                android.util.Log.d("WalletSetupViewModel", "✅ Wallet guardado correctamente")
                
                // Marcar seed phrase como mostrada
                WalletManager.markSeedPhraseAsShown(getApplication())

                // Limpiar datos sensibles de memoria
                generatedPrivateKey = null
                generatedSeedPhrase = null
                pendingPin = null

                _setupState.value = SetupState.Completed
            } catch (e: Exception) {
                android.util.Log.e("WalletSetupViewModel", "❌ Error guardando wallet: ${e.message}", e)
                _setupState.value = SetupState.Error("Error guardando wallet: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Obtiene la seed phrase generada (si existe).
     */
    fun getSeedPhrase(): List<String>? {
        return generatedSeedPhrase
    }

    /**
     * Resetea el estado de setup.
     */
    fun reset() {
        generatedPrivateKey = null
        generatedSeedPhrase = null
        pendingPin = null
        _setupState.value = SetupState.Initial
    }
}


