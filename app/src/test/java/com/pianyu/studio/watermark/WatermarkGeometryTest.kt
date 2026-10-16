package com.pianyu.studio.watermark

import com.pianyu.studio.watermark.photo.*
import org.junit.Assert.*
import org.junit.Test

class WatermarkGeometryTest {
    @Test fun watermarksStayInsidePortraitLandscapeAndPanorama() {
        for ((w, h) in listOf(400f to 1200f, 1200f to 400f, 800f to 600f, 10000f to 100f)) {
            for (ratio in listOf(.1f, 1f, 8f)) {
                for (x in listOf(-1f, .5f, 2f)) for (y in listOf(-1f, .5f, 2f)) {
                    val b = WatermarkGeometry.resolve(w, h, ratio, WatermarkPose(x, y, .65f))
                    assertTrue(b.x - b.halfX >= -.001f)
                    assertTrue(b.x + b.halfX <= w + .001f)
                    assertTrue(b.y - b.halfY >= -.001f)
                    assertTrue(b.y + b.halfY <= h + .001f)
                    assertTrue(b.width > 0 && b.height > 0)
                }
            }
        }
    }
    @Test fun bottomCenterPresetAndCoordinatesScaleWithPhoto() {
        val pose = WatermarkPose(.5f, 1f, .2f)
        val b = WatermarkGeometry.resolve(1000f, 800f, 4f, pose)
        assertEquals(500f, b.x, .001f)
        assertEquals(755f, b.y, .001f)
        assertTrue(b.contains(b.x + 90f, b.y))
        assertFalse(b.contains(b.x, b.y + 90f))
        val doubled = WatermarkGeometry.resolve(2000f, 1600f, 4f, pose)
        assertEquals(b.x * 2, doubled.x, .001f)
        assertEquals(b.y * 2, doubled.y, .001f)
        assertEquals(b.width * 2, doubled.width, .001f)
    }
}
