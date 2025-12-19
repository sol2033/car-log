# Car Log - MVP Архитектура и Структура Проекта

## 📋 Описание проекта

**Car Log** - мобильное приложение для Android, которое помогает владельцам автомобилей вести учёт:
- Пробега и характеристик автомобиля
- Установленных запчастей и их состояния
- Поломок и ремонтов
- Расхода топлива
- Дополнительных расходов (мойка, аксессуары, полировка)
- ДТП и связанных выплат
- Статистики по всем категориям расходов

---

## 🛠 Технический стек

### **Язык программирования**
- **Kotlin** - официальный язык для Android от Google
  - Современный, безопасный, лаконичный
  - Защита от NullPointerException
  - Легче Java для изучения
  - Большое сообщество и документация

### **UI Framework**
- **Jetpack Compose** - современный декларативный UI toolkit от Google
  - Меньше кода, чем XML layouts
  - Автоматическое обновление интерфейса при изменении данных
  - Простая поддержка тем (светлая/тёмная)
  - Material Design 3 из коробки
  - Стандарт разработки с 2021 года

### **Архитектурный паттерн**
- **MVVM (Model-View-ViewModel)** + Clean Architecture
  - **Model** - данные и бизнес-логика
  - **View** - UI (Compose screens)
  - **ViewModel** - связующее звено, управление состоянием
  - Разделение ответственности, легче тестировать
  - Официальная рекомендация Google для Android

### **База данных**
- **Room Database** - SQLite обёртка от Google
  - Локальное хранилище на устройстве
  - Типобезопасные SQL запросы
  - Автоматические миграции схемы
  - Интеграция с Kotlin Coroutines
  - Не требует интернета

### **Авторизация**
- **Firebase Authentication** - готовая система авторизации от Google
  - Email/пароль регистрация
  - Восстановление пароля
  - Email verification
  - Безопасное хранение токенов
  - Бесплатно до 10,000 пользователей/месяц

### **Асинхронность**
- **Kotlin Coroutines** - управление асинхронными операциями
  - Замена callback hell
  - Работа с БД без блокировки UI
  - Отмена операций при закрытии экрана
  - Простой синтаксис с suspend функциями

### **Dependency Injection (DI)**
- **Hilt** - упрощённая версия Dagger от Google
  - Автоматическое создание объектов
  - Внедрение зависимостей (ViewModel, Repository, Database)
  - Меньше boilerplate кода
  - Тестируемость

### **Навигация**
- **Jetpack Navigation Compose** - навигация между экранами
  - Типобезопасная передача данных
  - Deep links поддержка
  - Back stack управление
  - Анимации переходов

### **Работа с изображениями**
- **Coil** - библиотека загрузки изображений для Compose
  - Кеширование фото
  - Сжатие изображений
  - Kotlin-first, написана специально для Compose
  - Легковесная (меньше Glide/Picasso)

### **Локализация и темы**
- **Jetpack Compose Resources** - встроенная система
  - Поддержка русского/английского
  - Автоматическое переключение по языку системы
  - Material 3 Dynamic Colors для тем

### **Минимальная версия**
- **Android 10 (API 29)** - покрывает ~85% устройств (на 2025 год)
  - Современные API безопасности
  - Scoped Storage (защита файлов)
  - Dark Theme поддержка системная

---

## 🏗 Архитектура приложения

### **Слои архитектуры (Clean Architecture)**

```
┌─────────────────────────────────────────┐
│         UI Layer (Compose)              │
│  ┌────────────┐  ┌──────────────┐      │
│  │  Screens   │  │  ViewModels  │      │
│  └────────────┘  └──────────────┘      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│       Domain Layer (Optional)            │
│  ┌──────────────┐  ┌──────────────┐    │
│  │  Use Cases   │  │   Entities   │    │
│  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│          Data Layer                      │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ Repositories │  │  Data Sources│    │
│  │              │  │ (Room, Firebase)│ │
│  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────┘
```

**Пояснение:**
1. **UI Layer** - то, что видит пользователь (экраны, кнопки, списки)
2. **Domain Layer** - бизнес-логика (расчёт статистики, валидация)
3. **Data Layer** - получение/сохранение данных (Room БД, Firebase)

---

## 📁 Структура проекта

