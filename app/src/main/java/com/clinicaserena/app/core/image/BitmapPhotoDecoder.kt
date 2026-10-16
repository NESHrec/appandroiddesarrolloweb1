package com.clinicaserena.app.core.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Decodificador de Android: primero solo dimensiones (`inJustDecodeBounds`), luego la imagen reducida. */
class BitmapPhotoDecoder : PhotoDecoder<ImageBitmap> {

    override fun bounds(bytes: ByteArray): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) ImageBounds(options.outWidth, options.outHeight) else null
    }

    override fun decode(bytes: ByteArray, sampleSize: Int): ImageBitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
