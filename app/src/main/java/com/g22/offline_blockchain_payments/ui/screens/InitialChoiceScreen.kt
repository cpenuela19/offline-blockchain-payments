package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicator
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter
import com.g22.offline_blockchain_payments.ui.viewmodel.WalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InitialChoiceScreen(
    onSellClick: () -> Unit,
    onBuyClick: () -> Unit,
    availablePoints: Long = 58200,
    pendingPoints: Long = 20000,
    walletViewModel: WalletViewModel? = null
) {
    // Estado para controlar la animación de refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Refrescar balance cuando se muestra la pantalla
    LaunchedEffect(Unit) {
        walletViewModel?.refreshRealBalance()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Network status indicator en la parte superior
            NetworkStatusIndicator(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .align(Alignment.End)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Saldo de AgroPuntos con botón de refrescar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Mis AgroPuntos",
                            color = White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Disponibles",
                                    color = LightSteelBlue,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${NumberFormatter.formatAmount(availablePoints)} AP",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(24.dp))
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Pendientes",
                                    color = LightSteelBlue,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${NumberFormatter.formatAmount(pendingPoints)} AP",
                                    color = Color(0xFFFF9800),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Botón de refrescar en la esquina superior derecha
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                walletViewModel?.refreshRealBalance()
                                // Dar tiempo visual para la animación
                                delay(800)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar saldo",
                            tint = if (isRefreshing) Color(0xFF4CAF50) else LightSteelBlue,
                            modifier = Modifier
                                .size(24.dp)
                                .then(
                                    if (isRefreshing) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "refresh")
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "rotation"
                                        )
                                        Modifier.rotate(rotation)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Título
            Text(
                text = "¿Qué deseas hacer?",
                color = White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Botón QUIERO VENDER (Verde)
            Button(
                onClick = onSellClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // Verde
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "QUIERO VENDER",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón QUIERO COMPRAR (Naranja)
            Button(
                onClick = onBuyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800) // Naranja
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "QUIERO COMPRAR",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Texto explicativo
            Text(
                text = "Vender: Recibir AgroPuntos de otro celular\nComprar: Dar AgroPuntos a otro celular",
                color = LightSteelBlue,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

