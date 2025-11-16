package com.g22.offline_blockchain_payments.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utilidad para formatear números con punto como separador de miles
 * Siempre usa punto (.) independientemente de la configuración regional del dispositivo
 */
object NumberFormatter {
    
    /**
     * Formatea un número Long con punto como separador de miles
     * Ejemplo: 5000 -> "5.000"
     */
    fun formatAmount(amount: Long): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,###", symbols)
        return formatter.format(amount)
    }
    
    /**
     * Formatea un String de números agregando puntos como separadores de miles
     * Útil para formateo en tiempo real mientras el usuario escribe
     * Ejemplo: "5000" -> "5.000"
     */
    fun formatAmountString(input: String): String {
        if (input.isEmpty()) return ""
        
        // Remover puntos existentes y obtener solo números
        val numbersOnly = input.replace(".", "")
        
        if (numbersOnly.isEmpty()) return ""
        
        // Convertir a Long y formatear
        return try {
            val amount = numbersOnly.toLong()
            formatAmount(amount)
        } catch (e: NumberFormatException) {
            input // Si hay error, devolver el input original
        }
    }
    
    /**
     * Remueve los puntos de un string formateado para obtener el número puro
     * Ejemplo: "5.000" -> "5000"
     */
    fun unformatAmount(formattedAmount: String): String {
        return formattedAmount.replace(".", "")
    }
    
    /**
     * Convierte un string formateado a Long
     * Ejemplo: "5.000" -> 5000
     */
    fun parseAmount(formattedAmount: String): Long? {
        return try {
            unformatAmount(formattedAmount).toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}

