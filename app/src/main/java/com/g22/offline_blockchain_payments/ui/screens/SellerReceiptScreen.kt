package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModel
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModelFactory
import java.util.UUID

@Composable
fun SellerReceiptScreen(
    amount: Long,
    from: String = "Juan",
    to: String = "Marta",
    voucherId: String = UUID.randomUUID().toString().take(8).uppercase(),
    onSave: () -> Unit,
    onClose: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(context.applicationContext as Application)
    )
    var isSaved by remember { mutableStateOf(false) }
    
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
                    text = "Recibo guardado",
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
                        .background(EmeraldGreen, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Offline",
                    color = LightSteelBlue,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Check icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00FFB3)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = DarkNavy,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Información del comprobante
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Monto
                    Text(
                        text = "Monto",
                        color = LightSteelBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%,d", amount)} AP",
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // De y Para
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "De",
                                color = LightSteelBlue,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = from,
                                color = White,
                                fontSize = 16.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Para",
                                color = LightSteelBlue,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = to,
                                color = White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Identificador
                    Text(
                        text = "Identificador",
                        color = LightSteelBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = voucherId,
                        color = White,
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Estado
                    Text(
                        text = "Estado",
                        color = LightSteelBlue,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Guardado sin señal",
                        color = White,
                        fontSize = 15.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Botón Guardar
            Button(
                onClick = {
                    if (!isSaved) {
                        viewModel.createVoucher(
                            role = Role.SELLER,
                            amountAp = amount,
                            counterparty = from,
                            buyerAlias = from,
                            sellerAlias = to
                        )
                        isSaved = true
                        onSave()
                    }
                },
                enabled = !isSaved,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    disabledContainerColor = DarkCard
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isSaved) "Guardado" else "Guardar",
                    fontSize = 16.sp,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Botón Cerrar (deshabilitado hasta guardar)
            Button(
                onClick = onClose,
                enabled = isSaved,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCard,
                    disabledContainerColor = DarkCard.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cerrar y volver al inicio",
                    fontSize = 14.sp,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mensaje
            Text(
                text = "Cuando haya señal, se actualizará automáticamente.",
                color = LightSteelBlue,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

