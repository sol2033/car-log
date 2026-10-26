package com.carlog.util

import com.carlog.data.local.CarLogDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Проверка базы из резервной копии до подмены текущей.
 *
 * Регрессии: любой файл, не оказавшийся ZIP (фото, PDF), затирал базу целиком; копия
 * с более новой схемой роняла приложение при каждом запуске (Room не умеет понижать версию).
 */
class BackupDatabaseCheckTest {

    /** Заголовок SQLite с заданным user_version */
    private fun sqliteHeader(userVersion: Int): ByteArray {
        val header = ByteArray(BackupDatabaseCheck.HEADER_SIZE)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(header)
        header[60] = (userVersion ushr 24).toByte()
        header[61] = (userVersion ushr 16).toByte()
        header[62] = (userVersion ushr 8).toByte()
        header[63] = userVersion.toByte()
        return header
    }

    @Test
    fun `база текущей и более старой схемы принимается`() {
        assertEquals(BackupDatabaseCheck.Verdict.OK, BackupDatabaseCheck.verdict(sqliteHeader(28), 28))
        assertEquals(BackupDatabaseCheck.Verdict.OK, BackupDatabaseCheck.verdict(sqliteHeader(20), 28))
    }

    @Test
    fun `база более новой схемы отклоняется`() {
        assertEquals(BackupDatabaseCheck.Verdict.TOO_NEW, BackupDatabaseCheck.verdict(sqliteHeader(29), 28))
    }

    @Test
    fun `версия читается как big-endian`() {
        assertEquals(300, BackupDatabaseCheck.userVersion(sqliteHeader(300)))
    }

    @Test
    fun `фото, PDF и пустой файл базой не считаются`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(96)
        assertEquals(BackupDatabaseCheck.Verdict.NOT_DATABASE, BackupDatabaseCheck.verdict(jpeg, 28))
        assertEquals(
            BackupDatabaseCheck.Verdict.NOT_DATABASE,
            BackupDatabaseCheck.verdict("%PDF-1.7\n".toByteArray() + ByteArray(91), 28)
        )
        assertEquals(BackupDatabaseCheck.Verdict.NOT_DATABASE, BackupDatabaseCheck.verdict(ByteArray(0), 28))
    }

    @Test
    fun `обрезанный заголовок без версии базой не считается`() {
        val short = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertNull(BackupDatabaseCheck.userVersion(short))
        assertEquals(BackupDatabaseCheck.Verdict.NOT_DATABASE, BackupDatabaseCheck.verdict(short, 28))
    }

    /** SCHEMA_VERSION обязан совпадать с последней экспортированной схемой Room */
    @Test
    fun `версия схемы совпадает с последней схемой в app_schemas`() {
        val dir = listOf(
            File("schemas/com.carlog.data.local.CarLogDatabase"),
            File("app/schemas/com.carlog.data.local.CarLogDatabase")
        ).first { it.isDirectory }
        val latest = dir.list()!!.mapNotNull { it.removeSuffix(".json").toIntOrNull() }.max()
        assertEquals(latest, CarLogDatabase.SCHEMA_VERSION)
    }
}
