package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.api.ApiClient
import com.g22.offline_blockchain_payments.data.api.RegisterWalletRequest
import com.g22.offline_blockchain_payments.data.api.LoginWalletRequest
import com.g22.offline_blockchain_payments.data.wallet.SessionManager
import com.g22.offline_blockchain_payments.data.wallet.WalletManager
import com.g22.offline_blockchain_payments.data.wallet.SeedPhraseGenerator
import com.g22.offline_blockchain_payments.data.crypto.KeyDerivation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el flujo de setup de wallet.
 * 
 * MODELO TRUE SELF-CUSTODY:
 * - La app genera las 10 palabras localmente (SecureRandom)
 * - La app deriva la clave privada localmente (PBKDF2)
 * - El backend NUNCA conoce las palabras ni la clave privada
 * - Solo se envían datos públicos al backend (address, public_key)
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
     * Crea un nuevo wallet LOCALMENTE (True Self-Custody).
     * 
     * Proceso:
     * 1. Genera 10 palabras LOCALMENTE usando SecureRandom
     * 2. Deriva clave privada LOCALMENTE usando PBKDF2
     * 3. Calcula dirección y clave pública
     * 4. Registra en backend solo datos públicos (address, public_key)
     * 5. El backend NUNCA conoce las palabras ni la clave privada
     */
    fun createWallet() {
        android.util.Log.d("WalletSetupViewModel", "🟢 createWallet() llamado - Generación LOCAL")
        viewModelScope.launch {
            try {
                // 1. Generar 10 palabras LOCALMENTE
                android.util.Log.d("WalletSetupViewModel", "🔄 Generando 10 palabras localmente...")
                val phrase10 = SeedPhraseGenerator.generatePhrase10()
                android.util.Log.d("WalletSetupViewModel", "✅ 10 palabras generadas localmente")
                
                // 2. Derivar clave privada LOCALMENTE
                android.util.Log.d("WalletSetupViewModel", "🔄 Derivando clave privada localmente...")
                val privateKey = KeyDerivation.derivePrivateKeyFromPhrase(phrase10)
                android.util.Log.d("WalletSetupViewModel", "✅ Clave privada derivada localmente")
                
                // 3. Calcular dirección y clave pública
                android.util.Log.d("WalletSetupViewModel", "🔄 Calculando dirección y clave pública...")
                val address = KeyDerivation.getAddressFromPrivateKey(privateKey)
                val publicKey = KeyDerivation.getPublicKeyFromPrivateKey(privateKey)
                android.util.Log.d("WalletSetupViewModel", "✅ Dirección: $address")
                
                // 4. Registrar en backend (SOLO datos públicos)
                android.util.Log.d("WalletSetupViewModel", "🔄 Registrando wallet en backend (solo datos públicos)...")
                val request = RegisterWalletRequest(
                    address = address,
                    public_key = publicKey
                )
                val response = ApiClient.apiService.registerWallet(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    android.util.Log.d("WalletSetupViewModel", "✅ Wallet registrado en backend: address=${body.address}")
                    
                    // 5. Guardar sesión
                    SessionManager.saveSession(
                        getApplication(),
                        body.address,
                        publicKey,
                        body.session_token
                    )
                    
                    // 6. Guardar clave privada temporalmente (se cifrará después del PIN)
                    pendingPrivateKey = privateKey
                    generatedSeedPhrase = phrase10
                    
                    android.util.Log.d("WalletSetupViewModel", "✅ Setup local completado - Palabras guardadas temporalmente")
                    
                    // 7. Transición directa a WalletGenerated (sin approve)
                    _setupState.value = SetupState.WalletGenerated(phrase10)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                    android.util.Log.e("WalletSetupViewModel", "❌ Error registrando wallet en backend: $errorMsg")
                    _setupState.value = SetupState.Error("Error registrando wallet: $errorMsg")
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
     * 
     * Proceso:
     * 1. Deriva clave privada LOCALMENTE desde las 10 palabras
     * 2. Calcula la dirección desde la clave privada
     * 3. Verifica con el backend si esa dirección existe
     * 4. Obtiene un nuevo session token
     * 5. La clave privada NUNCA viaja por la red
     */
    fun restoreWallet(phrase10: List<String>) {
        android.util.Log.d("WalletSetupViewModel", "🟢 restoreWallet() llamado con ${phrase10.size} palabras")
        viewModelScope.launch {
            try {
                if (phrase10.size != 10) {
                    _setupState.value = SetupState.Error("Debes ingresar exactamente 10 palabras")
                    return@launch
                }
                
                // 1. Derivar clave privada LOCALMENTE
                android.util.Log.d("WalletSetupViewModel", "🔄 Derivando clave privada localmente desde las palabras...")
                val privateKey = KeyDerivation.derivePrivateKeyFromPhrase(phrase10)
                android.util.Log.d("WalletSetupViewModel", "✅ Clave privada derivada localmente")
                
                // 2. Calcular dirección
                android.util.Log.d("WalletSetupViewModel", "🔄 Calculando dirección...")
                val address = KeyDerivation.getAddressFromPrivateKey(privateKey)
                android.util.Log.d("WalletSetupViewModel", "✅ Dirección calculada: $address")
                
                // 3. Verificar con backend si existe
                android.util.Log.d("WalletSetupViewModel", "🔄 Verificando si wallet existe en backend...")
                val infoResponse = ApiClient.apiService.getWalletInfo(address)
                
                if (infoResponse.isSuccessful && infoResponse.body() != null) {
                    val walletInfo = infoResponse.body()!!
                    android.util.Log.d("WalletSetupViewModel", "✅ Wallet encontrado en backend")
                    
                    // 4. Generar nuevo session token
                    android.util.Log.d("WalletSetupViewModel", "🔄 Generando nuevo session token...")
                    val loginRequest = LoginWalletRequest(address = address)
                    val loginResponse = ApiClient.apiService.loginWallet(loginRequest)
                    
                    if (loginResponse.isSuccessful && loginResponse.body() != null) {
                        val loginBody = loginResponse.body()!!
                        android.util.Log.d("WalletSetupViewModel", "✅ Session token obtenido")
                        
                        // 5. Guardar sesión
                        SessionManager.saveSession(
                            getApplication(),
                            address,
                            walletInfo.public_key,
                            loginBody.session_token
                        )
                        
                        // 6. Guardar clave temporalmente (se cifrará después del PIN)
                        pendingPrivateKey = privateKey
                        
                        android.util.Log.d("WalletSetupViewModel", "✅ Wallet restaurado correctamente")
                        _setupState.value = SetupState.SeedPhraseConfirmed(phrase10)
                    } else {
                        val errorMsg = loginResponse.errorBody()?.string() ?: "Error obteniendo session token"
                        android.util.Log.e("WalletSetupViewModel", "❌ Error obteniendo session token: $errorMsg")
                        _setupState.value = SetupState.Error("Error obteniendo session token: $errorMsg")
                    }
                } else {
                    android.util.Log.e("WalletSetupViewModel", "❌ Wallet no encontrado")
                    _setupState.value = SetupState.Error("Wallet no encontrado. Verifica las palabras e intenta de nuevo.")
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


