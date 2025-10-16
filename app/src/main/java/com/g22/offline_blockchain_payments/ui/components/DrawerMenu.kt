package com.g22.offline_blockchain_payments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun DrawerMenu(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSwapClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onCurrencyClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(370.dp)
            .background(DarkNavy)
    ) {
        // Header cyan
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyanBlue)
                .padding(vertical = 32.dp, horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu),
                    contentDescription = "Menu",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Menu",
                    color = White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Avatar y dirección
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(size = 50)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "0xA80B...b6320F",
                color = White,
                fontSize = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Sección de monedas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkCard)
                .padding(vertical = 16.dp)
        ) {
            CurrencyRow(
                currencyCode = "JOD",
                amount = "604.250000",
                onClick = { onCurrencyClick("JOD") }
            )
            MenuDivider()
            CurrencyRow(
                currencyCode = "USD",
                amount = "929.750000",
                onClick = { onCurrencyClick("USD") }
            )
            MenuDivider()
            CurrencyRow(
                currencyCode = "ILS",
                amount = "4599.010000",
                onClick = { onCurrencyClick("ILS") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Opciones de menú principal
        Column {
            MenuItemRow(
                iconRes = R.drawable.ic_send,
                text = "Enviar",
                onClick = onSendClick
            )
            MenuDivider()
            
            MenuItemRow(
                iconRes = R.drawable.ic_receive,
                text = "Recibir",
                onClick = onReceiveClick
            )
            MenuDivider()
            
            MenuItemRow(
                iconRes = R.drawable.ic_swap,
                text = "Swap",
                onClick = onSwapClick
            )
            MenuDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            MenuItemRow(
                iconRes = R.drawable.ic_settings,
                text = "Ajustes",
                onClick = onSettingsClick
            )
            MenuDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            MenuItemRow(
                iconRes = R.drawable.ic_logout,
                text = "Cerrar sesion",
                onClick = onLogoutClick
            )
        }
    }
}

