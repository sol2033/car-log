package com.carlog.presentation.components

import com.carlog.domain.model.MaintenanceType

/**
 * Запчасть, добавляемая внутри события (обслуживание или ДТП), до сохранения самого события.
 *
 * Настоящая запись в модуле «Запчасти» создаётся только при сохранении события, поэтому
 * здесь лежат уже введённые пользователем данные. Поля, которые определяются событием
 * (дата, пробег, способ установки, сервис и стоимость его работ), сюда не входят намеренно:
 * они берутся из события, иначе разойдутся с ним, а стоимость работ ещё и задвоилась бы
 * с собственной «стоимостью работ сервиса» события.
 *
 * @property showInPartsList показывать ли запись в модуле «Запчасти»: мелочь вроде прокладок
 * можно оставить только внутри события — стоимость и привязка при этом сохраняются.
 * @property maintenanceType ремонт это или тюнинг. Тип живёт у позиции, а не у события:
 * за один заезд в сервис могут поменять сломанный рычаг и поставить защиту картера,
 * и деньги должны разойтись по разным категориям статистики.
 * @property partId id уже сохранённой записи (при редактировании события); null — ещё не создана.
 * @property photosPaths файлы уже скопированы в хранилище, поэтому при отказе от события
 * их нужно чистить — см. [orphanPhotosToDelete].
 */
data class EventPart(
    val name: String,
    val manufacturer: String = "",
    val partNumber: String = "",
    val price: Double,
    val notes: String = "",
    val photosPaths: List<String> = emptyList(),
    val showInPartsList: Boolean = true,
    val maintenanceType: MaintenanceType = MaintenanceType.REPAIR,
    val partId: Long? = null
)

/**
 * Файлы фотографий, на которые больше никто не ссылается — их нужно удалить,
 * иначе они останутся в хранилище навсегда и попадут в каждый бэкап.
 *
 * @param touchedPhotos всё, что прошло через экран: фото уже сохранённых запчастей плюс
 * выбранные в этот заход.
 * @param referencedPhotos на что ссылаются после операции. При **сохранении** события — фото
 * итогового списка; при **отказе** от него — фото, которые были у записей изначально
 * (тогда «сиротами» окажется всё выбранное в этот заход).
 */
fun orphanPhotosToDelete(
    touchedPhotos: Set<String>,
    referencedPhotos: Set<String>
): List<String> = (touchedPhotos - referencedPhotos).toList()

/**
 * Вложения, на которые событие ссылается после сохранения — вторая половина пары
 * к [orphanPhotosToDelete].
 *
 * @param keepsParts сохраняются ли сами позиции. `false` — событие уходит с **общей суммой**
 * запчастей: список в БД не попадает, а прежние записи удаляются, поэтому их фото больше
 * никому не принадлежат. Раньше они всё равно считались нужными и оставались в хранилище
 * навсегда, попадая в каждый бэкап.
 */
fun referencedEventPhotos(
    eventPhotos: List<String>,
    parts: List<EventPart>,
    keepsParts: Boolean
): Set<String> = buildSet {
    addAll(eventPhotos)
    if (keepsParts) parts.forEach { addAll(it.photosPaths) }
}
