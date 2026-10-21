# BOXBT — X5 Lite BT Proxy

Android-приложение, которое делает смартфон Bluetooth-HID-геймпадом и транслирует
ввод с подключённого контроллера (GameSir X5 Lite) на ПК, представляя его как
«Xbox Wireless Controller» (XInput-совместимый HID-дескриптор).

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

## Заметки по переносу кода

Код перенесён из `.docx` дословно, с минимальными правками для сборки:

- манифест: убран атрибут `package` (в AGP 8+ используется `namespace`);
- `minSdk` поднят до 28 — `BluetoothHidDevice` недоступен ниже;
- добавлено разрешение `POST_NOTIFICATIONS` и его запрос на Android 13+;
- строки уведомления вынесены в `res/values/strings.xml`;
- добавлен флаг `bound` перед `unbindService`, чтобы не падать при неподключённом сервисе;
- добавлены иконка (adaptive), тема `Theme.BoxBt` и `.gitignore`.
