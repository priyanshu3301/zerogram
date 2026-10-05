# Architecture

Zerogram is designed using **Clean Architecture** principles and is modularized by feature.

## Module Structure

The project is split into several Gradle modules to enforce separation of concerns and speed up compilation:

- **`:app`**: The main entry point. Contains the Navigation graph, Dependency Injection wiring (Hilt), the foreground `TransferService`, and UI for unlocking the vault.
- **`:core-tdlib`**: Encapsulates all interactions with the Telegram API. Provides domain interfaces (`ITelegramRepository`), data models, and manages Telegram authentication (`CredentialsManager`).
- **`:core-data`**: Contains the local Room database, DAOs, and the Tink cryptography implementation (`CryptoManager`, `VaultManagerImpl`).
- **`:core-ui`**: Shared Jetpack Compose UI components, themes, typography, and utility classes used across feature modules.
- **`:tdlib`**: A precompiled library module containing the `libtdjni.so` native libraries for TDLib and the generated Java bindings.

### Feature Modules
- **`:feature-home`**: Dashboard displaying category statistics and quick actions.
- **`:feature-folder`**: File and folder browser, recursive folder copy/move operations, and recently deleted view.
- **`:feature-category`**: Smart views aggregating files by type (Photos, Videos, Audio, Documents).
- **`:feature-search`**: Full-text search engine spanning all files and folders.
- **`:feature-transfers`**: Live status of active and queued uploads/downloads.

## Data Flow

Zerogram uses an MVI/MVVM pattern with Jetpack Compose:
1. **UI Layer**: Compose screens collect state from ViewModels using `StateFlow` and dispatch Intent actions.
2. **ViewModel Layer**: Uses Coroutines to orchestrate background operations, interact with Repositories, and map domain results to UI state.
3. **Data Layer**: Repositories abstract the source of truth, combining local database cache (Room) with remote operations (TDLib/Telegram) and encryption (Tink).

## The Transfer Service
To ensure uploads and downloads survive configuration changes and app backgrounding, all transfers are delegated to `TransferService` (a Foreground Service) in the `:app` module. The service communicates with the database to dequeue jobs and process them sequentially or in parallel depending on network conditions.
