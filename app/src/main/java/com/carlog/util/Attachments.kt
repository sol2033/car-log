package com.carlog.util

/**
 * Вложения записи: фотографии и PDF-документы лежат в одном списке путей.
 *
 * Отдельного поля под документы намеренно нет — иначе каждая сущность с фото
 * потребовала бы миграции, а хранилище и оба бэкапа уже устроены так, что папки
 * `photos/` и `documents/` архивируются целиком, а удаление идёт по пути файла
 * ([FileHelper.deleteFiles]). Тип вложения определяется по расширению: PDF попадает
 * в хранилище только через [FileHelper.saveDocumentToInternalStorage], который
 * даёт имя `DOC_<uuid>.pdf`.
 *
 * Чистые функции без Android — правила выбора основного фото проверяются на JVM.
 */
object Attachments {

    /** Документ, а не картинка: показывается карточкой и открывается внешним просмотрщиком */
    fun isPdf(path: String): Boolean = path.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    fun isImage(path: String): Boolean = !isPdf(path)

    /** Только картинки — там, где PDF отрисовать нечем (миниатюра в списке, основное фото авто) */
    fun images(paths: List<String>): List<String> = paths.filter { isImage(it) }

    fun firstImage(paths: List<String>): String? = paths.firstOrNull { isImage(it) }

    /**
     * Основное фото автомобиля после изменения списка вложений.
     *
     * Основным может быть только картинка: PDF нечем показать ни в списке машин, ни в шапке
     * карточки. Уже выбранное фото не перебиваем — иначе прикреплённый следом документ
     * или удаление любого другого снимка молча меняли бы обложку.
     */
    fun nextMainPhoto(paths: List<String>, current: String?): String? =
        if (current != null && paths.contains(current) && isImage(current)) current
        else firstImage(paths)
}
