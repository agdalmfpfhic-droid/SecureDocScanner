package com.securedoc.scanner.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.securedoc.scanner.data.model.ScanProfile
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/** Génération PDF multipage locale avec android.graphics.pdf.PdfDocument. */
object PdfBuilder {

    private const val PT_PER_MM = 72f / 25.4f

    /**
     * Taille de page en points selon le profil.
     * L'orientation suit celle de l'image (une carte scannée verticalement reste verticale).
     */
    fun pageSize(profile: ScanProfile, imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        val ratio = imageHeight.toFloat() / imageWidth.coerceAtLeast(1)
        var w = profile.widthMm * PT_PER_MM
        var h = profile.heightMm * PT_PER_MM
        when {
            w <= 0f -> { w = 210f * PT_PER_MM; h = w * ratio } // livre : largeur A4, ratio de l'image
            h <= 0f -> h = w * ratio                           // reçu : largeur fixe, hauteur libre
            (w > h) != (imageWidth > imageHeight) -> { val t = w; w = h; h = t }
        }
        return w.roundToInt() to h.roundToInt().coerceAtLeast(1)
    }

    suspend fun write(
        profile: ScanProfile,
        pageCount: Int,
        render: suspend (index: Int) -> Bitmap,
        out: OutputStream,
    ) {
        val pdf = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            for (i in 0 until pageCount) {
                val bitmap = render(i)
                val (pw, ph) = pageSize(profile, bitmap.width, bitmap.height)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                // Recadrage au ratio du profil : l'image est centrée et ajustée, fond blanc
                val scale = min(pw.toFloat() / bitmap.width, ph.toFloat() / bitmap.height)
                val dw = bitmap.width * scale
                val dh = bitmap.height * scale
                val left = (pw - dw) / 2f
                val top = (ph - dh) / 2f
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), paint)
                pdf.finishPage(page)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }
}
