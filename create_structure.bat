@echo off
REM Скрипт для создания структуры проекта Car Log

echo Creating project structure...

REM Создание основных директорий
mkdir app\src\main\java\com\carlog\app 2>nul
mkdir app\src\main\res\values 2>nul
mkdir app\src\main\res\values-en 2>nul
mkdir app\src\main\res\drawable 2>nul
mkdir app\src\main\res\mipmap-hdpi 2>nul
mkdir app\src\main\res\mipmap-mdpi 2>nul
mkdir app\src\main\res\mipmap-xhdpi 2>nul
mkdir app\src\main\res\mipmap-xxhdpi 2>nul
mkdir app\src\main\res\mipmap-xxxhdpi 2>nul
mkdir app\src\test\java\com\carlog\app 2>nul
mkdir app\src\androidTest\java\com\carlog\app 2>nul

REM Создание структуры пакетов
mkdir app\src\main\java\com\carlog\app\data\local\dao 2>nul
mkdir app\src\main\java\com\carlog\app\data\local\entities 2>nul
mkdir app\src\main\java\com\carlog\app\data\repository 2>nul
mkdir app\src\main\java\com\carlog\app\domain\model 2>nul
mkdir app\src\main\java\com\carlog\app\domain\usecase 2>nul
mkdir app\src\main\java\com\carlog\app\di 2>nul
mkdir app\src\main\java\com\carlog\app\ui\theme 2>nul
mkdir app\src\main\java\com\carlog\app\ui\components 2>nul
mkdir app\src\main\java\com\carlog\app\ui\navigation 2>nul
mkdir app\src\main\java\com\carlog\app\ui\screens\carlist 2>nul
mkdir app\src\main\java\com\carlog\app\ui\screens\cardetail 2>nul
mkdir app\src\main\java\com\carlog\app\ui\screens\addcar 2>nul
mkdir app\src\main\java\com\carlog\app\util 2>nul

echo Project structure created successfully!
echo Now you can open the project in Android Studio.
pause
