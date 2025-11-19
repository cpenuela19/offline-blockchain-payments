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
 * Pantalla para mostrar la seed phrase (6 palabras en español) al usuario.
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

            // ADVERTENCIA CRÍTICA: ÚNICA FORMA DE RECUPERACIÓN
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 3.dp,
                        color = Color(0xFFFF6B6B), // Rojo llamativo
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x33FF6B6B).copy(alpha = 0.2f) // Fondo rojo translúcido
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icono de alerta grande
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                        contentDescription = "Alerta crítica",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Título de advertencia crítica
                    Text(
                        text = "⚠️ ADVERTENCIA CRÍTICA ⚠️",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Mensaje principal muy claro
                    Text(
                        text = "ESTAS 10 PALABRAS EN ESTE ORDEN EXACTO SON LA ÚNICA MANERA DE RECUPERAR TU CUENTA SI PIERDES TU PIN DE 4 DÍGITOS.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Sin estas palabras, NO podrás recuperar tu wallet si olvidas tu PIN.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFE66D), // Amarillo claro
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Advertencia de seguridad adicional
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

            // Grid de palabras (10 palabras en 2 columnas)
            // Usar Column/Row en lugar de LazyVerticalGrid para evitar conflicto con scroll
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                seedPhrase.chunked(2).forEachIndexed { rowIndex, rowWords ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowWords.forEachIndexed { colIndex, word ->
                            val wordIndex = rowIndex * 2 + colIndex
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                WordChip(
                                    wordNumber = wordIndex + 1,
                                    word = word
                                )
                            }
                        }
                        // Rellenar si la fila tiene menos de 2 palabras
                        repeat(2 - rowWords.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instrucciones con énfasis en el orden
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CyanBlue.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    CyanBlue.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📝 INSTRUCCIONES",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanBlue,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Escribe estas 10 palabras EXACTAMENTE EN ESTE ORDEN en un lugar seguro.",
                        fontSize = 15.sp,
                        color = White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El orden es CRÍTICO. Si cambias el orden, no podrás recuperar tu cuenta.",
                        fontSize = 13.sp,
                        color = YellowWarning,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

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
            .height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkNavyLight
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            CyanBlue.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$wordNumber.",
                fontSize = 12.sp,
                color = CyanBlue,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = word,
                fontSize = 18.sp,
                color = White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

