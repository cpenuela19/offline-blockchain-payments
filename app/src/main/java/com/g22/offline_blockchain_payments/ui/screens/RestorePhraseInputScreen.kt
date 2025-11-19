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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.*

/**
 * Pantalla para ingresar las 10 palabras de recuperación.
 * El usuario escribe sus 10 palabras para restaurar su wallet.
 */
@Composable
fun RestorePhraseInputScreen(
    onConfirm: (List<String>) -> Unit,
    onBack: () -> Unit,
    errorMessage: String?
) {
    val wordInputs = remember { mutableStateListOf(*Array(10) { "" }) }

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
            // Botón de Atrás en la parte superior
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
                contentDescription = "Restaurar",
                tint = CyanBlue,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Título
            Text(
                text = "Restaurar Wallet",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "Ingresa tus 10 palabras en orden",
                fontSize = 16.sp,
                color = LightSteelBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Advertencia
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
                        text = "⚠️ IMPORTANTE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = YellowWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ingresa las palabras EXACTAMENTE como las guardaste, en el orden correcto (1-10).",
                        fontSize = 14.sp,
                        color = White,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Campos de entrada para las 10 palabras
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                wordInputs.forEachIndexed { index, word ->
                    OutlinedTextField(
                        value = word,
                        onValueChange = { newValue ->
                            // Normalizar: minúsculas, sin espacios extra
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
                onClick = {
                    val phrase10 = wordInputs.toList()
                    if (phrase10.all { it.isNotBlank() }) {
                        onConfirm(phrase10)
                    }
                },
                enabled = wordInputs.all { it.isNotBlank() },
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