```
car-log/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/carlog/
│   │   │   │   ├── di/                    # Dependency Injection (Hilt модули)
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   ├── DatabaseModule.kt
│   │   │   │   │   └── RepositoryModule.kt
│   │   │   │   │
│   │   │   │   ├── data/                  # Слой данных
│   │   │   │   │   ├── local/             # Локальная БД
│   │   │   │   │   │   ├── dao/           # Data Access Objects (SQL запросы)
│   │   │   │   │   │   │   ├── CarDao.kt
│   │   │   │   │   │   │   ├── PartDao.kt
│   │   │   │   │   │   │   ├── BreakdownDao.kt
│   │   │   │   │   │   │   ├── RefuelingDao.kt
│   │   │   │   │   │   │   ├── ExpenseDao.kt
│   │   │   │   │   │   │   └── AccidentDao.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── entities/      # Room Entity классы (таблицы БД)
│   │   │   │   │   │   │   ├── CarEntity.kt
│   │   │   │   │   │   │   ├── PartEntity.kt
│   │   │   │   │   │   │   ├── BreakdownEntity.kt
│   │   │   │   │   │   │   ├── RefuelingEntity.kt
│   │   │   │   │   │   │   ├── ExpenseEntity.kt
│   │   │   │   │   │   │   └── AccidentEntity.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   └── CarLogDatabase.kt  # Room Database главный класс
│   │   │   │   │   │
│   │   │   │   │   ├── repository/        # Репозитории (связь с данными)
│   │   │   │   │   │   ├── CarRepository.kt
│   │   │   │   │   │   ├── PartRepository.kt
│   │   │   │   │   │   ├── BreakdownRepository.kt
│   │   │   │   │   │   ├── RefuelingRepository.kt
│   │   │   │   │   │   ├── ExpenseRepository.kt
│   │   │   │   │   │   ├── AccidentRepository.kt
│   │   │   │   │   │   └── AuthRepository.kt
│   │   │   │   │   │
│   │   │   │   │   └── firebase/          # Firebase интеграция
│   │   │   │   │       └── FirebaseAuthManager.kt
│   │   │   │   │
│   │   │   │   ├── domain/                # Бизнес-логика
│   │   │   │   │   ├── model/             # Domain модели (UI модели)
│   │   │   │   │   │   ├── Car.kt
│   │   │   │   │   │   ├── Part.kt
│   │   │   │   │   │   ├── Breakdown.kt
│   │   │   │   │   │   ├── Refueling.kt
│   │   │   │   │   │   ├── Expense.kt
│   │   │   │   │   │   └── Accident.kt
│   │   │   │   │   │
│   │   │   │   │   └── usecase/           # Use Cases (бизнес-операции)
│   │   │   │   │       ├── CalculateAverageFuelConsumptionUseCase.kt
│   │   │   │   │       ├── CalculateTotalRepairCostUseCase.kt
│   │   │   │   │       ├── CalculateTotalMaintenanceCostUseCase.kt
│   │   │   │   │       └── UpdateCarMileageUseCase.kt
│   │   │   │   │
│   │   │   │   ├── ui/                    # UI слой (Compose)
│   │   │   │   │   ├── theme/             # Темы и стили
│   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   ├── Theme.kt
│   │   │   │   │   │   └── Type.kt
│   │   │   │   │   │
│   │   │   │   │   ├── components/        # Переиспользуемые UI компоненты
│   │   │   │   │   │   ├── CarCard.kt
│   │   │   │   │   │   ├── PartListItem.kt
│   │   │   │   │   │   ├── StatisticCard.kt
│   │   │   │   │   │   └── PhotoPicker.kt
│   │   │   │   │   │
│   │   │   │   │   ├── navigation/        # Навигация
│   │   │   │   │   │   ├── NavGraph.kt
│   │   │   │   │   │   └── Screen.kt
│   │   │   │   │   │
│   │   │   │   │   └── screens/           # Экраны приложения
│   │   │   │   │       ├── auth/          # Авторизация
│   │   │   │   │       │   ├── LoginScreen.kt
│   │   │   │   │       │   ├── LoginViewModel.kt
│   │   │   │   │       │   ├── RegisterScreen.kt
│   │   │   │   │       │   └── RegisterViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── carlist/       # Список автомобилей
│   │   │   │   │       │   ├── CarListScreen.kt
│   │   │   │   │       │   └── CarListViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── cardetail/     # Детали автомобиля (главная)
│   │   │   │   │       │   ├── CarDetailScreen.kt
│   │   │   │   │       │   └── CarDetailViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── addcar/        # Добавление автомобиля
│   │   │   │   │       │   ├── AddCarScreen.kt
│   │   │   │   │       │   └── AddCarViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── parts/         # Список запчастей
│   │   │   │   │       │   ├── PartsScreen.kt
│   │   │   │   │       │   ├── PartsViewModel.kt
│   │   │   │   │       │   ├── AddPartScreen.kt
│   │   │   │   │       │   └── AddPartViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── breakdowns/    # Поломки и ремонты
│   │   │   │   │       │   ├── BreakdownsScreen.kt
│   │   │   │   │       │   ├── BreakdownsViewModel.kt
│   │   │   │   │       │   ├── AddBreakdownScreen.kt
│   │   │   │   │       │   └── AddBreakdownViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── refueling/     # Заправки
│   │   │   │   │       │   ├── RefuelingScreen.kt
│   │   │   │   │       │   ├── RefuelingViewModel.kt
│   │   │   │   │       │   ├── AddRefuelingScreen.kt
│   │   │   │   │       │   └── AddRefuelingViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── expenses/      # Прочие расходы
│   │   │   │   │       │   ├── ExpensesScreen.kt
│   │   │   │   │       │   ├── ExpensesViewModel.kt
│   │   │   │   │       │   ├── AddExpenseScreen.kt
│   │   │   │   │       │   └── AddExpenseViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── accidents/     # ДТП
│   │   │   │   │       │   ├── AccidentsScreen.kt
│   │   │   │   │       │   ├── AccidentsViewModel.kt
│   │   │   │   │       │   ├── AddAccidentScreen.kt
│   │   │   │   │       │   └── AddAccidentViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       ├── statistics/    # Статистика
│   │   │   │   │       │   ├── StatisticsScreen.kt
│   │   │   │   │       │   └── StatisticsViewModel.kt
│   │   │   │   │       │
│   │   │   │   │       └── settings/      # Настройки
│   │   │   │   │           ├── SettingsScreen.kt
│   │   │   │   │           └── SettingsViewModel.kt
│   │   │   │   │
│   │   │   │   ├── util/                  # Утилиты и расширения
│   │   │   │   │   ├── Constants.kt
│   │   │   │   │   ├── Extensions.kt
│   │   │   │   │   ├── DateFormatter.kt
│   │   │   │   │   └── ImageManager.kt
│   │   │   │   │
│   │   │   │   └── CarLogApplication.kt   # Application класс (Hilt entry point)
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── values/                # Ресурсы по умолчанию (русский)
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   │
│   │   │   │   ├── values-en/             # Английская локализация
│   │   │   │   │   └── strings.xml
│   │   │   │   │
│   │   │   │   ├── drawable/              # Иконки и изображения
│   │   │   │   └── mipmap/                # Launcher иконки
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/                          # Unit тесты
│   │       └── java/com/carlog/
│   │
│   ├── build.gradle.kts                   # Gradle конфигурация модуля
│   └── google-services.json               # Firebase конфигурация (после настройки)
│
├── build.gradle.kts                       # Root Gradle файл
├── settings.gradle.kts                    # Gradle settings
├── gradle.properties                      # Gradle свойства
└── README.md
```

