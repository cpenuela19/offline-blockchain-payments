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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.viewmodel.UserDataViewModel
import android.app.Application

/**
 * Pantalla "Tus datos" - Permite al usuario ver su información de identidad
 * ingresando su frase de 10 palabras.
 * Principalmente para desarrolladores y pruebas.
 */
@Composable
fun UserDataScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: UserDataViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val application = context.applicationContext as Application
                return UserDataViewModel(application) as T
            }
        }
    )
    val state by viewModel.state.collectAsState()
    val wordInputs = remember { mutableStateListOf(*Array(10) { "" }) }
    var showPrivateKey by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Botón de Atrás
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LightSteelBlue
                    )
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_revert),
                        contentDescription = "Atrás",
                        tint = LightSteelBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Atrás",
                        fontSize = 16.sp,
                        color = LightSteelBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Icono
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_dialog_info),
                contentDescription = "Tus datos",
                tint = CyanBlue,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Título
            Text(
                text = "Tus datos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "Ingresa tus 10 palabras para ver tu información",
                fontSize = 16.sp,
                color = LightSteelBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Mostrar datos si el estado es Success
            when (val currentState = state) {
                is UserDataViewModel.UserDataState.Success -> {
                    // Mostrar información del usuario
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkNavyLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Dirección
                            Column {
                                Text(
                                    text = "Dirección",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentState.address,
                                    fontSize = 12.sp,
                                    color = White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }

                            Divider(color = LightSteelBlue.copy(alpha = 0.3f))

                            // Clave pública
                            Column {
                                Text(
                                    text = "Clave pública",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentState.publicKey,
                                    fontSize = 12.sp,
                                    color = White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }

                            Divider(color = LightSteelBlue.copy(alpha = 0.3f))

                            // Clave privada con toggle
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Clave privada",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanBlue
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (showPrivateKey) "Ocultar" else "Mostrar",
                                            fontSize = 12.sp,
                                            color = LightSteelBlue
                                        )
                                        Switch(
                                            checked = showPrivateKey,
                                            onCheckedChange = { showPrivateKey = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = CyanBlue,
                                                checkedTrackColor = CyanBlue.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (showPrivateKey) currentState.privateKey else "••••••••••••••••",
                                    fontSize = 12.sp,
                                    color = White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botón para volver a ingresar frase
                    Button(
                        onClick = {
                            viewModel.reset()
                            wordInputs.fill("")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightSteelBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Ingresar otra frase",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }

                is UserDataViewModel.UserDataState.Loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(
                        color = CyanBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Verificando...",
                        fontSize = 16.sp,
                        color = LightSteelBlue
                    )
                }

                is UserDataViewModel.UserDataState.Error -> {
                    // Mostrar error
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFFF6B6B).copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "❌ ${currentState.message}",
                            fontSize = 14.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is UserDataViewModel.UserDataState.Initial -> {
                    // Campos de entrada para las 10 palabras
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        wordInputs.forEachIndexed { index, word ->
                            OutlinedTextField(
                                value = word,
                                onValueChange = { newValue ->
                                    val normalized = newValue.lowercase().trim()
                                    wordInputs[index] = normalized
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Palabra ${index + 1}", color = LightSteelBlue) },
                                placeholder = { Text("palabra${index + 1}", color = LightSteelBlue.copy(alpha = 0.5f)) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
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
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Botón de confirmar
                    Button(
                        onClick = {
                            val phrase10 = wordInputs.toList()
                            viewModel.verifyIdentity(phrase10)
                        },
                        enabled = wordInputs.all { it.isNotBlank() } && state !is UserDataViewModel.UserDataState.Loading,
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
                            text = "Verificar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

