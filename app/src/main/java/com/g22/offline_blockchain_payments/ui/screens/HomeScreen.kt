package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.g22.offline_blockchain_payments.ui.components.*
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onSwapClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    availablePoints: Long = 1000,  // Default para Preview (1000 AP inicial)
    pendingPoints: Long = 0,        // Default para Preview (sin pendientes)
    isSyncing: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header con título y botón de menú
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu),
                        contentDescription = "Menu",
                        tint = White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Text(
                    text = "Mi billetera",
                    color = White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Normal
                )
                
                // Espaciador para centrar el título
                Spacer(modifier = Modifier.width(44.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Avatar
            ProfileAvatar(size = 80)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dirección de wallet
            WalletAddressChip(address = "0xA80B...b6320F")
            
            // Network status indicator
            NetworkStatusIndicator(
                modifier = Modifier.padding(top = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Tarjeta de AgroPuntos disponibles
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AgroPuntos disponibles",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isSyncing) {
                        // Mostrar indicador de sincronización
                        Text(
                            text = "Sincronizando...",
                            color = Color(0xFFFF9800), // Naranja
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Mostrar balance normal
                        Text(
                            text = "${NumberFormatter.formatAmount(availablePoints)} AP",
                            color = Color(0xFF4CAF50),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tarjeta de AgroPuntos pendientes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AgroPuntos pendientes",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${NumberFormatter.formatAmount(pendingPoints)} AP",
                        color = Color(0xFFFF9800),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Se subirán cuando tengas conexión",
                        color = LightSteelBlue,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Botón Ver histórico
            Button(
                onClick = onHistoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanBlue
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Ver historial",
                    fontSize = 24.sp,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer con estado offline
            Text(
                text = "Sin conexión: Los pagos se guardarán cuando tengas señal",
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            )
        }
    }
}