---

## 📊 Модели данных (Entities)

### **1. CarEntity (Автомобиль)**
```kotlin
@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: String,              // Firebase UID пользователя
    val brand: String,                // Марка (обязательно) - "Toyota"
    val model: String,                // Модель (обязательно) - "Camry"
    val year: Int?,                   // Год выпуска - 2020
    val color: String?,               // Цвет - "Черный"
    val licensePlate: String?,        // Госномер - "А123БВ777"
    val vin: String?,                 // VIN код - "1HGBH41JXMN109186"
    
    val engineModel: String?,         // Модель двигателя - "2GR-FE"
    val engineVolume: Double?,        // Объем двигателя (литры) - 3.5
    val transmissionType: String?,    // Тип КПП - "Автомат", "Механика", "Робот", "Вариатор"
    val driveType: String?,           // Привод - "Полный", "Передний", "Задний"
    
    val fuelType: String,             // Тип топлива - "Бензин АИ-95", "Дизель", "Электро", "Газ", "Гибрид"
    
    val currentMileage: Int,          // Текущий пробег (км) - обновляется автоматически
    val purchaseMileage: Int?,        // Пробег при покупке (км)
    val purchaseDate: Long?,          // Дата покупки (timestamp)
    
    val mainPhotoPath: String?,       // Путь к главному фото автомобиля
    val photosPaths: List<String>?,   // Список путей дополнительных фото
    
    val notes: String?,               // Заметки от пользователя
    val createdAt: Long,              // Дата создания записи
    val updatedAt: Long               // Дата обновления
)
```

