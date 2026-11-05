# Steady

Offline habit tracker, task manager and journal for Android (Kotlin, Jetpack Compose,
Material 3, Hilt, Room). See [ARCHITECTURE.md](ARCHITECTURE.md) for the design and phase plan.

## Requirements

- Android Studio with support for AGP 9.4 (Android SDK Platform 37 installed)
- JDK 17 or newer (the Gradle daemon may run on JDK 21)
- Gradle 9.6.0 — downloaded automatically by the wrapper

## Toolchain versions (gradle/libs.versions.toml)

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.4.0 (built-in Kotlin) |
| Kotlin | 2.3.20 |
| KSP | 2.3.9 |
| Compose BOM | 2026.09.00 |
| Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Navigation | 2.10.2 (type-safe routes) |
| Lifecycle | 2.11.0 |
| Room | 2.8.5 |
| WorkManager | 2.12.0 |
| DataStore | 1.3.0 |
| Glance | 1.2.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

## Build and test

```bash
./gradlew assembleDebug                 # build the APK
./gradlew installDebug                  # install on a connected device/emulator
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew connectedDebugAndroidTest     # instrumented UI tests (device required)
./gradlew lintDebug
```

If the wrapper JAR is ever missing, run `gradle wrapper --gradle-version 9.6.0` with any
local Gradle installation.

## Phase 1 — how to verify

1. Launch the app: the **Today** tab shows the weekday, a time-of-day greeting and the date.
2. Switch between the five bottom tabs; each tab keeps its own state and back stack.
3. Tap the settings icon on Today:
   - change **Theme** (System/Light/Dark) — the whole app and the system-bar icons switch immediately;
   - on Android 12+, toggle **Use wallpaper colors**;
   - pick a **First day of the week**;
   - enter **Your name** and tap Save — the Today greeting now includes it.
4. Kill and relaunch the app: all settings persist (DataStore).
5. Set the device language to Portuguese (Brasil): UI strings switch to pt-BR.
