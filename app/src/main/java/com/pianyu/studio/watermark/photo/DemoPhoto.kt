package com.pianyu.studio.watermark.photo

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pianyu.studio.R

/** Decode the bundled lossless image at its actual size for both preview and export. */
internal fun decodeDemoPhoto(resources: Resources): Bitmap {
    val bytes = resources.openRawResource(R.drawable.demo_landscape).use { it.readBytes() }
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inMutable = true
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
}
