package com.g22.offline_blockchain_payments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.CyanBlue
import com.g22.offline_blockchain_payments.ui.theme.EmeraldGreen
import com.g22.offline_blockchain_payments.ui.theme.LightSteelBlue
import com.g22.offline_blockchain_payments.ui.util.rememberNetworkStatus

/**
 * Indicador de estado de red (Online/Offline)
 * Se actualiza automáticamente según el estado real de la conexión a internet
 */
@Composable
fun NetworkStatusIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 12.dp,
    fontSize: TextUnit = 24.sp
) {
    val isOnline = rememberNetworkStatus()
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(
                    color = if (isOnline) EmeraldGreen else CyanBlue,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isOnline) "Online" else "Offline",
            color = LightSteelBlue,
            fontSize = fontSize
        )
    }
}

/**
 * Versión pequeña del indicador para recibos y pantallas compactas
 */
@Composable
fun NetworkStatusIndicatorSmall(
    modifier: Modifier = Modifier
) {
    NetworkStatusIndicator(
        modifier = modifier,
        dotSize = 8.dp,
        fontSize = 12.sp
    )
}

