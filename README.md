# BTBOX-mz

**BTBOX-mz** — уникальное Android-приложение (с поддержкой Root/APatch/Magisk), которое превращает подключенный по USB/Type-C геймпад (например, GameSir X5 Lite) в полнофункциональный беспроводной **Bluetooth HID Gamepad** для ПК (Windows, Linux/SteamOS) и других хостов.

> 🤖 **Важное примечание:** 100% кода в этом репозитории написаны искусственным интеллектом (Gemini) под чутким и матерным руководством человека. Ни один программист при создании этого приложения не пострадал, если не считать сожженных нервных клеток в попытках подружить Android Bluetooth Stack с ядром Linux. Работает? Работает. Как? Не спрашивайте, нейронка сама в шоке, что это скомпилировалось и запустилось.

## 🚀 Возможности
- **Zero-Latency Bluetooth HID:** Использование низкоуровневого API `BluetoothHidDevice` с приоритетным потоком отправки отчетов (до 140–250 Гц) без накопления задержек.
- **Мультимедиа-контроль с экрана (Gesture Volume):** В режиме погашенного экрана свайп вверх/вниз по правой стороне дисплея удаленно регулирует системную громкость на ПК (эмуляция Consumer Control HID).
- **Двухрежимная таблица маппинга:**
  - *Windows DirectInput / GameSir Mode:* Нативная раскладка под Windows (`joy.cpl`) с выделенными цифровыми триггерами (кнопки 9 и 10).
  - *Linux / Debian / SteamOS Mode:* Полная совместимость с ядром Linux (`hid-generic` и SDL2) без сдвигов осей X/Y и бамперов.
- **Гибридные курки (Trigger Switch):** Переключение на лету между плавными аналоговыми осями (для гонок/симуляторов) и чистыми цифровыми кликами (для шутеров и калибровки Steam).
- **Защита от перебивания осей (Axis Decoupling):** Независимый кэш осей предотвращает сброс курков при одновременном отклонении стиков.
- **AMOLED Blackout Mode:** Автоматическое скрытие интерфейса и переход в глубокий черный экран (1% яркости) через 10 секунд бездействия для экономии батареи во время игры (пробуждение по тапу слева/по центру).
- **Root-модуль (опционально):** Подмена Class of Device (CoD) на `0x002508` (Peripheral/Gamepad) и поднятие системного приоритета (`renice`) службы Bluetooth.
- **Безопасный откат:** При выключении восстанавливает родное имя устройства и класс смартфона.

## 🛠 Требования
- Android 11+ (протестировано на Android 14–17)
- Подключенный по USB геймпад (GameSir X5 Lite, Razer Kishi, обычный проводной геймпад через OTG)
- Наличие Root (APatch / Magisk / KernelSU) — *опционально, для CoD-спуфинга*
## Структура проекта

```
BOXBT/
├─ settings.gradle.kts          # модули и репозитории
├─ build.gradle.kts             # AGP 9.4.1
├─ gradle.properties            # AndroidX, JVM-аргументы
├─ local.properties             # путь к Android SDK (не коммитится)
├─ gradlew / gradlew.bat        # Gradle wrapper 9.8.2
├─ gradle/wrapper/              # файлы wrapper'а
├─ legacy/                      # исходные .docx и старый манифест (архив)
└─ app/
   ├─ build.gradle.kts
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/root/xinputbtproxy/
      │  ├─ MainActivity.kt            # UI, приём событий геймпада
      │  ├─ GamepadProxyService.kt     # foreground-сервис, BluetoothHIDDevice
      │  ├─ XboxHidDescriptor.kt       # HID report descriptor
      │  └─ RootBtSpoofer.kt           # root-твики (su)
      └─ res/
         ├─ values/ (strings, colors, themes)
         ├─ drawable/ic_launcher_foreground.xml
         └─ mipmap-anydpi-v26/ic_launcher.xml
```

## Параметры сборки

| Параметр | Значение |
|---|---|
| applicationId | `com.root.xinputbtproxy` |
| minSdk | 28 (BluetoothHidDevice появился в API 28) |
| targetSdk / compileSdk | 37 |
| AGP / Gradle / JDK | 9.4.1 / 9.8.2 / 17+ (проверено на JDK 26) |

## Сборка

```powershell
# из папки E:\BOXBT
.\gradlew.bat :app:assembleDebug
```

Готовый APK: `app\build\outputs\apk\debug\app-debug.apk`
(подписан debug-ключом, ставится на устройство).

Установка:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Релизная сборка (потребуется свой keystore):
```powershell
.\gradlew.bat :app:assembleRelease
```

## Требования к устройству

- Android 9 (API 28) и выше, поддержка Bluetooth HID Device profile.
- Root (`su`) — используется для `setenforce 0`, `setprop class_of_device`
  и прав на `/dev/input/event*`. Без root сервис запустится, но HID-регистрация
  и захват ввода могут не работать.
- Разрешения Bluetooth (CONNECT / ADVERTISE / SCAN) и уведомления запрашиваются
  при старте `MainActivity`.
