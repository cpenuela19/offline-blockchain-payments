package com.g22.offline_blockchain_payments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g22.offline_blockchain_payments.ui.theme.*

@Composable
fun SwapScreen(onBack: () -> Unit, onSwapComplete: () -> Unit = {}) {
    var fromAmount by remember { mutableStateOf("10") }
    var fromCurrency by remember { mutableStateOf("COP") }
    var showFromDropdown by remember { mutableStateOf(false) }
    
    // El toCurrency siempre es lo opuesto al fromCurrency
    val toCurrency = if (fromCurrency == "COP") "TK" else "COP"
    
    // El toAmount es igual al fromAmount (conversión 1:1)
    val toAmount = fromAmount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = CyanBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Intercambiar",
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal
            )
            // Spacer para balancear el header
            Box(modifier = Modifier.size(48.dp))
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
                    .size(10.dp)
                    .background(CyanBlue, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline",
                color = LightSteelBlue,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Card: Intercambiar desde
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
                    .padding(20.dp)
            ) {
                Text(
                    text = "Intercambiar desde",
                    color = LightSteelBlue,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Fila con dropdown y input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dropdown de moneda
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { showFromDropdown = !showFromDropdown }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fromCurrency,
                                color = White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Desplegar",
                                tint = White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showFromDropdown,
                            onDismissRequest = { showFromDropdown = false },
                            modifier = Modifier.background(CardDarkBlue)
                        ) {
                            DropdownMenuItem(
                                text = { Text("COP", color = White) },
                                onClick = {
                                    fromCurrency = "COP"
                                    showFromDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("TK", color = White) },
                                onClick = {
                                    fromCurrency = "TK"
                                    showFromDropdown = false
                                }
                            )
                        }
                    }

                    // Input de cantidad
                    BasicTextField(
                        value = fromAmount,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                fromAmount = newValue
                            }
                        },
                        textStyle = TextStyle(
                            color = White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card: Intercambiar a
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
                    .padding(20.dp)
            ) {
                Text(
                    text = "Intercambiar a",
                    color = LightSteelBlue,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Fila con moneda y resultado (solo lectura)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Moneda (sin dropdown)
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toCurrency,
                            color = White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Cantidad resultante (solo lectura)
                    Text(
                        text = toAmount,
                        color = White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tasa de conversión dinámica
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (fromCurrency == "COP") "1 COP = 1 TK" else "1 TK = 1 COP",
                color = LightSteelBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Botón Intercambiar
        Button(
            onClick = { onSwapComplete() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Intercambiar",
                fontSize = 28.sp,
                color = White,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mensaje informativo
        Text(
            text = "El intercambio se realizará localmente y se sincronizará cuando haya conexión",
            color = LightSteelBlue,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

