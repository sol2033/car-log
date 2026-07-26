package com.carlog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Диспетчер для тяжёлых вычислений (фильтрация и агрегация статистики).
 * Вынесен в зависимость, чтобы в тестах его можно было подменить на тестовый:
 * `Dispatchers.Default` живёт вне тестового планировщика, и `advanceUntilIdle()`
 * не дожидается расчёта.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
