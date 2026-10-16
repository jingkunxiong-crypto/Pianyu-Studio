package com.pianyu.studio.collage

import com.pianyu.studio.collage.model.templatesForPhotoCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSelectionRulesTest {
    @Test
    fun continueRequiresAtLeastTwoPhotos() {
        assertFalse(canContinueWithPhotoCount(0))
        assertFalse(canContinueWithPhotoCount(1))
        assertTrue(canContinueWithPhotoCount(2))
        assertTrue(canContinueWithPhotoCount(9))
    }

    private fun canContinueWithPhotoCount(count: Int): Boolean = count in 2..9

    @Test
    fun editorProvidesMultipleLayoutsForTwoToNinePhotos() {
        assertEquals(10, templatesForPhotoCount(2).size)
        (3..9).forEach { count ->
            assertTrue("$count photos should have at least ten layouts", templatesForPhotoCount(count).size >= 10)
        }
        assertTrue(templatesForPhotoCount(10).isEmpty())
    }

    @Test
    fun everyTemplateContainsOneValidCellPerPhoto() {
        (2..9).flatMap(::templatesForPhotoCount).forEach { template ->
            assertEquals(template.photoCount, template.cells.size)
            template.cells.forEach { cell ->
                assertTrue(cell.left >= 0f)
                assertTrue(cell.top >= 0f)
                assertTrue(cell.width > 0f)
                assertTrue(cell.height > 0f)
                assertTrue(cell.left + cell.width <= 1.0001f)
                assertTrue(cell.top + cell.height <= 1.0001f)
            }
        }
    }
}
