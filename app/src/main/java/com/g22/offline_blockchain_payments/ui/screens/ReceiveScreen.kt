package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.components.BalanceCard
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun ReceiveScreen(
    onBack: () -> Unit
) {
    var amountToReceive by remember { mutableStateOf("12000") }
    var showQR by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
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
                    text = "Cobrar con QR",
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
                        .background(CyanBlue, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline",
                    color = LightSteelBlue,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (!showQR) {
                // Primera pantalla: Ingresar monto
                ReceiveAmountInput(
                    amountToReceive = amountToReceive,
                    onAmountChange = { amountToReceive = it },
                    onGenerateQR = { showQR = true },
                    onCancel = onBack
                )
            } else {
                // Segunda pantalla: Mostrar QR
                ReceiveQRDisplay(
                    amountToReceive = amountToReceive,
                    onCancel = onBack
                )
            }
        }
    }
}

@Composable
fun ReceiveAmountInput(
    amountToReceive: String,
    onAmountChange: (String) -> Unit,
    onGenerateQR: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Saldo local
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Saldo local (sin sincronizar)",
                    color = LightSteelBlue,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$58,200 COP",
                    color = White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Monto a cobrar (editable)
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Monto a cobrar",
                    color = LightSteelBlue,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Input field for amount
                OutlinedTextField(
                    value = amountToReceive,
                    onValueChange = { newValue ->
                        // Solo permitir números
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            onAmountChange(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBlue,
                        unfocusedBorderColor = CardDarkBlue,
                        cursorColor = CyanBlue,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    ),
                    suffix = {
                        Text(
                            text = " COP",
                            fontSize = 48.sp,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Botón Generar QR
        Button(
            onClick = onGenerateQR,
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
                text = "Generar QR",
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
fun ReceiveQRDisplay(
    amountToReceive: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Monto a cobrar
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
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Monto a cobrar",
                    color = LightSteelBlue,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                val formattedAmount = try {
                    val amount = amountToReceive.toIntOrNull() ?: 0
                    String.format("$%,d COP", amount)
                } catch (e: Exception) {
                    "$$amountToReceive COP"
                }
                Text(
                    text = formattedAmount,
                    color = White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // QR Code display
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "QR generado (offline)",
                    color = LightSteelBlue,
                    fontSize = 24.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code placeholder (simulado)
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .border(4.dp, CyanBlue, RoundedCornerShape(8.dp))
                        .background(DarkNavy, RoundedCornerShape(8.dp))
                        .padding(24.dp)
                ) {
                    // Simulación de QR code con cuadros cyan
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(CyanBlue)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(CyanBlue)
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(CyanBlue)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(CyanBlue)
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(CyanBlue)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
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

