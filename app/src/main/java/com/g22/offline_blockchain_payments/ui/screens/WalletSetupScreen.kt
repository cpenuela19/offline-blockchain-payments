package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletSetupViewModel

/**
 * Pantalla principal de setup de wallet.
 * Maneja el flujo completo: generación de wallet, mostrar seed phrase, configurar PIN.
 */
@Composable
fun WalletSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: WalletSetupViewModel
) {
    android.util.Log.d("WalletSetupScreen", "🟢 WalletSetupScreen Composable iniciado")
    val setupState by viewModel.setupState.collectAsState()
    android.util.Log.d("WalletSetupScreen", "📊 Estado actual: $setupState")
    var showSeedPhrase by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }

    // Observar cambios de estado
    LaunchedEffect(setupState) {
        when (setupState) {
            is WalletSetupViewModel.SetupState.WalletGenerated -> {
                showSeedPhrase = true
            }
            is WalletSetupViewModel.SetupState.SeedPhraseConfirmed -> {
                showSeedPhrase = false
                showPinSetup = true
            }
            is WalletSetupViewModel.SetupState.Completed -> {
                onSetupComplete()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        when {
            showSeedPhrase && setupState is WalletSetupViewModel.SetupState.WalletGenerated -> {
                SeedPhraseDisplayScreen(
                    seedPhrase = (setupState as WalletSetupViewModel.SetupState.WalletGenerated).seedPhrase,
                    onConfirm = {
                        viewModel.confirmSeedPhrase()
                    }
                )
            }
            showPinSetup -> {
                PinSetupScreen(
                    pinInput = pinInput,
                    confirmPinInput = confirmPinInput,
                    onPinInputChange = { pinInput = it },
                    onConfirmPinInputChange = { confirmPinInput = it },
                    onConfirm = { pin, confirmPin ->
                        if (viewModel.setPin(pin, confirmPin)) {
                            viewModel.completeSetup()
                        }
                    },
                    errorMessage = if (setupState is WalletSetupViewModel.SetupState.Error) {
                        (setupState as WalletSetupViewModel.SetupState.Error).message
                    } else null
                )
            }
            else -> {
                WelcomeScreen(
                    onContinue = {
                        android.util.Log.d("WalletSetupScreen", "🟢 onContinue llamado, llamando viewModel.generateWallet()")
                        viewModel.generateWallet()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono de wallet
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_dialog_info),
            contentDescription = "Wallet",
            tint = CyanBlue,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Título
        Text(
            text = "Bienvenido a AgroPuntos",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Descripción
        Text(
            text = "Para comenzar, necesitamos crear tu wallet seguro.",
            fontSize = 16.sp,
            color = LightSteelBlue,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Información
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkNavyLight
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Tu wallet incluirá:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoItem("🔐 Clave privada cifrada y segura")
                InfoItem("📝 Frase de recuperación (12 palabras)")
                InfoItem("🔒 Protección con PIN de 4 dígitos")
                InfoItem("👆 Autenticación biométrica opcional")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Botón de continuar
        Button(
            onClick = {
                android.util.Log.d("WalletSetupScreen", "🔵 Botón 'Crear Wallet' presionado")
                onContinue()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBlue
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Crear Wallet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
        }
    }
}

@Composable
private fun InfoItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = LightSteelBlue
        )
    }
}

@Composable
private fun PinSetupScreen(
    pinInput: String,
    confirmPinInput: String,
    onPinInputChange: (String) -> Unit,
    onConfirmPinInputChange: (String) -> Unit,
    onConfirm: (String, String) -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono de PIN
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_lock_idle_lock),
            contentDescription = "PIN",
            tint = CyanBlue,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Título
        Text(
            text = "Configurar PIN",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtítulo
        Text(
            text = "Crea un PIN de 4 dígitos para proteger tu wallet",
            fontSize = 16.sp,
            color = LightSteelBlue,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Input de PIN
        OutlinedTextField(
            value = pinInput,
            onValueChange = { newValue ->
                if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                    onPinInputChange(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            label = { Text("PIN", color = LightSteelBlue) },
            placeholder = { Text("0000", color = LightSteelBlue.copy(alpha = 0.5f)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
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
                unfocusedLabelColor = LightSteelBlue
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Input de confirmación de PIN
        OutlinedTextField(
            value = confirmPinInput,
            onValueChange = { newValue ->
                if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                    onConfirmPinInputChange(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            label = { Text("Confirmar PIN", color = LightSteelBlue) },
            placeholder = { Text("0000", color = LightSteelBlue.copy(alpha = 0.5f)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
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
                unfocusedLabelColor = LightSteelBlue
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Mensaje de error
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = YellowWarning,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de confirmar
        Button(
            onClick = { onConfirm(pinInput, confirmPinInput) },
            enabled = pinInput.length == 4 && confirmPinInput.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldGreen,
                disabledContainerColor = LightSteelBlue.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Confirmar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
        }
    }
}


