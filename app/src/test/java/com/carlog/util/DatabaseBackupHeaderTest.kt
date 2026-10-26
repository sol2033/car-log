package com.carlog.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Импорт проверяет, что выбран файл базы, до того как писать его поверх текущей.
 * Регрессия: любой файл, не оказавшийся ZIP (фото, PDF), затирал базу целиком.
 */
class DatabaseBackupHeaderTest {

    private fun bytes(text: String, tail: Int = 0) =
        text.toByteArray(Charsets.US_ASCII) + ByteArray(tail)

    @Test
    fun `заголовок SQLite распознаётся`() {
        assertTrue(DatabaseBackup.isSqliteHeader(bytes("SQLite format 3\u0000", tail = 84)))
    }

    @Test
    fun `фото и PDF базой не считаются`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(12)
        assertFalse(DatabaseBackup.isSqliteHeader(jpeg))
        assertFalse(DatabaseBackup.isSqliteHeader(bytes("%PDF-1.7\n", tail = 7)))
    }

    @Test
    fun `короткий или пустой файл базой не считается`() {
        assertFalse(DatabaseBackup.isSqliteHeader(ByteArray(0)))
        assertFalse(DatabaseBackup.isSqliteHeader(bytes("SQLite format")))
    }
}
