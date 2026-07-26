package com.carlog.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фото запчасти копируется в хранилище сразу при выборе, а сама запчасть создаётся только
 * при сохранении события. Значит файлы легко осиротеть — правила чистки проверяем отдельно.
 */
class EventPartPhotosTest {

    private fun part(vararg photos: String) = EventPart(name = "Деталь", price = 100.0, photosPaths = photos.toList())

    @Test
    fun `при сохранении удаляются фото убранных позиций`() {
        val touched = setOf("a.jpg", "b.jpg", "c.jpg")
        val referenced = listOf(part("a.jpg")).flatMap { it.photosPaths }.toSet()

        val orphans = orphanPhotosToDelete(touched, referenced)

        assertEquals(setOf("b.jpg", "c.jpg"), orphans.toSet())
    }

    @Test
    fun `при сохранении фото итогового списка не трогаются`() {
        val touched = setOf("a.jpg", "b.jpg")
        val referenced = listOf(part("a.jpg"), part("b.jpg")).flatMap { it.photosPaths }.toSet()

        assertTrue(orphanPhotosToDelete(touched, referenced).isEmpty())
    }

    @Test
    fun `при отказе от события удаляется только выбранное в этот заход`() {
        val original = setOf("saved.jpg")           // фото уже сохранённой запчасти
        val touched = original + setOf("new.jpg")   // плюс выбранное сейчас

        val orphans = orphanPhotosToDelete(touched, original)

        assertEquals(listOf("new.jpg"), orphans)
    }

    @Test
    fun `при отказе фото сохранённых запчастей остаются на месте`() {
        val original = setOf("saved1.jpg", "saved2.jpg")

        assertTrue(orphanPhotosToDelete(original, original).isEmpty())
    }

    @Test
    fun `фото, убранное из окна и возвращённое обратно, не удаляется`() {
        // Пользователь убрал фото, передумал и выбрал его снова — путь остался в итоговом списке
        val touched = setOf("a.jpg")
        val referenced = setOf("a.jpg")

        assertTrue(orphanPhotosToDelete(touched, referenced).isEmpty())
    }

    @Test
    fun `новая запчасть без фото ничего не удаляет`() {
        assertTrue(orphanPhotosToDelete(emptySet(), emptySet()).isEmpty())
    }
}
