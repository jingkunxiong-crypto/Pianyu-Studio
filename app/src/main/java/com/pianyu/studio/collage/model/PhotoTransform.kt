package com.pianyu.studio.collage.model

/** Offsets are fractions of the photo cell, so the crop survives ratio and layout changes. */
data class PhotoTransform(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val clockwiseQuarterTurns: Int = 0,
)
