package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun HomeMinimal(
    currentRole: Role,
    onRoleChange: (Role) -> Unit,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        // Botón de menú fijo en la esquina superior izquierda
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_menu),
                contentDescription = "Menú",
                tint = White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "¿Qué quieres hacer hoy?",
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp)
            )
            
            // Botón Comprar (azul)
            Button(
                onClick = onBuyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Comprar",
                    fontSize = 18.sp,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botón Vender (verde esmeralda)
            Button(
                onClick = onSellClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Vender",
                    fontSize = 18.sp,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Toggle de rol
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentRole == Role.BUYER) "Modo comprador" else "Modo vendedor",
                    color = LightSteelBlue,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                Switch(
                    checked = currentRole == Role.SELLER,
                    onCheckedChange = { 
                        onRoleChange(if (it) Role.SELLER else Role.BUYER)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = EmeraldGreen,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = CyanBlue
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Link secundario: Ver movimientos
            Text(
                text = "Ver movimientos",
                color = CyanBlue,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onHistoryClick)
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

