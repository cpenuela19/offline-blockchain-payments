package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.api.ApiClient
import com.g22.offline_blockchain_payments.data.api.CreateWalletRequest
import com.g22.offline_blockchain_payments.data.api.LoginViaPhraseRequest
import com.g22.offline_blockchain_payments.data.wallet.SessionManager
import com.g22.offline_blockchain_payments.data.wallet.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de setup de wallet.
 * Gestiona la creación de wallet desde el backend, seed phrase y configuración de PIN.
 */
class WalletSetupViewModel(application: Application) : AndroidViewModel(application) {

    sealed class SetupState {
        object Initial : SetupState()
        object ShowingRestorePhrase : SetupState()
        data class WalletGenerated(val seedPhrase: List<String>) : SetupState()
        data class SeedPhraseConfirmed(val seedPhrase: List<String>) : SetupState()
        object PinSet : SetupState()
        object Completed : SetupState()
        data class Error(val message: String) : SetupState()
    }

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Initial)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    private var generatedSeedPhrase: List<String>? = null
    private var pendingPin: String? = null
    private var pendingPrivateKey: String? = null // Clave privada temporal hasta que se configure el PIN

    /**
     * Crea un nuevo wallet llamando al backend.
     */
    fun createWallet() {
        android.util.Log.d("WalletSetupViewModel", "🟢 createWallet() llamado")
        viewModelScope.launch {
            try {
                android.util.Log.d("WalletSetupViewModel", "🔄 Creando wallet en backend...")
                val request = CreateWalletRequest(device_info = null)
                val response = ApiClient.apiService.createWallet(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    android.util.Log.d("WalletSetupViewModel", "✅ Wallet creado: address=${body.address}")
                    
                    // Guardar datos de sesión
                    SessionManager.saveSession(
                        getApplication(),
                        body.address,
                        body.public_key,
                        body.session_token
                    )
                    
                    // Obtener clave privada del backend (guardarla temporalmente, se cifrará después del PIN)
                    android.util.Log.d("WalletSetupViewModel", "🔄 Obteniendo clave privada del backend...")
                    val privateKeyResponse = ApiClient.apiService.getPrivateKey(body.session_token)
                    
                    if (privateKeyResponse.isSuccessful && privateKeyResponse.body() != null) {
                        val privateKey = privateKeyResponse.body()!!.private_key
                        android.util.Log.d("WalletSetupViewModel", "✅ Clave privada obtenida del backend")
                        
                        // Guardar temporalmente en memoria (se cifrará después de configurar el PIN)
                        pendingPrivateKey = privateKey
                        android.util.Log.d("WalletSetupViewModel", "✅ Clave privada guardada temporalmente (se cifrará después del PIN)")
                    } else {
                        val errorMsg = privateKeyResponse.errorBody()?.string() ?: "Error obteniendo clave privada"
                        android.util.Log.e("WalletSetupViewModel", "❌ Error obteniendo clave privada: $errorMsg")
                        _setupState.value = SetupState.Error("Error obteniendo clave privada: $errorMsg")
                        return@launch
                    }
                    
                    // Guardar frase para mostrar
                    generatedSeedPhrase = body.phrase10
                    android.util.Log.d("WalletSetupViewModel", "✅ Frase recibida: ${body.phrase10.size} palabras")
                    
                    _setupState.value = SetupState.WalletGenerated(body.phrase10)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                    android.util.Log.e("WalletSetupViewModel", "❌ Error creando wallet: $errorMsg")
                    _setupState.value = SetupState.Error("Error creando wallet: $errorMsg")
                }
            } catch (e: Exception) {
                android.util.Log.e("WalletSetupViewModel", "❌ Error creando wallet: ${e.message}", e)
                val errorMsg = when {
                    e.message?.contains("SocketTimeoutException") == true || 
                    e.message?.contains("failed to connect") == true -> {
                        "No se pudo conectar al servidor. Verifica que el backend esté corriendo y que la IP sea correcta."
                    }
                    e.message?.contains("timeout") == true -> {
                        "Tiempo de espera agotado. Verifica tu conexión a internet."
                    }
                    else -> {
                        "Error creando wallet: ${e.message ?: "Error de conexión"}"
                    }
                }
                _setupState.value = SetupState.Error(errorMsg)
            }
        }
    }

    /**
     * Inicia el flujo de restauración de wallet.
     */
    fun startRestoreFlow() {
        _setupState.value = SetupState.ShowingRestorePhrase
    }

    /**
     * Vuelve al estado inicial (pantalla de bienvenida).
     */
    fun goBackToInitial() {
        android.util.Log.d("WalletSetupViewModel", "🔄 goBackToInitial() llamado, volviendo a estado Initial")
        _setupState.value = SetupState.Initial
    }

    /**
     * Restaura un wallet existente usando la frase de 10 palabras.
     */
    fun restoreWallet(phrase10: List<String>) {
        android.util.Log.d("WalletSetupViewModel", "🟢 restoreWallet() llamado con ${phrase10.size} palabras")
        viewModelScope.launch {
            try {
                if (phrase10.size != 10) {
                    _setupState.value = SetupState.Error("Debes ingresar exactamente 10 palabras")
                    return@launch
                }
                
                android.util.Log.d("WalletSetupViewModel", "🔄 Haciendo login con frase...")
                val request = LoginViaPhraseRequest(phrase10 = phrase10)
                val response = ApiClient.apiService.loginViaPhrase(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    android.util.Log.d("WalletSetupViewModel", "✅ Login exitoso: address=${body.address}")
                    
                    // Guardar datos de sesión
                    SessionManager.saveSession(
                        getApplication(),
                        body.address,
                        body.public_key,
                        body.session_token
                    )
                    
                    // Obtener clave privada del backend (si no existe localmente)
                    if (!WalletManager.walletExists(getApplication())) {
                        android.util.Log.d("WalletSetupViewModel", "🔄 Wallet no existe localmente, obteniendo clave privada del backend...")
                        val privateKeyResponse = ApiClient.apiService.getPrivateKey(body.session_token)
                        
                        if (privateKeyResponse.isSuccessful && privateKeyResponse.body() != null) {
                            val privateKey = privateKeyResponse.body()!!.private_key
                            android.util.Log.d("WalletSetupViewModel", "✅ Clave privada obtenida del backend")
                            
                            // Guardar temporalmente en memoria (se cifrará después de configurar el PIN)
                            pendingPrivateKey = privateKey
                            android.util.Log.d("WalletSetupViewModel", "✅ Clave privada guardada temporalmente (se cifrará después del PIN)")
                        } else {
                            val errorMsg = privateKeyResponse.errorBody()?.string() ?: "Error obteniendo clave privada"
                            android.util.Log.e("WalletSetupViewModel", "❌ Error obteniendo clave privada: $errorMsg")
                            _setupState.value = SetupState.Error("Error obteniendo clave privada: $errorMsg")
                            return@launch
                        }
                    } else {
                        android.util.Log.d("WalletSetupViewModel", "✅ Wallet ya existe localmente, no es necesario importar")
                    }
                    
                    // Continuar con PIN setup
                    _setupState.value = SetupState.SeedPhraseConfirmed(phrase10)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Frase incorrecta"
                    android.util.Log.e("WalletSetupViewModel", "❌ Error en login: $errorMsg")
                    _setupState.value = SetupState.Error("Frase incorrecta. Verifica las palabras e intenta de nuevo.")
                }
            } catch (e: Exception) {
                android.util.Log.e("WalletSetupViewModel", "❌ Error restaurando wallet: ${e.message}", e)
                _setupState.value = SetupState.Error("Error restaurando wallet: ${e.message ?: "Error de conexión"}")
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
     * Completa el setup.
     * Cifra y guarda la clave privada después de que el usuario configure el PIN.
     */
    fun completeSetup() {
        viewModelScope.launch {
            try {
                if (pendingPin == null) {
                    _setupState.value = SetupState.Error("PIN no configurado")
                    return@launch
                }

                android.util.Log.d("WalletSetupViewModel", "🔄 Completando setup...")
                
                // Si hay una clave privada pendiente, cifrarla y guardarla ahora
                if (pendingPrivateKey != null) {
                    android.util.Log.d("WalletSetupViewModel", "🔄 Cifrando y guardando clave privada...")
                    try {
                        WalletManager.importPrivateKeyFromBackend(getApplication(), pendingPrivateKey!!)
                        android.util.Log.d("WalletSetupViewModel", "✅ Clave privada cifrada y guardada correctamente")
                    } catch (e: Exception) {
                        android.util.Log.e("WalletSetupViewModel", "❌ Error cifrando clave privada: ${e.message}", e)
                        _setupState.value = SetupState.Error("Error guardando wallet: ${e.message ?: e.javaClass.simpleName}")
                        return@launch
                    }
                } else {
                    android.util.Log.w("WalletSetupViewModel", "⚠️ No hay clave privada pendiente para guardar")
                }
                
                // Marcar seed phrase como mostrada (si aplica)
                if (generatedSeedPhrase != null) {
                    WalletManager.markSeedPhraseAsShown(getApplication())
                }

                // Limpiar datos sensibles de memoria
                generatedSeedPhrase = null
                pendingPin = null
                pendingPrivateKey = null

                android.util.Log.d("WalletSetupViewModel", "✅ Setup completado")
                _setupState.value = SetupState.Completed
            } catch (e: Exception) {
                android.util.Log.e("WalletSetupViewModel", "❌ Error completando setup: ${e.message}", e)
                _setupState.value = SetupState.Error("Error completando setup: ${e.message ?: e.javaClass.simpleName}")
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
        generatedSeedPhrase = null
        pendingPin = null
        pendingPrivateKey = null
        _setupState.value = SetupState.Initial
    }
}