### **2. PartEntity (Запчасть)**
```kotlin
@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PartEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,                  // Связь с автомобилем
    
    val name: String,                 // Название запчасти - "Передние тормозные колодки"
    val manufacturer: String?,        // Производитель - "Bosch"
    val partNumber: String?,          // Артикул - "0 986 494 109"
    
    val installDate: Long,            // Дата установки (timestamp)
    val installMileage: Int,          // Пробег при установке (км)
    val installationType: String,     // Способ установки - "Самостоятельно", "Сервис"
    
    val price: Double,                // Цена запчасти
    val servicePrice: Double?,        // Цена работы сервиса (если установка в сервисе)
    
    val isBroken: Boolean = false,    // Сломана ли запчасть
    val breakdownDate: Long?,         // Дата поломки (если сломана)
    val breakdownMileage: Int?,       // Пробег при поломке
    val mileageDriven: Int?,          // Пройденный пробег запчастью (рассчитывается)
    
    val notes: String?,               // Заметки
    val createdAt: Long,
    val updatedAt: Long
)
```

### **3. BreakdownEntity (Поломка/Ремонт)**
```kotlin
@Entity(
    tableName = "breakdowns",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BreakdownEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,                  // Связь с автомобилем
    
    val title: String,                // Название поломки - "Износ тормозных колодок"
    val description: String,          // Описание поломки
    
    val breakdownDate: Long,          // Дата поломки
    val breakdownMileage: Int,        // Пробег при поломке
    
    val brokenPartId: Long?,          // ID сломанной запчасти (из PartEntity)
    val brokenPartName: String?,      // Название сломанной запчасти (если не из списка)
    
    val installedPartIds: List<Long>?, // Список ID установленных запчастей для ремонта
    
    val partsCost: Double,            // Стоимость только запчастей
    val serviceCost: Double,          // Стоимость только работы сервиса
    val totalCost: Double,            // Общая стоимость (partsCost + serviceCost)
    
    val photosPaths: List<String>?,   // Фото ремонта/поломки
    
    val notes: String?,               // Дополнительные заметки
    val createdAt: Long,
    val updatedAt: Long
)
```

### **4. RefuelingEntity (Заправка)**
```kotlin
@Entity(
    tableName = "refuelings",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RefuelingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,                  // Связь с автомобилем
    
    val date: Long,                   // Дата заправки
    val mileage: Int,                 // Пробег на момент заправки
    
    val fuelType: String,             // Тип топлива - "АИ-92", "АИ-95", "АИ-98", "Дизель", "Газ", "Электричество"
    val liters: Double,               // Количество литров (или кВтч для электро)
    val pricePerLiter: Double,        // Цена за литр/кВтч
    val totalCost: Double,            // Общая стоимость заправки
    
    val isFullTank: Boolean,          // Заправка "до полного"
    val fuelConsumption: Double?,     // Расход на 100км (рассчитывается автоматически)
    
    val gasStation: String?,          // Название АЗС
    val notes: String?,               // Заметки
    
    val createdAt: Long,
    val updatedAt: Long
)
```

### **5. ExpenseEntity (Прочие расходы)**
```kotlin
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,                  // Связь с автомобилем
    
    val category: String,             // Категория - "Мойка", "Аксессуары", "Полировка", "Страховка", "Налоги", "Парковка", "Штрафы"
    val title: String,                // Название расхода
    val description: String?,         // Описание
    
    val date: Long,                   // Дата расхода
    val mileage: Int?,                // Пробег (опционально)
    
    val cost: Double,                 // Стоимость
    
    val photosPaths: List<String>?,   // Фото чеков/расходов
    val notes: String?,               // Заметки
    
    val createdAt: Long,
    val updatedAt: Long
)
```

### **6. AccidentEntity (ДТП)**
```kotlin
@Entity(
    tableName = "accidents",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AccidentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,                  // Связь с автомобилем
    
    val date: Long,                   // Дата ДТП
    val mileage: Int,                 // Пробег на момент ДТП
    
    val location: String?,            // Место ДТП
    val faultType: String,            // Виновник - "Я", "Другой водитель", "Обоюдная вина", "Нет виновных"
    
    val damageDescription: String,    // Описание повреждений автомобиля пользователя
    val severity: String,             // Серьезность - "Незначительная", "Средняя", "Серьезная", "Тотальная"
    
    val hasInsurancePayout: Boolean,  // Есть ли выплата по страховке
    val insurancePayoutAmount: Double?, // Размер выплаты по страховке
    
    val hasOtherPayouts: Boolean,     // Есть ли другие выплаты
    val otherPayoutsAmount: Double?,  // Размер других выплат
    
    val repairCost: Double?,          // Стоимость ремонта (может быть связан с Breakdown)
    val relatedBreakdownId: Long?,    // ID связанной поломки/ремонта
    
    val photosPaths: List<String>?,   // Фото с места ДТП, повреждений
    val documentsPath: List<String>?, // Документы (справки, протоколы)
    
    val notes: String?,               // Заметки
    val createdAt: Long,
    val updatedAt: Long
)
```

