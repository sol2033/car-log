package com.carlog.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileHelper {

    /** Максимальный размер большей стороны сохраняемого фото, px */
    private const val MAX_IMAGE_DIMENSION = 2048

    /** Качество JPEG при пережатии */
    private const val JPEG_QUALITY = 85

    /**
     * Копирует фото из content:// URI во внутреннее хранилище приложения.
     * Изображение пережимается (длинная сторона ≤ 2048 px, JPEG 85%) — это в разы
     * сокращает объём хранилища и размеров бэкапов. Поворот из EXIF запекается в пиксели.
     * Если пережать не удалось — файл копируется как есть (прежнее поведение).
     * @return путь к сохранённому файлу или null при ошибке
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            // Создаём директорию для фото, если её нет
            val photosDir = File(context.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            // Генерируем уникальное имя файла
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val outputFile = File(photosDir, fileName)

            val saved = try {
                saveCompressed(context, uri, outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } catch (e: OutOfMemoryError) {
                false
            }

            if (!saved) {
                // Фолбэк: копируем оригинал без пережатия
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            outputFile.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveCompressed(context: Context, uri: Uri, outputFile: File): Boolean {
        val resolver = context.contentResolver

        // 1. Узнаём размеры без загрузки пикселей
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        // 2. Декодируем с прореживанием до ближайшей степени двойки
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return false

        // 3. Запекаем EXIF-поворот, иначе после пережатия фото будет лежать на боку
        val rotated = applyExifRotation(resolver, uri, bitmap)

        // 4. Если после inSampleSize сторона всё ещё больше лимита — досжимаем точно
        val finalBitmap = scaleDownIfNeeded(rotated)

        FileOutputStream(outputFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        finalBitmap.recycle()
        return true
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        while (maxOf(width, height) / (inSampleSize * 2) >= MAX_IMAGE_DIMENSION) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun applyExifRotation(
        resolver: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result != bitmap) bitmap.recycle()
        return result
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) return bitmap

        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide
        val result = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (result != bitmap) bitmap.recycle()
        return result
    }

    /**
     * Копирует документ (PDF) из content:// URI во внутреннее хранилище приложения.
     * Папка documents/ входит в бэкапы вместе с photos/.
     * @return путь к сохранённому файлу или null при ошибке
     */
    fun saveDocumentToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }

            val outputFile = File(documentsDir, "DOC_${UUID.randomUUID()}.pdf")
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Открывает PDF во внешнем просмотрщике.
     * Понимает и путь к файлу во внутреннем хранилище (через FileProvider),
     * и старый формат — сырой content:// URI (записи до v1.2.0; доступ мог протухнуть).
     * @return false, если открыть не удалось (нет просмотрщика / потерян доступ)
     */
    fun openPdf(context: Context, path: String): Boolean {
        return try {
            val uri = if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(path)
                )
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Удаляет файлы записи (фото/документы), пропуская сырые `content://` URI старых
     * записей — такие файлы приложению не принадлежат.
     */
    fun deleteFiles(paths: List<String>?) {
        paths?.forEach { path ->
            if (!path.startsWith("content://")) deleteFile(path)
        }
    }

    /**
     * Удаляет файл по пути
     */
    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
