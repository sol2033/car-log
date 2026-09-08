package com.carlog.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Журнал задуман так, что пользователь **отправляет файл разработчику**. Значит, содержимое
 * файла — это то, что человек в худшем случае покажет постороннему: переслал письмо, выложил
 * в общую папку, приложил скриншотом. Ни одна проверка ниже не косметическая.
 */
class DiagnosticRedactorTest {

    @Test
    fun `OAuth-токен не попадает в журнал`() {
        val text = "Authorization: OAuth y0_AgAAAABsecretTOKENvalue failed"

        val redacted = DiagnosticRedactor.redact(text)!!

        assertFalse("токен утёк в журнал", redacted.contains("y0_AgAAAABsecretTOKENvalue"))
        assertTrue(redacted.contains("OAuth ***"))
    }

    @Test
    fun `access_token из OAuth-редиректа вычищается`() {
        // Так выглядит deep link, которым Яндекс возвращает токен в приложение
        val text = "carlog://oauth/yandex#access_token=y0_SECRET&token_type=bearer"

        val redacted = DiagnosticRedactor.redact(text)!!

        assertFalse(redacted.contains("y0_SECRET"))
        assertTrue(redacted.contains("access_token=***"))
    }

    @Test
    fun `подписанная ссылка загрузки теряет параметры, но сохраняет хост`() {
        // Сама эта ссылка даёт право писать в чужой Диск — в файле ей не место.
        // А хост оставить обязательно: ради него журнал и заводился
        val text = "PUT https://uploader1g.disk.yandex.net/upload-target/2024?hash=abc&sign=def"

        val redacted = DiagnosticRedactor.redact(text)!!

        assertFalse(redacted.contains("sign=def"))
        assertFalse(redacted.contains("hash=abc"))
        assertTrue(
            "хост загрузчика — главная улика, его терять нельзя",
            redacted.contains("uploader1g.disk.yandex.net")
        )
    }

    @Test
    fun `обычный текст не портится`() {
        val text = "SocketTimeoutException: timeout | phase=UPLOAD host=uploader1g.disk.yandex.net"

        assertEquals(text, DiagnosticRedactor.redact(text))
    }

    @Test
    fun `пустое и null переживаются`() {
        assertNull(DiagnosticRedactor.redact(null))
        assertEquals("", DiagnosticRedactor.redact(""))
    }
}

class DiagnosticEntryTest {

    @Test
    fun `запись переживает сериализацию`() {
        val entry = DiagnosticEntry(
            timestamp = 1_700_000_000_000,
            severity = DiagnosticSeverity.ERROR,
            area = DiagnosticArea.BACKUP,
            event = "backup_failure",
            details = "SocketTimeoutException: timeout phase=UPLOAD host=uploader1g.disk.yandex.net"
        )

        assertEquals(entry, DiagnosticEntry.parse(entry.serialize()))
    }

    @Test
    fun `разделитель и переносы строк в тексте не ломают разбор`() {
        // В сообщение исключения может попасть что угодно, включая наш разделитель
        val entry = DiagnosticEntry(
            timestamp = 1,
            severity = DiagnosticSeverity.CRASH,
            area = DiagnosticArea.APP,
            event = "uncaught_exception",
            details = "at Foo.bar\nat Baz|qux"
        )

        val parsed = DiagnosticEntry.parse(entry.serialize())

        assertEquals(DiagnosticSeverity.CRASH, parsed?.severity)
        assertEquals("uncaught_exception", parsed?.event)
        assertTrue(parsed?.details?.contains("Baz/qux") == true)
    }

    @Test
    fun `запись без подробностей разбирается`() {
        val entry = DiagnosticEntry(
            timestamp = 42,
            severity = DiagnosticSeverity.INFO,
            area = DiagnosticArea.APP,
            event = "app_started"
        )

        assertNull(DiagnosticEntry.parse(entry.serialize())?.details)
    }

    @Test
    fun `битые строки пропускаются`() {
        assertNull(DiagnosticEntry.parse("мусор"))
        assertNull(DiagnosticEntry.parse("1|НЕИЗВЕСТНО|APP|event|-"))
    }

    @Test
    fun `длинный стек обрезается`() {
        val entry = DiagnosticEntry(
            timestamp = 1,
            severity = DiagnosticSeverity.CRASH,
            area = DiagnosticArea.APP,
            event = "uncaught_exception",
            details = "x".repeat(DiagnosticEntry.MAX_DETAILS * 3)
        )

        val parsed = DiagnosticEntry.parse(entry.serialize())

        assertEquals(DiagnosticEntry.MAX_DETAILS, parsed?.details?.length)
    }
}

class DiagnosticBufferTest {

    private fun entry(index: Int) = DiagnosticEntry(
        timestamp = index.toLong(),
        severity = DiagnosticSeverity.ERROR,
        area = DiagnosticArea.BACKUP,
        event = "event_$index"
    )

    @Test
    fun `журнал не растёт бесконечно`() {
        var log = emptyList<DiagnosticEntry>()
        repeat(DiagnosticBuffer.MAX_ENTRIES + 10) { log = DiagnosticBuffer.append(log, entry(it)) }

        assertEquals(DiagnosticBuffer.MAX_ENTRIES, log.size)
    }

    @Test
    fun `вытесняются самые старые записи, а не самые новые`() {
        // Жалоба приходит после сбоя, значит нужны последние события
        var log = emptyList<DiagnosticEntry>()
        repeat(DiagnosticBuffer.MAX_ENTRIES + 10) { log = DiagnosticBuffer.append(log, entry(it)) }

        assertEquals(
            (DiagnosticBuffer.MAX_ENTRIES + 9).toLong(),
            log.last().timestamp
        )
        assertEquals(10L, log.first().timestamp)
    }

    @Test
    fun `журнал переживает запись и чтение файла`() {
        val log = listOf(entry(1), entry(2), entry(3))

        assertEquals(log, DiagnosticBuffer.parse(DiagnosticBuffer.serialize(log)))
    }

    @Test
    fun `пустой файл даёт пустой журнал`() {
        assertTrue(DiagnosticBuffer.parse("").isEmpty())
        assertTrue(DiagnosticBuffer.parse("\n\n").isEmpty())
    }
}
