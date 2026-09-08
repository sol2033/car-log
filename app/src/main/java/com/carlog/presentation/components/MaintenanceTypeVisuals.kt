package com.carlog.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.carlog.domain.model.MaintenanceType

/**
 * Цвет типа обслуживания. Один источник на все экраны: раньше набор был вписан прямо
 * в карточку списка, а событие может быть сразу нескольких типов — цвета понадобились
 * ещё и чипам формы, и сегментам карточки.
 */
@Composable
fun maintenanceTypeColor(type: MaintenanceType): Color = when (type) {
    MaintenanceType.REPAIR -> MaterialTheme.colorScheme.errorContainer
    MaintenanceType.SCHEDULED_SERVICE -> MaterialTheme.colorScheme.primaryContainer
    MaintenanceType.MODIFICATION -> MaterialTheme.colorScheme.tertiaryContainer
}

/**
 * Фон карточки события: равные доли на каждый его тип, с резкими границами.
 *
 * Доли равные, а не по деньгам: событие «95% ремонт + 5% ТО» иначе выглядело бы почти
 * одноцветным, и смысл «здесь есть и то, и другое» пропал бы. Порядок сегментов задаёт
 * `EventComposition.DISPLAY_ORDER`, поэтому цвета не прыгают между открытиями экрана.
 *
 * @param alpha прозрачность — карточка остаётся фоном, а не пятном.
 */
@Composable
fun eventTypeBrush(types: List<MaintenanceType>, alpha: Float = 0.3f): Brush {
    val colors = types.map { maintenanceTypeColor(it).copy(alpha = alpha) }
    if (colors.size <= 1) {
        val single = colors.firstOrNull() ?: Color.Transparent
        return Brush.horizontalGradient(listOf(single, single))
    }

    // Резкие границы: каждый цвет занимает свою долю и заканчивается ровно там, где
    // начинается следующий. Без парных стопов Compose размыл бы переход градиентом
    val stops = buildList {
        colors.forEachIndexed { index, color ->
            add(index / colors.size.toFloat() to color)
            add((index + 1) / colors.size.toFloat() to color)
        }
    }
    return Brush.horizontalGradient(colorStops = stops.toTypedArray())
}
