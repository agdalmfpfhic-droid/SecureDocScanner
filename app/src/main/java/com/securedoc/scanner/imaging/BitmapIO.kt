package com.securedoc.scanner.imaging

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.securedoc.scanner.data.model.ExportFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedImage(val bytes: ByteArray, val width: Int, val height: Int)

object BitmapIO {

    fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Image illisible")
        return scaleDown(bitmap, maxSide)
    }

    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Lit une image (scan ML Kit ou galerie), applique l'orientation EXIF,
     * réduit la taille et ré-encode en JPEG. Le ré-encodage supprime toutes les
     * métadonnées EXIF (position GPS, modèle d'appareil…).
     */
    fun normalize(resolver: ContentResolver, uri: Uri, maxSide: Int, quality: Int): NormalizedImage {
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Impossible de lire l'image")
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(raw)).rotationDegrees }.getOrDefault(0)
        val bitmap = rotate(decodeSampled(raw, maxSide), orientation)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return NormalizedImage(out.toByteArray(), bitmap.width, bitmap.height)
    }

    fun encode(bitmap: Bitmap, format: ExportFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val compress = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bitmap.compress(compress, quality, out)
        return out.toByteArray()
    }
}
