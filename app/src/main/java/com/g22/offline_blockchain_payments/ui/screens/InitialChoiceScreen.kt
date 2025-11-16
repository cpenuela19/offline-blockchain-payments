package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicator
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter

@Composable
fun InitialChoiceScreen(
    onSellClick: () -> Unit,
    onBuyClick: () -> Unit,
    availablePoints: Long = 58200,
    pendingPoints: Long = 20000
) {
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
            
            // Saldo de AgroPuntos
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
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

