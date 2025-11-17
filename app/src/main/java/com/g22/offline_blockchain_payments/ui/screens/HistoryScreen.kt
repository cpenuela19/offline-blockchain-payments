package com.g22.offline_blockchain_payments.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.data.database.VoucherEntity
import com.g22.offline_blockchain_payments.ui.data.VoucherStatus
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModel
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(context.applicationContext as Application)
    )
    
    val vouchers by viewModel.allVouchers.collectAsState(initial = emptyList())
    
    // Agrupar vouchers por día
    val groupedVouchers = vouchers.groupBy { voucher ->
        val date = Date(voucher.createdAt * 1000)
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val voucherCal = Calendar.getInstance().apply { time = date }
        
        when {
            isSameDay(voucherCal, today) -> "Hoy"
            isSameDay(voucherCal, yesterday) -> "Ayer"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        }
    }
    
    val sortedKeys = groupedVouchers.keys.sortedByDescending { key ->
        when (key) {
            "Hoy" -> 0
            "Ayer" -> 1
            else -> 2
        }
    }
    
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis ventas y pagos",
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            // Offline indicator
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CyanBlue, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Offline",
                    color = LightSteelBlue,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (vouchers.isEmpty()) {
                Text(
                    text = "No hay operaciones registradas",
                    color = LightSteelBlue,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                // Mostrar secciones agrupadas
                sortedKeys.forEach { key ->
                    val dayVouchers = groupedVouchers[key]!!
                    val total = dayVouchers.sumOf { it.amountAp }
                    
                    VoucherSection(
                        title = key,
                        vouchers = dayVouchers,
                        total = total,
                        context = context
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón Volver atrás
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Volver atrás",
                    fontSize = 16.sp,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun VoucherSection(
    title: String,
    vouchers: List<VoucherEntity>,
    total: Long,
    context: Context
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
        
        // Lista de vouchers
        vouchers.forEach { voucher ->
            VoucherItem(voucher, context)
        }
        
        // Total del día
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Total del día",
                    color = LightSteelBlue,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%,d", total)} AP",
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VoucherItem(voucher: VoucherEntity, context: Context) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(voucher.createdAt * 1000))
    
    val statusText = when (voucher.status) {
        VoucherStatus.GUARDADO_SIN_SENAL -> "Guardado sin señal"
        VoucherStatus.ENVIANDO -> "Guardando…"
        VoucherStatus.RECEIVED -> "Recibido, esperando confirmación"
        VoucherStatus.SUBIDO_OK -> "Guardado correctamente"
        VoucherStatus.ERROR -> voucher.lastError ?: "Error"
    }
    
    val statusColor = when (voucher.status) {
        VoucherStatus.SUBIDO_OK -> Color(0xFF00FFB3)
        VoucherStatus.ENVIANDO -> CyanBlue
        VoucherStatus.RECEIVED -> CyanBlue
        else -> Color.Transparent
    }
    
    val textColor = when (voucher.status) {
        VoucherStatus.SUBIDO_OK -> DarkNavy
        else -> LightSteelBlue
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Información del voucher
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = time,
                        color = LightSteelBlue,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Operación #${voucher.id.take(8)}",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Estado
                Box(
                    modifier = Modifier.weight(1.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusText,
                            color = textColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Valor
                Text(
                    text = "${String.format("%,d", voucher.amountAp)} AP",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f)
                )
            }
            
            // Mostrar txHash si existe
            if (voucher.txHash != null && voucher.status == VoucherStatus.SUBIDO_OK) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("txHash", voucher.txHash)
                            clipboard.setPrimaryClip(clip)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hash: ${voucher.txHash.take(8)}...${voucher.txHash.takeLast(6)}",
                        color = CyanBlue,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Tocar para copiar",
                        color = LightSteelBlue,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

