package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.components.*
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onSwapClick: () -> Unit = {}
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
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
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
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Avatar
            ProfileAvatar(size = 100)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dirección de wallet
            WalletAddressChip(address = "0xA80B...b6320F")
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Tarjeta de Balance
            BalanceCard(
                title = "Balance",
                amount = "$58,200 COP"
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Tarjeta de Tokens
            BalanceCard(
                title = "Tokens disponibles",
                amount = "58.200 TK"
            )
            
            Spacer(modifier = Modifier.height(50.dp))
            
            // Botones de acción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    iconRes = R.drawable.ic_send,
                    text = "Enviar",
                    onClick = onSendClick
                )
                ActionButton(
                    iconRes = R.drawable.ic_receive,
                    text = "Recibir",
                    onClick = onReceiveClick
                )
                ActionButton(
                    iconRes = R.drawable.ic_swap,
                    text = "Intercambiar",
                    onClick = onSwapClick
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer con estado offline
            Text(
                text = "Modo: Offline disponible • Saldo local listo • Sincroniza cuando haya señal",
                color = LightSteelBlue,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            )
        }
    }
}

