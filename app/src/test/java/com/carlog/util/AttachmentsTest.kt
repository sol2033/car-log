package com.carlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фото и PDF живут в одном списке путей, поэтому тип вложения определяется по расширению,
 * а обложкой автомобиля может стать только картинка.
 */
class AttachmentsTest {

    @Test
    fun `pdf определяется по расширению независимо от регистра`() {
        assertTrue(Attachments.isPdf("/data/documents/DOC_1.pdf"))
        assertTrue(Attachments.isPdf("/data/documents/DOC_1.PDF"))
        assertFalse(Attachments.isPdf("/data/photos/IMG_1.jpg"))
    }

    @Test
    fun `файл без расширения считается картинкой`() {
        // Старые записи хранят пути, сгенерированные до появления документов
        assertTrue(Attachments.isImage("/data/photos/IMG_1"))
    }

    @Test
    fun `pdf не попадает в список картинок`() {
        val paths = listOf("a.jpg", "b.pdf", "c.jpg")

        assertEquals(listOf("a.jpg", "c.jpg"), Attachments.images(paths))
        assertEquals("a.jpg", Attachments.firstImage(paths))
    }

    @Test
    fun `основным фото становится первая картинка, а не первый файл`() {
        assertEquals("b.jpg", Attachments.nextMainPhoto(listOf("a.pdf", "b.jpg"), current = null))
    }

    @Test
    fun `прикреплённый pdf не перебивает выбранную обложку`() {
        assertEquals("a.jpg", Attachments.nextMainPhoto(listOf("a.jpg", "b.pdf"), current = "a.jpg"))
    }

    @Test
    fun `после удаления обложки берётся следующая картинка`() {
        assertEquals("c.jpg", Attachments.nextMainPhoto(listOf("b.pdf", "c.jpg"), current = "a.jpg"))
    }

    @Test
    fun `из одних документов обложки не получается`() {
        assertNull(Attachments.nextMainPhoto(listOf("a.pdf", "b.pdf"), current = null))
        assertNull(Attachments.nextMainPhoto(emptyList(), current = "a.jpg"))
    }
}
