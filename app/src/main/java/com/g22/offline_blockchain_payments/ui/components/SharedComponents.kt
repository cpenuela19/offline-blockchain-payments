package com.g22.offline_blockchain_payments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun ProfileAvatar(
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(ProfileBlue),
        contentAlignment = Alignment.Center
    ) {
        // Avatar vacío por ahora, puede añadirse imagen o inicial
    }
}

@Composable
fun WalletAddressChip(
    address: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = CardDarkBlue,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = address,
            color = LightSteelBlue,
            fontSize = 17.sp
        )
    }
}

@Composable
fun BalanceCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = LightSteelBlue,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActionButton(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = text,
                tint = CyanBlue,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = text,
            color = White,
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun MenuItemRow(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = text,
            tint = CyanBlue,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = White,
            fontSize = 26.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = com.g22.offline_blockchain_payments.R.drawable.ic_chevron),
            contentDescription = "Navigate",
            tint = White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun CurrencyRow(
    currencyCode: String,
    amount: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currencyCode,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                color = LightSteelBlue,
                fontSize = 22.sp
            )
        }
        Icon(
            painter = painterResource(id = com.g22.offline_blockchain_payments.R.drawable.ic_chevron),
            contentDescription = "Expand",
            tint = White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        color = CardDarkBlue,
        thickness = 1.dp
    )
}

