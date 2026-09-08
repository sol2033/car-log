package com.carlog.data.backup

/**
 * Опись медиафайлов, на которые ссылается снимок бэкапа.
 *
 * Зачем: до версии 1.2.3 каждый бэкап заливал заново весь архив — БД плюс все фото и PDF.
 * При сотне фото это десятки мегабайт на каждое изменение данных, и чем дольше идёт
 * заливка, тем выше шанс поймать сетевой затык. Фото иммутабельны (имя `IMG_<uuid>.jpg`
 * генерируется один раз и никогда не переиспользуется), поэтому файл, уже лежащий на
 * Диске, повторно заливать незачем — достаточно перечислить его в описи.
 *
 * Формат намеренно текстовый, без JSON: разбирается без Android-зависимостей и
 * поэтому тестируется на JVM.
 *
 * ```
 * carlog-backup-v2
 * photos/IMG_1a2b.jpg
 * documents/DOC_3c4d.pdf
 * ```
 */
data class BackupManifest(val mediaEntries: List<String>) {

    fun serialize(): String = buildString {
        append(HEADER).append('\n')
        mediaEntries.forEach { append(it).append('\n') }
    }

    companion object {
        const val HEADER = "carlog-backup-v2"

        /** Имя файла описи внутри снимка и рядом с ним на Диске */
        const val FILE_NAME = "manifest.txt"

        /**
         * Разбирает опись. `null` — это не опись версии 2: значит, перед нами
         * старый цельный архив, и восстанавливать его надо по-старому.
         */
        fun parse(text: String): BackupManifest? {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != HEADER) return null
            return BackupManifest(lines.drop(1))
        }
    }
}

/**
 * Что заливать и что подчищать. Чистые функции над именами файлов — без сети и без Android.
 */
object BackupMediaDiff {

    /**
     * Файлы, которых на Диске ещё нет. Сравнение по имени: имена уникальны (UUID),
     * содержимое под одним именем не меняется, поэтому сверять хеши не нужно.
     */
    fun toUpload(local: Collection<String>, remote: Collection<String>): List<String> {
        val present = remote.toHashSet()
        return local.filterNot { present.contains(it) }.sorted()
    }

    /**
     * Файлы на Диске, на которые не ссылается ни один из сохраняемых снимков.
     *
     * Считаем по объединению описей всех оставляемых копий, а не только последней:
     * иначе восстановление из копии двухдневной давности осталось бы без фотографий,
     * удалённых из приложения вчера.
     */
    fun orphans(remote: Collection<String>, referencedByKeptBackups: Collection<String>): List<String> {
        val referenced = referencedByKeptBackups.toHashSet()
        return remote.filterNot { referenced.contains(it) }.sorted()
    }
}
