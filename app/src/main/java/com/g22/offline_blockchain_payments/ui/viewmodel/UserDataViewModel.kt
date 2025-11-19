package com.g22.offline_blockchain_payments.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g22.offline_blockchain_payments.data.api.ApiClient
import com.g22.offline_blockchain_payments.data.api.IdentityDebugRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla "Tus datos".
 * Permite al usuario ver su información de identidad (address, public_key, private_key)
 * ingresando su frase de 10 palabras.
 */
class UserDataViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UserDataState {
        object Initial : UserDataState()
        object Loading : UserDataState()
        data class Success(
            val address: String,
            val publicKey: String,
            val privateKey: String
        ) : UserDataState()
        data class Error(val message: String) : UserDataState()
    }

    private val _state = MutableStateFlow<UserDataState>(UserDataState.Initial)
    val state: StateFlow<UserDataState> = _state.asStateFlow()

    /**
     * Verifica la identidad del usuario usando su frase de 10 palabras
     * y obtiene sus datos (address, public_key, private_key).
     */
    fun verifyIdentity(phrase10: List<String>) {
        if (phrase10.size != 10 || phrase10.any { it.isBlank() }) {
            _state.value = UserDataState.Error("Debes ingresar las 10 palabras completas")
            return
        }

        _state.value = UserDataState.Loading

        viewModelScope.launch {
            try {
                val request = IdentityDebugRequest(phrase10 = phrase10)
                val response = ApiClient.apiService.identityDebug(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _state.value = UserDataState.Success(
                        address = body.address,
                        publicKey = body.public_key,
                        privateKey = body.private_key
                    )
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "Error desconocido"
                    _state.value = UserDataState.Error(
                        "Frase incorrecta. Verifica que hayas ingresado las palabras correctamente."
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("UserDataViewModel", "Error verificando identidad", e)
                _state.value = UserDataState.Error(
                    "Error de conexión: ${e.message ?: "No se pudo conectar al servidor"}"
                )
            }
        }
    }

    /**
     * Resetea el estado a Initial.
     */
    fun reset() {
        _state.value = UserDataState.Initial
    }
}

