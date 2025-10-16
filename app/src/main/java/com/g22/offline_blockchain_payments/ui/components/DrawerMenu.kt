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
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(370.dp)
            .background(DarkNavy)
    ) {
        // Header cyan con Menu, Avatar y dirección
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyanBlue)
                .padding(vertical = 24.dp, horizontal = 24.dp)
        ) {
            // Icono de menú y texto
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Avatar y dirección en el mismo header
            Row(
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
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Opciones de menú
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
                text = "Intercambiar",
                onClick = onSwapClick
            )
            MenuDivider()
            
            MenuItemRow(
                iconRes = R.drawable.ic_history,
                text = "Histórico de transacciones",
                onClick = onHistoryClick
            )
            MenuDivider()
            
            MenuItemRow(
                iconRes = R.drawable.ic_settings,
                text = "Ajustes",
                onClick = onSettingsClick
            )
            MenuDivider()
            
            MenuItemRow(
                iconRes = R.drawable.ic_logout,
                text = "Cerrar sesion",
                onClick = onLogoutClick
            )
        }
    }
}