---

## 🎯 MVP Функционал (по приоритетам)

### **Этап 1: Core функционал (2-3 недели)**
**Что реализуем первым делом:**

1. ✅ **Авторизация (Firebase)**
   - Регистрация с email/паролем
   - Вход в систему
   - Выход из аккаунта
   - Восстановление пароля (опционально для MVP)

2. ✅ **Управление автомобилями**
   - Список автомобилей пользователя
   - Добавление автомобиля (марка, модель - обязательно, остальное опционально)
   - Просмотр деталей автомобиля
   - Редактирование автомобиля
   - Удаление автомобиля
   - Одно главное фото автомобиля

3. ✅ **Пробег**
   - Отображение текущего пробега
   - Автоматическое обновление пробега из последней записи
   - Ручное изменение пробега с подтверждением

4. ✅ **Базовая навигация**
   - Главный экран со списком авто
   - Детальная страница авто с вкладками/разделами
   - Переход между экранами

### **Этап 2: Учёт расходов (2-3 недели)**

5. ✅ **Заправки**
   - Список всех заправок автомобиля
   - Добавление заправки (дата, литры, стоимость, пробег, тип топлива)
   - Удаление/редактирование заправки
   - Автоматический расчёт расхода на 100км (если заправка "до полного")

6. ✅ **Прочие расходы**
   - Список расходов автомобиля
   - Добавление расхода (категория, название, стоимость, дата)
   - Удаление/редактирование расхода
   - Прикрепление фото чеков (1-3 фото)

7. ✅ **Базовая статистика**
   - Средний расход топлива на 100км
   - Общая стоимость заправок за месяц/год
   - Общая стоимость прочих расходов

### **Этап 3: Запчасти и ремонты (3-4 недели)**

8. ✅ **Запчасти**
   - Список установленных запчастей
   - Добавление запчасти (название, дата, пробег, цена, способ установки)
   - Отображение пройденного пробега запчастью
   - Отметка запчасти как сломанной
   - Удаление/редактирование запчасти

9. ✅ **Поломки и ремонты**
   - Список поломок/ремонтов
   - Добавление поломки с описанием
   - Выбор сломанной запчасти (из списка или вручную)
   - Добавление установленных запчастей для ремонта
   - Раздельный учёт стоимости запчастей и работы
   - Прикрепление фото (до 5 фото)

10. ✅ **Статистика по ремонтам**
    - Общая стоимость ремонтов
    - Стоимость ремонтов по месяцам
    - Фильтр по датам

### **Этап 4: ДТП и дополнительно (2 недели)**

11. ✅ **ДТП**
    - Список ДТП
    - Добавление ДТП (дата, виновник, повреждения, выплаты)
    - Прикрепление фото (до 10 фото)
    - Связь с ремонтом (опционально)

12. ✅ **Общая статистика содержания**
    - Итоговая стоимость: ремонты + топливо + прочие расходы
    - График расходов по месяцам
    - Фильтры по периодам

### **Этап 5: UX улучшения (1-2 недели)**

13. ✅ **Настройки приложения**
    - Переключение языка (русский/английский)
    - Переключение темы (светлая/тёмная/системная)
    - Информация о приложении
    - Выход из аккаунта

14. ✅ **Улучшения UI**
    - Анимации переходов
    - Пустые состояния (Empty States) с подсказками
    - Loading индикаторы
    - Валидация форм с понятными ошибками

---

## 📱 Структура экранов приложения

### **Главная навигация**
```
┌────────────────────────────────────┐
│  Bottom Navigation Bar             │
├────────────────────────────────────┤
│  🚗 Мои авто  │  📊 Статистика  │ ⚙️ │
└────────────────────────────────────┘
```

### **1. Экран "Мои автомобили" (CarListScreen)**
- Список карточек с автомобилями
- Каждая карточка: фото, марка, модель, текущий пробег
- FAB кнопка "+" для добавления авто
- Клик на карточку → переход к деталям авто

