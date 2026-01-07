package com.carlog.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object FileHelper {
    
    /**
     * Копирует файл из content:// URI во внутреннее хранилище приложения
     * @return путь к сохранённому файлу или null при ошибке
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            // Открываем input stream из URI
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            // Создаём директорию для фото, если её нет
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Генерируем уникальное имя файла
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val outputFile = File(imagesDir, fileName)
            
            // Копируем содержимое
            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Возвращаем абсолютный путь к файлу
            outputFile.absolutePath
            
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
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
