package com.clinicaserena.app.core.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clinicaserena.app.core.image.PractitionerPhotoLoader.Companion.isAcceptable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** BitmapFactory real: dimensiones sin decodificar (inJustDecodeBounds) y decodificación reducida. */
@RunWith(AndroidJUnit4::class)
class BitmapPhotoDecoderTest {

    private val decoder = BitmapPhotoDecoder()

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0xDD, 0xF3, 0xF1)) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    @Test
    fun leeDimensionesYRechazaLasExageradas() {
        val bounds = decoder.bounds(png(4100, 10))

        assertEquals(ImageBounds(4100, 10), bounds)
        assertFalse(bounds!!.isAcceptable())
    }

    @Test
    fun decodificaUnaFotoNormalReducida() {
        val bytes = png(512, 512)
        val bounds = decoder.bounds(bytes)!!
        assertTrue(bounds.isAcceptable())

        val image = decoder.decode(bytes, PractitionerPhotoLoader.sampleSizeFor(bounds, 256))

        assertNotNull(image)
        assertEquals(256, image!!.width)
    }

    @Test
    fun bytesQueNoSonImagenNoTienenDimensiones() {
        assertNull(decoder.bounds("no es una imagen".toByteArray()))
    }
}
