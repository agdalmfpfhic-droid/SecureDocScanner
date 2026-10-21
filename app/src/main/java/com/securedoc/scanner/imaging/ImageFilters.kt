package com.securedoc.scanner.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.securedoc.scanner.data.model.PageFilter
import kotlin.math.max
import kotlin.math.min

/**
 * Filtres appliqués localement, en complément de ceux de ML Kit.
 * Les filtres « texte net » et « reçu » divisent chaque pixel par la luminosité
 * moyenne de son voisinage : ombres, taches et papier jauni deviennent blancs.
 * Le noir et blanc utilise un seuillage adaptatif (méthode de Bradley).
 * Limite : images jusqu'à ~8 Mpx (image intégrale en Int).
 */
object ImageFilters {

    fun apply(src: Bitmap, filter: PageFilter): Bitmap = when (filter) {
        PageFilter.ORIGINAL -> src
        PageFilter.COLOR -> withMatrix(src, ColorMatrix().apply {
            setSaturation(1.15f)
            postConcat(contrast(1.2f, 8f))
        })
        PageFilter.GRAYSCALE -> withMatrix(src, ColorMatrix().apply {
            setSaturation(0f)
            postConcat(contrast(1.1f, 6f))
        })
        PageFilter.BLACK_WHITE -> adaptiveThreshold(src)
        PageFilter.SHARP_TEXT -> sharpen(normalizeBackground(src, low = 40, high = 235))
        PageFilter.RECEIPT -> normalizeBackground(src, low = 70, high = 215)
    }

    private fun contrast(c: Float, brightness: Float): ColorMatrix {
        val t = (1f - c) * 128f + brightness
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun withMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun luminance(src: Bitmap): IntArray {
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            px[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return px
    }

    /** Image intégrale de taille (w+1)×(h+1). */
    private fun integral(lum: IntArray, w: Int, h: Int): IntArray {
        val stride = w + 1
        val ii = IntArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0
            for (x in 0 until w) {
                rowSum += lum[y * w + x]
                ii[(y + 1) * stride + x + 1] = ii[y * stride + x + 1] + rowSum
            }
        }
        return ii
    }

    private fun boxMean(ii: IntArray, w: Int, h: Int, x: Int, y: Int, r: Int): Int {
        val stride = w + 1
        val x1 = max(0, x - r); val x2 = min(w - 1, x + r)
        val y1 = max(0, y - r); val y2 = min(h - 1, y + r)
        val sum = ii[(y2 + 1) * stride + x2 + 1] - ii[y1 * stride + x2 + 1] -
            ii[(y2 + 1) * stride + x1] + ii[y1 * stride + x1]
        return sum / ((x2 - x1 + 1) * (y2 - y1 + 1))
    }

    private fun toBitmap(gray: IntArray, w: Int, h: Int): Bitmap {
        for (i in gray.indices) {
            val v = gray[i]
            gray[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(gray, w, h, Bitmap.Config.ARGB_8888)
    }

    fun adaptiveThreshold(src: Bitmap, percent: Int = 12): Bitmap {
        val w = src.width; val h = src.height
        val lum = luminance(src)
        val ii = integral(lum, w, h)
        val r = max(8, min(w, h) / 16)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            out[i] = if (lum[i] * 100 < boxMean(ii, w, h, x, y, r) * (100 - percent)) 0 else 255
        }
        return toBitmap(out, w, h)
    }

    fun normalizeBackground(src: Bitmap, low: Int, high: Int): Bitmap {
        val w = src.width; val h = src.height
        val lum = luminance(src)
        val ii = integral(lum, w, h)
        val r = max(10, min(w, h) / 12)
        val range = (high - low).coerceAtLeast(1)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val background = max(boxMean(ii, w, h, x, y, r), 1)
            val flat = min(255, lum[i] * 255 / background)
            out[i] = ((flat - low) * 255 / range).coerceIn(0, 255)
        }
        return toBitmap(out, w, h)
    }

    /** Netteté (noyau laplacien 3×3) sur une image en niveaux de gris. */
    fun sharpen(gray: Bitmap): Bitmap {
        val w = gray.width; val h = gray.height
        val px = IntArray(w * h)
        gray.getPixels(px, 0, w, 0, 0, w, h)
        val v = IntArray(px.size) { px[it] and 0xFF }
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            out[i] = if (x == 0 || y == 0 || x == w - 1 || y == h - 1) v[i]
            else (5 * v[i] - v[i - 1] - v[i + 1] - v[i - w] - v[i + w]).coerceIn(0, 255)
        }
        return toBitmap(out, w, h)
    }
}
