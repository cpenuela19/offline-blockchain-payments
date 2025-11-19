package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.theme.*

/**
 * Pantalla para mostrar la seed phrase (12 palabras) al usuario.
 * Solo se muestra una vez, nunca se almacena en el dispositivo.
 */
@Composable
fun SeedPhraseDisplayScreen(
    seedPhrase: List<String>,
    onConfirm: () -> Unit
) {
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
            Spacer(modifier = Modifier.height(32.dp))

            // Icono de advertencia
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                contentDescription = "Advertencia",
                tint = YellowWarning,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Título
            Text(
                text = "Guarda tu frase de recuperación",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Advertencia de seguridad
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
                        text = "Esta frase NO se almacena en el dispositivo.\n" +
                                "Si pierdes esta frase, perderás acceso a tu wallet.",
                        fontSize = 14.sp,
                        color = White,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ NUNCA compartas esta frase con nadie.\n" +
                                "⚠️ NUNCA la envíes al backend.\n" +
                                "⚠️ Guárdala en un lugar seguro.",
                        fontSize = 14.sp,
                        color = LightSteelBlue,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Grid de palabras (12 palabras en 3 columnas)
            // Usar Column/Row en lugar de LazyVerticalGrid para evitar conflicto con scroll
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                seedPhrase.chunked(3).forEachIndexed { rowIndex, rowWords ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowWords.forEachIndexed { colIndex, word ->
                            val wordIndex = rowIndex * 3 + colIndex
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                WordChip(
                                    wordNumber = wordIndex + 1,
                                    word = word
                                )
                            }
                        }
                        // Rellenar si la fila tiene menos de 3 palabras
                        repeat(3 - rowWords.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instrucciones
            Text(
                text = "Escribe estas 12 palabras en orden en un lugar seguro.",
                fontSize = 14.sp,
                color = LightSteelBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de confirmación
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Ya guardé la frase",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun WordChip(
    wordNumber: Int,
    word: String
) {
    Card(
        modifier = Modifier
            .height(56.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkNavyLight
        ),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            LightSteelBlue.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$wordNumber.",
                fontSize = 10.sp,
                color = LightSteelBlue,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = word,
                fontSize = 14.sp,
                color = White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