### **2. Экран "Детали автомобиля" (CarDetailScreen)**
**Главная информация:**
- Фото автомобиля (галерея)
- Характеристики (марка, модель, год, госномер и т.д.)
- Текущий пробег (с кнопкой редактирования)
- Кнопка "Редактировать авто"

**Вкладки/разделы (можно использовать TabRow или вертикальные кнопки):**
- 📝 **Заправки** → RefuelingScreen
- 🔧 **Запчасти** → PartsScreen
- ⚠️ **Поломки** → BreakdownsScreen
- 💰 **Расходы** → ExpensesScreen
- 🚨 **ДТП** → AccidentsScreen

### **3. Экран "Добавить/Редактировать авто" (AddCarScreen)**
- Форма с полями:
  - Марка* (обязательно)
  - Модель* (обязательно)
  - Год
  - Цвет
  - Госномер
  - VIN
  - Модель двигателя
  - Объем двигателя
  - Тип КПП (dropdown)
  - Привод (dropdown)
  - Тип топлива (dropdown)*
  - Текущий пробег*
  - Пробег при покупке
  - Дата покупки (DatePicker)
  - Фото (PhotoPicker)
  - Заметки
- Кнопка "Сохранить"

### **4. Экран "Заправки" (RefuelingScreen)**
- Список заправок (дата, литры, стоимость, расход)
- FAB "+" для добавления
- Клик на элемент → редактирование

**AddRefuelingScreen:**
- Дата (DatePicker)
- Пробег
- Тип топлива (dropdown)
- Количество литров
- Цена за литр
- Общая стоимость (рассчитывается автоматически)
- Заправка "до полного" (Checkbox)
- Название АЗС
- Заметки

### **5. Экран "Запчасти" (PartsScreen)**
- Список запчастей (название, дата установки, пробег, статус)
- Фильтр: все / активные / сломанные
- FAB "+" для добавления

**AddPartScreen:**
- Название*
- Производитель
- Артикул
- Дата установки (DatePicker)*
- Пробег установки*
- Способ установки (Radio: Самостоятельно / Сервис)*
- Цена запчасти*
- Цена работы сервиса (если "Сервис")
- Заметки

### **6. Экран "Поломки" (BreakdownsScreen)**
- Список поломок (название, дата, стоимость)
- FAB "+" для добавления

**AddBreakdownScreen:**
- Название*
- Описание*
- Дата*
- Пробег*
- Сломанная запчасть:
  - Radio: "Выбрать из списка" / "Указать вручную"
  - Dropdown или TextField
- Установленные запчасти для ремонта (multiple select + создание новой)
- Стоимость запчастей*
- Стоимость работы сервиса*
- Общая стоимость (рассчитывается)
- Фото (до 5 шт)
- Заметки

### **7. Экран "Расходы" (ExpensesScreen)**
- Список расходов (категория, название, стоимость, дата)
- FAB "+" для добавления

**AddExpenseScreen:**
- Категория (dropdown: Мойка, Аксессуары, Полировка, Страховка, Налоги, Парковка, Штрафы)*
- Название*
- Описание
- Дата*
- Пробег
- Стоимость*
- Фото чеков (до 3 шт)
- Заметки

### **8. Экран "ДТП" (AccidentsScreen)**
- Список ДТП (дата, серьезность, виновник)
- FAB "+" для добавления

**AddAccidentScreen:**
- Дата*
- Пробег*
- Место ДТП
- Виновник (Radio: Я / Другой / Обоюдная / Нет виновных)*
- Описание повреждений*
- Серьезность (dropdown: Незначительная / Средняя / Серьезная / Тотальная)*
- Страховая выплата:
  - Checkbox "Есть выплата"
  - Сумма выплаты
- Другие выплаты:
  - Checkbox "Есть выплаты"
  - Сумма
- Стоимость ремонта
- Связанный ремонт (dropdown из Breakdowns)
- Фото (до 10 шт)
- Заметки

### **9. Экран "Статистика" (StatisticsScreen)**
**Селектор автомобиля** (dropdown для выбора)

**Карточки статистики:**
1. **Расход топлива**
   - Средний расход л/100км
   - График расхода по месяцам

2. **Стоимость содержания**
   - Общая стоимость за выбранный период
   - Разбивка: топливо / ремонты / прочее
   - Фильтр по периоду (месяц, год, всё время)

3. **Ремонты**
   - Общая стоимость ремонтов
   - Количество ремонтов
   - Фильтр по месяцам

