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

    // --- Переход на общую сумму запчастей ---
    // Позиции при этом в БД не сохраняются, а прежние удаляются, поэтому их фото обязаны
    // уйти следом. Раньше они считались нужными и оставались в хранилище навсегда.

    @Test
    fun `в режиме общей суммы фото запчастей становятся сиротами`() {
        val parts = listOf(part("a.jpg"), part("b.jpg"))
        val touched = setOf("a.jpg", "b.jpg", "event.pdf")

        val referenced = referencedEventPhotos(
            eventPhotos = listOf("event.pdf"),
            parts = parts,
            keepsParts = false
        )

        assertEquals(setOf("event.pdf"), referenced)
        assertEquals(setOf("a.jpg", "b.jpg"), orphanPhotosToDelete(touched, referenced).toSet())
    }

    @Test
    fun `в режиме списка фото запчастей остаются нужными`() {
        val parts = listOf(part("a.jpg"), part("b.jpg"))
        val touched = setOf("a.jpg", "b.jpg", "event.pdf")

        val referenced = referencedEventPhotos(
            eventPhotos = listOf("event.pdf"),
            parts = parts,
            keepsParts = true
        )

        assertTrue(orphanPhotosToDelete(touched, referenced).isEmpty())
    }

    @Test
    fun `вложения самого события не зависят от режима стоимости запчастей`() {
        val eventPhotos = listOf("scan.pdf", "photo.jpg")

        val withParts = referencedEventPhotos(eventPhotos, listOf(part("a.jpg")), keepsParts = true)
        val withoutParts = referencedEventPhotos(eventPhotos, listOf(part("a.jpg")), keepsParts = false)

        assertTrue(withParts.containsAll(eventPhotos))
        assertTrue(withoutParts.containsAll(eventPhotos))
    }
}
