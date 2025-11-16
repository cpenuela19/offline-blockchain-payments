package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g22.offline_blockchain_payments.R
import com.g22.offline_blockchain_payments.ui.components.NetworkStatusIndicatorSmall
import com.g22.offline_blockchain_payments.ui.data.Role
import com.g22.offline_blockchain_payments.ui.theme.*
import com.g22.offline_blockchain_payments.ui.util.NumberFormatter
import com.g22.offline_blockchain_payments.ui.util.PdfGenerator
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModel
import com.g22.offline_blockchain_payments.ui.viewmodel.VoucherViewModelFactory
import java.util.UUID

@Composable
fun BuyerReceiptScreen(
    amount: Long = 12000L,
    from: String = "Juan",
    to: String = "Marta",
    transactionId: String = UUID.randomUUID().toString(),
    concept: String? = null,
    voucherId: String = transactionId.take(8).uppercase(),
    onSave: () -> Unit,
    onClose: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(context.applicationContext as Application)
    )
    var isSaved by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Guardar automáticamente al cargar la pantalla
    LaunchedEffect(Unit) {
        if (!isSaved) {
            // Guardar voucher en base de datos local
            viewModel.createVoucher(
                role = Role.BUYER,
                amountAp = amount,
                counterparty = to,
                buyerAlias = from,
                sellerAlias = to
            )
            
            // Generar y guardar PDF automáticamente
            val pdfResult = PdfGenerator.generateReceiptPdf(
                context = context,
                transactionId = transactionId,
                amount = amount,
                fromName = from,
                fromId = "1.234.567.890",
                toName = to,
                toId = "9.876.543.210",
                timestamp = System.currentTimeMillis() / 1000,
                isReceiver = false,
                concept = concept
            )
            
            if (pdfResult != null) {
                pdfUri = pdfResult.uri
                showPdfDialog = true
            }
            
            isSaved = true
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
                    text = "Recibo guardado",
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            // Network status indicator
            NetworkStatusIndicatorSmall(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End)
            )
            
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
            
            Spacer(modifier = Modifier.height(20.dp))
            
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
                    // AgroPuntos
                    Text(
                        text = "AgroPuntos entregados",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${NumberFormatter.formatAmount(amount)} AP",
                        color = BuyerPrimary,
                        fontSize = 32.sp,
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
                                text = "Yo soy",
                                color = White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = from,
                                color = White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "C.C. 1.234.567.890",
                                color = LightSteelBlue,
                                fontSize = 12.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Di a",
                                color = White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = to,
                                color = White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "C.C. 9.876.543.210",
                                color = LightSteelBlue,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    // Mostrar concepto solo si existe
                    if (!concept.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Concepto",
                            color = LightSteelBlue,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = concept,
                            color = White,
                            fontSize = 16.sp
                        )
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
            
            // Mensaje importante
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardDarkBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Importante",
                        color = BuyerPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Los AgroPuntos se te descontaron de tu saldo. Solo puedes usar el resto de tus puntos disponibles.",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        }
        
        // Diálogo para abrir PDF
        if (showPdfDialog && pdfUri != null) {
            AlertDialog(
                onDismissRequest = { showPdfDialog = false },
                title = {
                    Text(
                        text = "Comprobante guardado",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text("El comprobante se ha guardado en Documentos/AgroPuntos. ¿Deseas abrirlo ahora?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pdfUri?.let { PdfGenerator.openPdf(context, it) }
                            showPdfDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BuyerPrimary
                        )
                    ) {
                        Text("Ver comprobante")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPdfDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

