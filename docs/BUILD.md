# Build Guide

This guide explains how to set up your environment and build Zerogram from source.

## Prerequisites

1. **JDK 17**: This project requires Java 17 to build. Ensure your `JAVA_HOME` is set correctly.
2. **Android SDK**: Install the Android SDK via Android Studio.
3. **Telegram API Credentials**: You must obtain your own API ID and API Hash from Telegram to authenticate the app.

### Obtaining Telegram API Credentials
1. Go to [my.telegram.org](https://my.telegram.org) and log in with your phone number.
2. Go to **API development tools**.
3. Create a new application (the details don't matter much).
4. Copy your **App api_id** and **App api_hash**.

## Configuration

1. Clone the repository:
   ```bash
   git clone https://github.com/priyanshu3301/zerogram.git
   cd zerogram
   ```
2. Copy the `local.properties.template` file to `local.properties`:
   ```bash
   cp local.properties.template local.properties
   ```
3. Open `local.properties` and add your Android SDK path.

*Note: The app will prompt you for your Telegram API credentials and phone number upon first launch.*

## Building

Zerogram uses a multi-module Gradle build. Note that the build requires at least 6GB of heap space (`-Xmx6G` is configured in `gradle.properties`).

**Build a Debug APK:**
```bash
./gradlew assembleDebug
```

**Build a Release APK:**
```bash
./gradlew assembleRelease
```
*Note: You must configure a valid release keystore in your environment to sign a release build properly.*

## TDLib Prebuilts
The project currently includes prebuilt TDLib binaries (`libtdjni.so`) in the `tdlib` module for convenience. See [TDLIB.md](TDLIB.md) for details on how to build them yourself if you wish to update the library.
