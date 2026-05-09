package com.catkeeper.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.catkeeper.app.data.database.DailySummary
import com.catkeeper.app.data.database.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.util.Date

object ExportHelper {

    fun exportToPdf(context: Context, summaries: List<DailySummary>, todaySessions: List<SessionEntity>) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 16f
        }
        
        var y = 50f
        canvas.drawText("CatKeeper - Instagram Stats Report", 50f, y, paint)
        y += 40f
        
        paint.textSize = 12f
        canvas.drawText("Last 7 Days Summaries:", 50f, y, paint)
        y += 20f
        
        summaries.forEach { sum ->
            val min = sum.totalDuration / 60000
            canvas.drawText("Date: ${sum.dateString} - ${min}m - ${sum.totalScrolls} scrolls", 60f, y, paint)
            y += 20f
        }
        
        y += 20f
        canvas.drawText("Today's Sessions:", 50f, y, paint)
        y += 20f
        
        todaySessions.forEach { ses ->
            val min = ses.durationMs / 60000
            canvas.drawText("Start: ${Date(ses.startTimeMs)} - ${min}m - ${ses.scrollCount} scrolls", 60f, y, paint)
            y += 20f
        }
        
        document.finishPage(page)
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "CatKeeper_Stats_${System.currentTimeMillis()}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    document.writeTo(out)
                }
                Toast.makeText(context, "PDF saved to Downloads", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error saving PDF", Toast.LENGTH_SHORT).show()
            }
        }
        document.close()
    }

    fun shareStatsCard(context: Context, totalUsageMs: Long, totalScrolls: Long) {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        
        // Background
        canvas.drawColor(android.graphics.Color.parseColor("#0D0D1A")) // DarkBackground
        
        // Title
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 60f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CatKeeper Stats", 400f, 150f, paint)
        
        // Usage
        paint.textSize = 40f
        paint.color = android.graphics.Color.parseColor("#B0B0C0")
        canvas.drawText("Today's Instagram Time:", 400f, 300f, paint)
        
        paint.textSize = 80f
        paint.color = android.graphics.Color.parseColor("#FF6D00") // OrangeAccent
        val min = totalUsageMs / 60000
        canvas.drawText("${min}m", 400f, 400f, paint)
        
        // Scrolls
        paint.textSize = 40f
        paint.color = android.graphics.Color.parseColor("#B0B0C0")
        canvas.drawText("Reels Scrolls:", 400f, 550f, paint)
        
        paint.textSize = 80f
        paint.color = android.graphics.Color.parseColor("#7C4DFF") // PurpleAccent
        canvas.drawText("$totalScrolls", 400f, 650f, paint)
        
        // Save to cache
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "stats_card.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Stats Card"))
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating card", Toast.LENGTH_SHORT).show()
        }
    }
}
