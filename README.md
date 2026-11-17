# TimeSheetPro

Android-приложение для учёта рабочего времени, заказчиков и выставления счетов. Kotlin, Jetpack Compose, Room; интерфейс на Material 3 Expressive.

## Проверить и посмотреть на Windows

1. Запустите `Verify.cmd`: сборка APK, unit/screenshot-тесты и Lint. Журнал — `.work/verify.log`.
2. Запустите `Preview.cmd`: сборка и запуск настоящего приложения в отдельном Pixel 9 AVD.

Требуются JDK 21, SDK Platform 37.0, Build Tools 36.0.0, Platform Tools, Emulator и образ Android 36 Google APIs x86_64. Скрипты находят подготовленные локальные инструменты в `.work`; на другом компьютере задайте `JAVA_HOME` и `ANDROID_HOME`.

Предварительный APK: `app/build/outputs/apk/debug/app-debug.apk`, пакет `com.example.preview`. Его данные отделены от рабочего приложения.

## Документация

- [Дизайн-система](docs/DESIGN_SYSTEM.md)
- [Проверки, измерения и безопасное обновление](docs/UPDATE_AND_VERIFICATION.md)
- [Текущий статус проверок](docs/VERIFICATION_STATUS.md)

Рабочее обновление сохраняет пакет `com.example` и требует совместимого исходного ключа подписи. Не удаляйте установленное приложение для обхода несовпадения подписей.
