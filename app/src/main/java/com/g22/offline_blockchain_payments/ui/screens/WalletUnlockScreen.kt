package com.g22.offline_blockchain_payments.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletUnlockViewModel
import java.util.concurrent.Executor

/**
 * Pantalla para desbloquear el wallet.
 * Permite desbloqueo con PIN (4 dígitos) o biometría (opcional).
 */
@Composable
fun WalletUnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: WalletUnlockViewModel = viewModel()
) {
    val unlockState by viewModel.unlockState.collectAsState()
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Observar cambios de estado
    LaunchedEffect(unlockState) {
        when (val state = unlockState) {
            is WalletUnlockViewModel.UnlockState.Unlocked -> {
                onUnlocked()
            }
            is WalletUnlockViewModel.UnlockState.Error -> {
                errorMessage = state.message
                pinInput = ""
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono de candado
            Icon(
                painter = painterResource(id = android.R.drawable.ic_lock_idle_lock),
                contentDescription = "Desbloquear wallet",
                tint = CyanBlue,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Título
            Text(
                text = "Desbloquear Wallet",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "Ingresa tu PIN de 4 dígitos",
                fontSize = 16.sp,
                color = LightSteelBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Input de PIN (4 dígitos)
            OutlinedTextField(
                value = pinInput,
                onValueChange = { newValue ->
                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                        pinInput = newValue
                        errorMessage = null
                        
                        // Auto-desbloquear cuando se ingresan 4 dígitos
                        if (newValue.length == 4 && unlockState !is WalletUnlockViewModel.UnlockState.Unlocking) {
                            viewModel.unlockWithPin(newValue)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                label = { Text("PIN", color = LightSteelBlue) },
                placeholder = { Text("0000", color = LightSteelBlue.copy(alpha = 0.5f)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = unlockState !is WalletUnlockViewModel.UnlockState.Unlocking,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    focusedBorderColor = CyanBlue,
                    unfocusedBorderColor = LightSteelBlue.copy(alpha = 0.3f),
                    focusedLabelColor = CyanBlue,
                    unfocusedLabelColor = LightSteelBlue,
                    disabledTextColor = LightSteelBlue.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Mensaje de error
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage ?: "",
                    color = YellowWarning,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de desbloqueo manual (por si no se auto-desbloquea)
            Button(
                onClick = {
                    if (pinInput.length == 4) {
                        viewModel.unlockWithPin(pinInput)
                    }
                },
                enabled = pinInput.length == 4 && unlockState !is WalletUnlockViewModel.UnlockState.Unlocking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanBlue,
                    disabledContainerColor = LightSteelBlue.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (unlockState is WalletUnlockViewModel.UnlockState.Unlocking) {
                        "Desbloqueando..."
                    } else {
                        "Desbloquear"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de biometría (si está disponible)
            if (context is FragmentActivity) {
                BiometricUnlockButton(
                    onBiometricSuccess = {
                        viewModel.unlockWithBiometric()
                    },
                    activity = context
                )
            }
        }
    }
}

@Composable
private fun BiometricUnlockButton(
    onBiometricSuccess: () -> Unit,
    activity: FragmentActivity
) {
    val executor = remember { ContextCompat.getMainExecutor(activity) }
    val biometricPrompt = remember {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Error de autenticación - usuario puede usar PIN en su lugar
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Autenticación falló - usuario puede intentar de nuevo
                }
            }
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Wallet")
            .setSubtitle("Usa tu huella digital o reconocimiento facial")
            .setNegativeButtonText("Usar PIN")
            .build()
    }

    OutlinedButton(
        onClick = {
            biometricPrompt.authenticate(promptInfo)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            LightSteelBlue.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            painter = painterResource(id = android.R.drawable.ic_dialog_info),
            contentDescription = "Biometría",
            tint = White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Usar biometría",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = White
        )
    }
}

