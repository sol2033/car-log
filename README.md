# 🚗 Car Log — Журнал обслуживания автомобиля

**Car Log** — нативное Android-приложение (offline-first) для комплексного учёта всех событий и
расходов, связанных с владением одним или несколькими автомобилями: запчасти, поломки/ремонты/ТО/
тюнинг, ДТП, расходники, заправки и прочие траты. Поверх данных строится подробная статистика.
Все данные хранятся **локально** (Room/SQLite + фото во внутреннем хранилище); есть опциональное
облачное резервное копирование на **Яндекс.Диск** и локальный экспорт/импорт.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.23-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-29-green.svg)](https://developer.android.com/about/versions/10)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

> Актуальная версия: **1.2.1** (versionCode 8) · minSdk 29 (Android 10) · targetSdk 34 (Android 14)

---

## ✨ Возможности

- **🚙 Несколько автомобилей** — все записи привязаны к конкретному авто; удаление машины
  каскадно удаляет её данные.
- **🔧 Запчасти** — установленные детали, отслеживание ресурса по пробегу, отметка «сломана» с
  автоматическим расчётом пройденного пробега, фильтры, фото.
- **⚙️ Поломки / ремонты / ТО / тюнинг** — полная история работ, гарантийные и платные, сервисные
  центры, разделение стоимости запчастей и работы, автосоздание расходников из ремонта, документы и фото.
- **🚨 ДТП** — учёт происшествий, роль «виновник / пострадавший», страховые выплаты (ОСАГО, КАСКО,
  от виновника), серьёзность повреждений, фото и PDF.
- **🧴 Расходники** — категории с настраиваемыми интервалами замены по пробегу и по времени,
  цветовая индикация состояния (норма / предупреждение / критично), история и статистика замен.
- **⛽ Заправки** — бензин/дизель/газ (пропан-метан)/электро, автоматический расчёт среднего
  расхода и стоимости километра.
- **💸 Прочие расходы** — мойка, аксессуары, автозвук, детейлинг, диски/шины и др. с категориями и заметками.
- **📑 Документы** — ОСАГО, КАСКО, транспортный налог (напоминание о следующем начислении) и
  свои типы: «светофор» по сроку действия, продление с историей цен, учёт в статистике
  («Страховка и налоги»).
- **📊 Статистика** — 5 вкладок (Общая / Топливо / Ремонты / Расходы / Расходники): графики по
  периодам, круговые диаграммы распределения, тренды расхода, фильтры по периоду и пробегу,
  исключение ДТП. Графики на **Vico** с компактными осями и легендами.
- **💾 Резервное копирование** — облачный бэкап на **Яндекс.Диск** (в т.ч. авто-бэкап через
  WorkManager) и локальный экспорт/импорт ZIP-архива (БД + фото + документы).
- **🎨 Интерфейс** — Material 3, тёмная/светлая/системная тема, локализация **ru/en**, валюты
  ₽ RUB · $ USD · € EUR · ₸ KZT · Br BYN, плавные анимации навигации.

---

## 🛠 Технологический стек

| Категория | Технология |
|-----------|-----------|
| Язык | Kotlin 1.9.23 (JVM target 17) |
| UI | Jetpack Compose (BOM 2024.02.00), Material 3, material-icons-extended |
| Архитектура | MVVM + лёгкий Clean Architecture (`data` / `domain` / `presentation`) |
| Навигация | navigation-compose 2.7.6 |
| DI | Hilt (Dagger) 2.50, hilt-navigation-compose, hilt-work |
| БД | Room 2.6.1 (через KSP), SQLite, DB version 20 |
| Асинхронность | kotlinx-coroutines 1.7.3 + Flow |
| Фоновые задачи | WorkManager 2.9.0 (авто-бэкап) |
| Сеть | OkHttp 4.12.0 (Яндекс.Диск REST API) |
| Изображения | Coil 2.5.0 |
| Графики | Vico 1.15.0 |
| Тесты | JUnit 4, kotlinx-coroutines-test, MockK 1.13.9, room-testing |
| Настройки | DataStore Preferences 1.0.0 |
| Сериализация | Gson 2.10.1 (Room TypeConverters) |
| Сборка | Gradle 8.5 (Kotlin DSL), AGP 8.3.1, KSP 1.9.23-1.0.20 |

Кодогенерация (Hilt, Room) — через **KSP**, не kapt.
Release-сборка минифицируется **R8** (`isMinifyEnabled` + `isShrinkResources`, правила в
`app/proguard-rules.pro`) — итоговый APK ≈ 4,5 МБ.

---

## 📁 Структура проекта

```
car-log/
├── app/
│   ├── src/main/
│   │   ├── java/com/carlog/
│   │   │   ├── CarLogApplication.kt      # @HiltAndroidApp
│   │   │   ├── MainActivity.kt           # единственная Activity, хостит NavHost
│   │   │   ├── data/                     # слой данных
│   │   │   │   ├── local/                # Room: Database, DAO, entity, миграции
│   │   │   │   ├── repository/           # реализации репозиториев
│   │   │   │   ├── preferences/          # DataStore (тема, язык, валюта, настройки)
│   │   │   │   └── backup/               # экспорт/импорт + Яндекс.Диск
│   │   │   ├── domain/                   # бизнес-логика
│   │   │   │   ├── model/                # модели (Car, Part, Consumable, ...)
│   │   │   │   └── repository/           # интерфейсы репозиториев
│   │   │   ├── presentation/             # UI-слой
│   │   │   │   ├── screens/              # экраны по фичам (parts, breakdowns,
│   │   │   │   │                         #   accidents, consumables, refuelings,
│   │   │   │   │                         #   expenses, statistics, settings, ...)
│   │   │   │   ├── navigation/           # Screen.kt + NavGraph.kt
│   │   │   │   ├── viewmodels/           # общие ViewModel
│   │   │   │   ├── theme/                # темы Material 3
│   │   │   │   └── util/                 # UI-утилиты
│   │   │   ├── di/                       # Hilt-модули
│   │   │   └── util/                     # форматирование, конвертеры, бэкап БД
│   │   └── res/
│   │       ├── values/                   # русская локализация
│   │       └── values-en/                # английская локализация
│   ├── schemas/                          # экспорт схем Room (история для миграций)
│   └── build.gradle.kts                  # конфигурация модуля app
├── docs/                                 # подробная документация
│   ├── TECHNICAL_DOCUMENTATION.md        # архитектура и структура кода
│   └── BUSINESS_LOGIC.md                 # бизнес-правила и функционал
├── gradle/                               # Gradle wrapper
└── README.md
```

Подробности — в [docs/TECHNICAL_DOCUMENTATION.md](docs/TECHNICAL_DOCUMENTATION.md) и
[docs/BUSINESS_LOGIC.md](docs/BUSINESS_LOGIC.md).

---

## 📱 Установка готового APK

> Для тех, кто просто хочет пользоваться приложением.

1. Скачайте `app-release.apk` со страницы [Releases](../../releases).
2. В настройках Android разрешите установку из неизвестных источников (для браузера/файлового менеджера).
3. Откройте скачанный файл и нажмите **Установить**.

Минимальная версия — **Android 10 (API 29)**.

---

## 🧑‍💻 Сборка из исходников

### Требования
- **Android Studio** Hedgehog (2023.1.1) или новее
- **JDK 17**
- **Android SDK** (API 34)
- Gradle 8.5 — поставляется с проектом через wrapper

### Клонирование

```bash
git clone https://github.com/sol2033/car-log.git
cd car-log
```

Дальше можно открыть проект в Android Studio (**File → Open**, дождаться синхронизации Gradle)
либо собирать из терминала.

### Debug-сборка (быстрый старт, без подписи)

```bash
# собрать debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# или установить на подключённое устройство/эмулятор
./gradlew installDebug
```

> На Windows используйте `gradlew.bat` вместо `./gradlew`.

### Release-сборка (подписанный APK для магазина)

Релизная сборка подписывается ключом, описанным в `keystore.properties` в корне проекта (файл
не коммитится). Создайте его рядом с `gradlew`:

```properties
storeFile=car-log.jks
storePassword=ВАШ_ПАРОЛЬ_ХРАНИЛИЩА
keyAlias=ВАШ_АЛИАС
keyPassword=ВАШ_ПАРОЛЬ_КЛЮЧА
```

Если своего ключа нет — сгенерируйте хранилище (положите `.jks` в корень проекта):

```bash
keytool -genkeypair -v -keystore car-log.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias car-log-key
```

Затем соберите:

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

> Если `keystore.properties` отсутствует, сборка пройдёт, но APK будет **неподписанным** —
> установить такой на устройство не получится.

Для публикации в Google Play вместо APK собирается App Bundle:

```bash
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

### Тесты

```bash
./gradlew testDebugUnitTest        # JVM-тесты: логика статусов, дат, расхода топлива, статистики
./gradlew connectedDebugAndroidTest # миграции БД, нужен подключённый телефон или эмулятор
```

Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

**Новая логика добавляется вместе с тестами** — требование и таблица «что чем покрывать»
в [docs/TECHNICAL_DOCUMENTATION.md](docs/TECHNICAL_DOCUMENTATION.md), §15.1.

---

## 📈 Версионирование

Источник истины по версии — [app/build.gradle.kts](app/build.gradle.kts) (`versionCode` / `versionName`).
Версия также отображается на экране **Настройки → О приложении**.

---

## 📄 Лицензия

Проект распространяется под лицензией **Apache License 2.0** — см. [LICENSE](LICENSE).

---

## 📧 Контакты

**Разработчик:** Титов Дмитрий (sol2033)
**Email:** sol2033@yandex.ru
**GitHub:** [@sol2033](https://github.com/sol2033)
**Drive2:** [sol2033](https://www.drive2.ru/users/sol2033/)

---

<div align="center">

**Сделано с ❤️ для автолюбителей**

⭐ Поставьте звезду, если проект вам понравился!

</div>
