# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Core architecture (Clean Architecture, MVVM) with Hilt for dependency injection.
- Complete TDLib integration using native JNI bindings.
- Tink AES-256-GCM-HKDF streaming encryption for secure, zero-knowledge file uploads and downloads.
- Room database for local metadata caching and file tree structure (Migrations V1 -> V5).
- Jetpack Compose UI (Material 3) featuring:
  - File/Folder browser
  - Category views (Photos, Videos, Audio, Documents)
  - Full-text search engine
  - Active transfers management and live progress indicators
- Foreground `TransferService` for reliable, background-resilient file transfers.

### Security
- Master Vault Key securely managed via Android Keystore and `EncryptedSharedPreferences`.
- Disabled Android auto-backup to prevent credential leakage.
- Removed debug keystore signing from the release configuration.
