package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.*

data class Transaction(
    val id: String,
    val time: String,
    val amount: String,
    val status: TransactionStatus
)

enum class TransactionStatus {
    SYNCHRONIZED,
    PENDING
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    val todayTransactions = listOf(
        Transaction("09321", "09:34 AM", "$12,000", TransactionStatus.SYNCHRONIZED),
        Transaction("09322", "09:58 AM", "$8,000", TransactionStatus.PENDING),
        Transaction("09323", "10:12 AM", "$5,500", TransactionStatus.PENDING)
    )
    
    val yesterdayTransactions = listOf(
        Transaction("09318", "09:58 AM", "$8,000", TransactionStatus.PENDING),
        Transaction("09319", "09:34 AM", "$12,000", TransactionStatus.SYNCHRONIZED),
        Transaction("09320", "10:12 AM", "$5,500", TransactionStatus.PENDING)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis ventas (offline)",
                    color = White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
            
            // Offline indicator
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(CyanBlue, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Offline",
                    color = LightSteelBlue,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sección "Hoy"
            TransactionSection(
                title = "Hoy",
                transactions = todayTransactions,
                total = "$25,500 COP"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sección "Ayer"
            TransactionSection(
                title = "Ayer",
                transactions = yesterdayTransactions,
                total = "$25,500 COP"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón Ver más
            Button(
                onClick = { /* Cargar más transacciones */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanBlue
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Ver más",
                    fontSize = 28.sp,
                    color = White,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Botón Volver atrás
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Volver atrás",
                    fontSize = 28.sp,
                    color = White,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TransactionSection(
    title: String,
    transactions: List<Transaction>,
    total: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header de la sección
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = LightSteelBlue,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Estado",
                    color = LightSteelBlue,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "Valor",
                    color = LightSteelBlue,
                    fontSize = 18.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
        
        // Lista de transacciones
        transactions.forEach { transaction ->
            TransactionItem(transaction)
        }
        
        // Total del día
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Total del día",
                    color = LightSteelBlue,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = total,
                    color = White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Información de la transacción
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.time,
                    color = LightSteelBlue,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Venta #${transaction.id}",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Estado
            Box(
                modifier = Modifier
                    .weight(1.2f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (transaction.status == TransactionStatus.SYNCHRONIZED) 
                                Color(0xFF00FFB3) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (transaction.status == TransactionStatus.SYNCHRONIZED) 
                            "Sincronizada" else "Pendiente",
                        color = if (transaction.status == TransactionStatus.SYNCHRONIZED) 
                            DarkNavy else LightSteelBlue,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Valor
            Text(
                text = transaction.amount,
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}

