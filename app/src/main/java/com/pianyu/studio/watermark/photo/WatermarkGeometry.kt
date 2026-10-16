package com.pianyu.studio.watermark.photo

import kotlin.math.abs

data class WatermarkPose(val x: Float = .88f, val y: Float = .88f, val width: Float = .18f)
data class WatermarkBounds(val x: Float, val y: Float, val width: Float, val height: Float,
                           val halfX: Float, val halfY: Float) {
    fun contains(px: Float, py: Float, padding: Float = 0f): Boolean =
        abs(px - x) <= width / 2 + padding && abs(py - y) <= height / 2 + padding
}

/** Coordinates always refer to the full photo, never to letterbox margins. */
object WatermarkGeometry {
    fun resolve(photoWidth: Float, photoHeight: Float, signatureRatio: Float, pose: WatermarkPose): WatermarkBounds {
        require(photoWidth > 0 && photoHeight > 0 && signatureRatio > 0)
        val margin = minOf(photoWidth, photoHeight) * .025f
        // Leave a tiny tolerance so roundoff cannot invert the center's clamp range.
        val width = minOf(photoWidth * pose.width.coerceIn(.08f, .65f),
            .9999f * (photoWidth - 2 * margin), .9999f * (photoHeight - 2 * margin) * signatureRatio)
        val height = width / signatureRatio
        val hx = width / 2; val hy = height / 2
        return WatermarkBounds((pose.x * photoWidth).coerceIn(hx + margin, photoWidth - hx - margin),
            (pose.y * photoHeight).coerceIn(hy + margin, photoHeight - hy - margin), width, height, hx, hy)
    }
}
