package com.carlog.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Подсказки названий из прошлых записей — строкой чипов под полем ввода.
 *
 * Чипы, а не выпадающее меню: в Material3 1.2 всплывающее меню у редактируемого поля
 * забирает фокус и прячет клавиатуру посреди набора. Список готовит
 * [com.carlog.domain.model.NameSuggestions]; пустой — ничего не рисуется.
 */
@Composable
fun NameSuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onPick(suggestion) },
                label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