4. **Пробег**
   - Общий пробег автомобиля
   - Средний пробег в месяц

### **10. Экран "Настройки" (SettingsScreen)**
- **Язык**
  - Radio: Русский / English
- **Тема**
  - Radio: Светлая / Тёмная / Системная
- **Аккаунт**
  - Email пользователя
  - Кнопка "Выйти из аккаунта"
- **О приложении**
  - Версия приложения
  - Разработчик
  - Лицензия

### **11. Экраны авторизации**

**LoginScreen:**
- Поле Email
- Поле Пароль
- Кнопка "Войти"
- Ссылка "Забыли пароль?" (опционально для MVP)
- Ссылка "Создать аккаунт" → RegisterScreen

**RegisterScreen:**
- Поле Email
- Поле Пароль
- Поле Повторите пароль
- Кнопка "Зарегистрироваться"
- Ссылка "Уже есть аккаунт?" → LoginScreen

---

## 📈 План разработки MVP

### **Фаза 0: Подготовка (3-5 дней)**
1. Создание проекта в Android Studio
2. Настройка Gradle зависимостей (Hilt, Room, Compose, Firebase)
3. Создание Firebase проекта
4. Настройка базовой структуры пакетов
5. Настройка темы (Material 3, светлая/тёмная)
6. Настройка локализации (strings.xml русский/английский)

### **Фаза 1: Авторизация и база (1 неделя)**
1. Firebase Authentication интеграция
2. Экраны Login/Register
3. AuthRepository, AuthViewModel
4. Сохранение состояния авторизации
5. Навигация: авторизация → главный экран

### **Фаза 2: Room Database и автомобили (1.5 недели)**
1. Создание Room Database
2. CarEntity, CarDao, CarRepository
3. CarListScreen с ViewModel
4. AddCarScreen с формой
5. CarDetailScreen (базовая версия)
6. CRUD операции для автомобилей

### **Фаза 3: Заправки и базовая статистика (1.5 недели)**
1. RefuelingEntity, Dao, Repository
2. RefuelingScreen + AddRefuelingScreen
3. Расчёт расхода топлива (Use Case)
4. Базовый StatisticsScreen (средний расход)
5. Обновление пробега автоматически

### **Фаза 4: Расходы (1 неделя)**
1. ExpenseEntity, Dao, Repository
2. ExpensesScreen + AddExpenseScreen
3. Категории расходов
4. Прикрепление фото (Coil + PhotoPicker)
5. Добавление расходов в статистику

### **Фаза 5: Запчасти (1.5 недели)**
1. PartEntity, Dao, Repository
2. PartsScreen + AddPartScreen
3. Расчёт пробега запчастей
4. Отметка запчасти как сломанной
5. Связь с поломками (подготовка)

### **Фаза 6: Поломки и ремонты (2 недели)**
1. BreakdownEntity, Dao, Repository
2. BreakdownsScreen + AddBreakdownScreen
3. Выбор сломанной запчасти
4. Создание новых запчастей при ремонте
5. Раздельный учёт стоимости
6. Прикрепление фото
7. Статистика по ремонтам

### **Фаза 7: ДТП (1 неделя)**
1. AccidentEntity, Dao, Repository
2. AccidentsScreen + AddAccidentScreen
3. Учёт выплат
4. Связь с ремонтами
5. Прикрепление фото

### **Фаза 8: Статистика и финализация (1.5 недели)**
1. Полная StatisticsScreen с графиками
2. Фильтры по датам
3. Общая стоимость содержания
4. SettingsScreen (язык, тема)
5. Полировка UI/UX
6. Тестирование

### **Фаза 9: Тестирование и баги (1 неделя)**
1. Полное тестирование функционала
2. Исправление багов
3. Оптимизация производительности
4. Подготовка к релизу

---

## 🔐 Безопасность и best practices

### **Firebase Security**
1. Email verification при регистрации
2. Ограничение API ключа по package name в Firebase Console
3. Включение App Check для защиты от ботов
4. Сложные пароли (минимум 8 символов, буквы+цифры)

### **Room Database**
1. Encrypted Shared Preferences для sensitive данных
2. SQLCipher для шифрования БД (опционально)
3. Регулярные бэкапы (экспорт в файл)

### **Хранение фото**
1. Использование Scoped Storage (Android 10+)
2. Сжатие изображений перед сохранением
3. Ограничение размера фото (макс 2MB на фото)
4. Удаление фото при удалении записей

