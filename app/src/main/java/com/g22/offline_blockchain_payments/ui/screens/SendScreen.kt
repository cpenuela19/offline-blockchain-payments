package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.*
import kotlinx.coroutines.delay

enum class SendStep {
    SCANNING,
    CONFIRMATION,
    RECEIPT
}

@Composable
fun SendScreen(
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(SendStep.SCANNING) }
    
    // Timer automático para simular escaneo de QR
    LaunchedEffect(currentStep) {
        if (currentStep == SendStep.SCANNING) {
            delay(5000) // Espera 5 segundos
            currentStep = SendStep.CONFIRMATION
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        when (currentStep) {
            SendStep.SCANNING -> ScanQRView(onCancel = onBack)
            SendStep.CONFIRMATION -> ConfirmationView(
                onConfirm = { currentStep = SendStep.RECEIPT },
                onCancel = onBack
            )
            SendStep.RECEIPT -> ReceiptView(
                onSave = { /* Guardar */ },
                onClose = onBack
            )
        }
    }
}

@Composable
fun ScanQRView(onCancel: () -> Unit) {
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
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pagar con QR",
                color = White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
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
                    .size(12.dp)
                    .background(CyanBlue, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline",
                color = LightSteelBlue,
                fontSize = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // QR Scanner Frame (marco con líneas punteadas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(horizontal = 24.dp)
                .border(
                    width = 4.dp,
                    color = CyanBlue,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(DarkNavy, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Esquinas del marco de escaneo
            Box(modifier = Modifier.fillMaxSize()) {
                // Esquina superior izquierda
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina superior derecha
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina inferior izquierda
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                // Esquina inferior derecha
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                )
                
                // Texto central
                Text(
                    text = "Apunta al QR del vendedor",
                    color = LightSteelBlue,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Botón Cancelar
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Cancelar",
                fontSize = 32.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ConfirmationView(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
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
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pagar con QR",
                color = White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
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
                    .size(12.dp)
                    .background(CyanBlue, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline",
                color = LightSteelBlue,
                fontSize = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.height(60.dp))
        
        // Información de pago
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Vas a pagar",
                        color = LightSteelBlue,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$12,000 COP",
                        color = White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Destino",
                        color = LightSteelBlue,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Marta Gomez",
                        color = White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Botón Confirmar
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Confirmar",
                fontSize = 38.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón Cancelar
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Cancelar",
                fontSize = 38.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun ReceiptView(
    onSave: () -> Unit,
    onClose: () -> Unit
) {
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
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Comprobante local",
                color = White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
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
                    .size(12.dp)
                    .background(CyanBlue, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline",
                color = LightSteelBlue,
                fontSize = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Check icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF00FFB3)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = DarkNavy,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Información del comprobante
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Monto",
                    color = LightSteelBlue,
                    fontSize = 18.sp
                )
                Text(
                    text = "$12,000 COP",
                    color = White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "De",
                            color = LightSteelBlue,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Cliente: Juan P.",
                            color = White,
                            fontSize = 20.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Para",
                            color = LightSteelBlue,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Vendedor: Marta Gomez",
                            color = White,
                            fontSize = 20.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Identificador",
                            color = LightSteelBlue,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "OF-9F3A-09321",
                            color = White,
                            fontSize = 20.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Hora",
                            color = LightSteelBlue,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "09:34 AM",
                            color = White,
                            fontSize = 20.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Estado",
                    color = LightSteelBlue,
                    fontSize = 16.sp
                )
                Text(
                    text = "Guardado offline (pendiente sincronizar)",
                    color = White,
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón Guardar
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Guardar",
                fontSize = 38.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón Cerrar
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Cerrar y volver al inicio",
                fontSize = 32.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mensaje de sincronización
        Text(
            text = "Cuando haya señal, se sincronizará automáticamente.",
            color = LightSteelBlue,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

