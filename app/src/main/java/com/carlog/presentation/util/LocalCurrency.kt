package com.carlog.presentation.util

import androidx.compose.runtime.compositionLocalOf
import com.carlog.data.preferences.Currency

/**
 * CompositionLocal для доступа к выбранной валюте в Composable-функциях
 */
val LocalCurrency = compositionLocalOf<Currency> { Currency.RUB }
