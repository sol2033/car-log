package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Подсказки названий из прошлых записей: дороги, сервисы, заправки */
class NameSuggestionsTest {

    // Как отдаёт DAO: самые недавние первыми
    private val known = listOf("ЗСД", "М-11 «Нева»", "ЦКАД", "М-12 «Восток»", "Западный скоростной диаметр")

    @Test
    fun `пустое поле — последние использованные названия`() {
        assertEquals(listOf("ЗСД", "М-11 «Нева»", "ЦКАД"), NameSuggestions.suggest(known, "", limit = 3))
    }

    @Test
    fun `поиск без учёта регистра и по середине названия`() {
        assertEquals(listOf("М-11 «Нева»", "М-12 «Восток»"), NameSuggestions.suggest(known, "м-1"))
        assertEquals(listOf("Западный скоростной диаметр"), NameSuggestions.suggest(known, "скоростной"))
    }

    @Test
    fun `совпадение в начале названия идёт раньше совпадения в середине`() {
        val names = listOf("Обход Тосно", "Тосненская трасса")
        assertEquals(listOf("Тосненская трасса", "Обход Тосно"), NameSuggestions.suggest(names, "тосн"))
    }

    @Test
    fun `уже набранное целиком не предлагается`() {
        assertEquals(emptyList<String>(), NameSuggestions.suggest(known, "зсд"))
    }

    @Test
    fun `дубли с разным регистром и пробелами схлопываются в самое недавнее написание`() {
        val names = listOf("ЗСД ", "зсд", "  ", "ЦКАД")
        assertEquals(listOf("ЗСД", "ЦКАД"), NameSuggestions.suggest(names, ""))
    }

    @Test
    fun `ничего не нашлось — пусто`() {
        assertEquals(emptyList<String>(), NameSuggestions.suggest(known, "Крымский мост"))
    }

    // === Адрес выбранного сервиса ===

    // Как отдаёт DAO: самые недавние первыми
    private val addresses = listOf(
        ServiceAddress("Автосервис на Ленина", "ул. Ленина, 5"),
        ServiceAddress("Шиномонтаж", "  пр. Мира, 10  "),
        ServiceAddress("автосервис на ленина", "старый адрес")
    )

    @Test
    fun `адрес берётся из самой недавней записи с этим сервисом`() {
        assertEquals("ул. Ленина, 5", NameSuggestions.addressFor("Автосервис на Ленина", addresses))
    }

    /** Регистр сравнивается в Kotlin: SQLite-шный LOWER кириллицу не понижает */
    @Test
    fun `название сравнивается без учёта регистра и пробелов по краям`() {
        assertEquals("ул. Ленина, 5", NameSuggestions.addressFor("  АВТОСЕРВИС на ленина ", addresses))
        assertEquals("пробелы адреса обрезаются", "пр. Мира, 10", NameSuggestions.addressFor("шиномонтаж", addresses))
    }

    @Test
    fun `неизвестный сервис или пустое название — адреса нет`() {
        assertEquals(null, NameSuggestions.addressFor("Мойка", addresses))
        assertEquals(null, NameSuggestions.addressFor("  ", addresses))
    }

    @Test
    fun `пустой адрес пропускается, берётся следующий`() {
        val history = listOf(ServiceAddress("Мойка", "  "), ServiceAddress("Мойка", "ул. Садовая, 1"))
        assertEquals("ул. Садовая, 1", NameSuggestions.addressFor("Мойка", history))
    }
}
