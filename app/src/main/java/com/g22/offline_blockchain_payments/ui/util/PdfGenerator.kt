package com.g22.offline_blockchain_payments.ui.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class PdfResult(
    val filePath: String,
    val uri: Uri?
)

object PdfGenerator {
    
    /**
     * Genera un PDF del recibo y lo guarda en el almacenamiento local
     */
    fun generateReceiptPdf(
        context: Context,
        transactionId: String,
        amount: Long,
        fromName: String,
        fromId: String,
        toName: String,
        toId: String,
        timestamp: Long,
        isReceiver: Boolean,
        concept: String? = null
    ): PdfResult? {
        try {
            // Crear documento PDF
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint()
            
            // Configuración de estilos
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            
            val headerPaint = Paint().apply {
                color = Color.BLACK
                textSize = 18f
                isFakeBoldText = true
            }
            
            val normalPaint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
            }
            
            val amountPaint = Paint().apply {
                color = if (isReceiver) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800")
                textSize = 32f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            
            // Dibujar contenido
            var yPosition = 80f
            
            // Título
            canvas.drawText("COMPROBANTE DE PAGO", 297.5f, yPosition, titlePaint)
            yPosition += 60f
            
            // Tipo de comprobante
            val receiptType = if (isReceiver) "AgroPuntos recibidos" else "AgroPuntos entregados"
            canvas.drawText(receiptType, 50f, yPosition, headerPaint)
            yPosition += 40f
            
            // Monto
            val formattedAmount = NumberFormatter.formatAmount(amount) + " AP"
            canvas.drawText(formattedAmount, 297.5f, yPosition, amountPaint)
            yPosition += 60f
            
            // Línea divisoria
            canvas.drawLine(50f, yPosition, 545f, yPosition, normalPaint)
            yPosition += 30f
            
            // Información de las personas
            if (isReceiver) {
                // Recibí de
                canvas.drawText("Recibí de:", 50f, yPosition, headerPaint)
                yPosition += 25f
                canvas.drawText(fromName, 50f, yPosition, normalPaint)
                yPosition += 20f
                canvas.drawText("C.C. $fromId", 50f, yPosition, normalPaint)
                yPosition += 40f
                
                // Yo soy
                canvas.drawText("Yo soy:", 50f, yPosition, headerPaint)
                yPosition += 25f
                canvas.drawText(toName, 50f, yPosition, normalPaint)
                yPosition += 20f
                canvas.drawText("C.C. $toId", 50f, yPosition, normalPaint)
            } else {
                // Yo soy
                canvas.drawText("Yo soy:", 50f, yPosition, headerPaint)
                yPosition += 25f
                canvas.drawText(fromName, 50f, yPosition, normalPaint)
                yPosition += 20f
                canvas.drawText("C.C. $fromId", 50f, yPosition, normalPaint)
                yPosition += 40f
                
                // Di a
                canvas.drawText("Di a:", 50f, yPosition, headerPaint)
                yPosition += 25f
                canvas.drawText(toName, 50f, yPosition, normalPaint)
                yPosition += 20f
                canvas.drawText("C.C. $toId", 50f, yPosition, normalPaint)
            }
            
            // Mostrar concepto solo si existe
            if (!concept.isNullOrBlank()) {
                yPosition += 40f
                canvas.drawText("Concepto:", 50f, yPosition, headerPaint)
                yPosition += 25f
                canvas.drawText(concept, 50f, yPosition, normalPaint)
            }
            
            yPosition += 40f
            
            // Línea divisoria
            canvas.drawLine(50f, yPosition, 545f, yPosition, normalPaint)
            yPosition += 30f
            
            // ID de transacción
            canvas.drawText("ID de transacción:", 50f, yPosition, headerPaint)
            yPosition += 25f
            canvas.drawText(transactionId, 50f, yPosition, normalPaint)
            yPosition += 40f
            
            // Fecha y hora
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp * 1000))
            canvas.drawText("Fecha: $dateStr", 50f, yPosition, normalPaint)
            yPosition += 60f
            
            // Nota al pie
            canvas.drawLine(50f, yPosition, 545f, yPosition, normalPaint)
            yPosition += 30f
            val footerPaint = Paint().apply {
                color = Color.GRAY
                textSize = 12f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Este comprobante se subirá cuando tengas conexión", 297.5f, yPosition, footerPaint)
            yPosition += 20f
            canvas.drawText("Sistema de Pagos Offline - AgroPuntos", 297.5f, yPosition, footerPaint)
            
            pdfDocument.finishPage(page)
            
            // Guardar el PDF
            val fileName = "Comprobante_${transactionId}_${System.currentTimeMillis()}.pdf"
            val result = savePdfToStorage(context, pdfDocument, fileName)
            
            pdfDocument.close()
            
            return result
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun savePdfToStorage(context: Context, pdfDocument: PdfDocument, fileName: String): PdfResult? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usar MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AgroPuntos")
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
                )
                
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    PdfResult(filePath = it.toString(), uri = it)
                }
            } else {
                // Android 9 y anteriores
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val agroPuntosDir = File(documentsDir, "AgroPuntos")
                if (!agroPuntosDir.exists()) {
                    agroPuntosDir.mkdirs()
                }
                
                val file = File(agroPuntosDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                
                // Obtener URI usando FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                PdfResult(filePath = file.absolutePath, uri = uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Abre el PDF con el visor predeterminado del sistema
     */
    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