### **Производительность**
1. LazyColumn для списков (не создаёт все элементы сразу)
2. Pagination для больших списков (Room Paging 3)
3. Coroutines для асинхронных операций
4. Remember и derivedStateOf для оптимизации Compose

---

## 📚 Зависимости (build.gradle.kts)

```kotlin
dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Coil (Images)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // DataStore (для настроек)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

---

## 🎨 UI/UX Guidelines

### **Material Design 3**
- Использование Material 3 компонентов
- Dynamic Colors (цвета адаптируются под обои пользователя на Android 12+)
- Следование Human Interface Guidelines

### **Анимации**
- Плавные переходы между экранами (Slide, Fade)
- Ripple эффекты на кнопках
- Smooth scroll в списках
- Loading индикаторы (CircularProgressIndicator)

### **Пустые состояния (Empty States)**
- Когда нет автомобилей: "Добавьте свой первый автомобиль"
- Когда нет заправок: "Запишите первую заправку"
- Иконка + текст + кнопка действия

### **Валидация**
- Подсветка обязательных полей красным
- Сообщения об ошибках под полями
- Disabled кнопка "Сохранить" до валидации

### **Доступность**
- Content Description для всех изображений/иконок
- Размер кликабельных элементов минимум 48dp
- Контрастные цвета (читаемость текста)

---

## 🚀 Следующие шаги после MVP

### **Фаза 2 (Post-MVP):**
1. **Синхронизация с облаком**
   - Firestore для хранения данных
   - Синхронизация между устройствами
   - Бэкап в облако

2. **Расширенная статистика**
   - Графики (MPAndroidChart или Vico)
   - Сравнение автомобилей
   - Экспорт в PDF/Excel

3. **Напоминания**
   - Напоминание о ТО (по пробегу/дате)
   - Напоминание о смене запчастей
   - WorkManager для фоновых задач

4. **Дополнительный функционал**
   - Документы автомобиля (СТС, ПТС, страховка)
   - История владельцев (если б/у авто)
   - Стоимость автомобиля (амортизация)

5. **Социальные функции**
   - Поделиться статистикой
   - Рекомендации сервисов
   - Отзывы на запчасти

---

## ✅ Чек-лист перед началом разработки

- [ ] Установить Android Studio (последняя версия)
- [ ] Установить JDK 17+
- [ ] Создать Firebase проект (console.firebase.google.com)
- [ ] Включить Firebase Authentication (Email/Password)
- [ ] Скачать `google-services.json`
- [ ] Изучить основы Kotlin (если нужно)
- [ ] Изучить основы Jetpack Compose (официальная документация Google)
- [ ] Пройти Compose Pathway на developer.android.com (рекомендую!)
- [ ] Создать репозиторий Git для проекта
- [ ] Прочитать про MVVM архитектуру

---

## 📖 Полезные ресурсы

### **Документация:**
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - официальная документация
- [Room Database](https://developer.android.com/training/data-storage/room) - работа с БД
- [Firebase Android](https://firebase.google.com/docs/android/setup) - интеграция Firebase
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) - DI framework

### **Курсы:**
- Android Basics with Compose (Google Codelabs)
- Kotlin Bootcamp for Programmers (Udacity - бесплатный)

### **YouTube каналы:**
- Philipp Lackner (Android + Compose)
- Coding in Flow (Android tutorials)

---

## 💡 Советы для начинающих

1. **Не пытайтесь выучить всё сразу** - начните с основ Compose и постепенно добавляйте сложность
2. **Используйте готовые компоненты** - Material 3 предоставляет 90% нужных UI элементов
3. **Commit чаще в Git** - сохраняйте прогресс после каждой завершённой фичи
4. **Тестируйте на реальном устройстве** - эмулятор не всегда точно работает с камерой/файлами
5. **Читайте документацию** - официальная документация Android очень качественная
6. **Не бойтесь ошибок** - Stack Overflow и ChatGPT помогут решить 99% проблем
7. **Делайте MVP маленькими шагами** - лучше сделать меньше, но качественно

---

## 📝 Заметки

- Этот документ - living document, обновляйте его по мере разработки
- Приоритеты могут меняться - главное сделать рабочий MVP
- Не стесняйтесь упрощать функционал на первом этапе
- Пользовательский опыт важнее количества фич

---

**Версия документа:** 1.0  
**Дата создания:** 20 декабря 2025  
**Автор:** GitHub Copilot  
**Статус:** MVP Planning

---

**Готовы начинать разработку? Следующий шаг - создание проекта в Android Studio! 🚀**
