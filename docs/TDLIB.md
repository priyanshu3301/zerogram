# TDLib Integration

Zerogram uses the **Telegram Database Library (TDLib)** to communicate with Telegram's servers. TDLib handles the MTProto protocol, encryption, local caching, and connection management automatically.

## Prebuilt Binaries

To simplify the build process for contributors, this repository contains precompiled `libtdjni.so` native libraries for all 4 Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) inside the `:tdlib` module.

The Java bindings (`TdApi.java` and `Client.java`) are also included in the same module.

## Updating TDLib

If you need to update TDLib to a newer version to support new Telegram API features:

1. Clone the official TDLib repository: `git clone https://github.com/tdlib/td.git`
2. Follow their [Android Build Instructions](https://github.com/tdlib/td/tree/master/example/android) to compile the `.so` files using the Android NDK.
3. Replace the `.so` files in `tdlib/libs/` with your newly compiled binaries.
4. Generate the new Java bindings and replace `TdApi.java` and `Client.java` in `tdlib/src/main/java/org/drinkless/tdlib/`.

## Authentication Flow

Zerogram authenticates as a standard Telegram client (not a bot).
1. `TDLibClient` dispatches a `setTdlibParameters` request.
2. It sends `setAuthenticationPhoneNumber`.
3. The user inputs the code sent via Telegram (or SMS) via `checkAuthenticationCode`.
4. Once authenticated, TDLib manages the session state locally in `context.filesDir/tdlib/`.

*Note: The TDLib session data is highly sensitive and is excluded from Android Auto Backup.*
