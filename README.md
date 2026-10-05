# Zerogram

Zerogram is a privacy-focused Android application that uses a private Telegram channel as remote encrypted file storage. It functions as a secure vault, encrypting files locally on your device before uploading them to Telegram, ensuring that your data is private, secure, and accessible only by you.

## Features
- **End-to-End Encryption**: All files are encrypted locally using Google Tink (AES-256-GCM-HKDF) before leaving your device.
- **Telegram as Storage**: Utilizes Telegram's infrastructure for limitless, secure cloud storage via TDLib.
- **No Third-Party Servers**: Communicates directly with Telegram. We don't host any middleware servers that can access your data.
- **Secure Vault**: Protected by a master password/key.

## Getting Started

### Prerequisites
To build and run Zerogram, you will need:
- JDK 17
- Android Studio (latest stable recommended)
- Your own Telegram API ID and Hash (obtained from [my.telegram.org](https://my.telegram.org))

### Building the App
For detailed, step-by-step instructions on how to set up your environment, configure your API keys, and build the project, please read the [Build Guide](docs/BUILD.md).

## Architecture & Documentation
Zerogram is built using modern Android development practices:
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: Clean Architecture / Multi-module
- **Dependency Injection**: Hilt
- **Database**: Room
- **Cryptography**: Google Tink

For deeper technical details, refer to the documentation:
- [Architecture](docs/ARCHITECTURE.md)
- [Encryption Model](docs/ENCRYPTION.md)
- [Database Schema](docs/DATABASE.md)
- [TDLib Integration](docs/TDLIB.md)

## Contributing
We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to set up your environment, our branching conventions, and the pull request process.

## Security
If you discover a security vulnerability, please refer to [SECURITY.md](SECURITY.md) for our responsible disclosure guidelines. Do not open public issues for security vulnerabilities.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

TDLib binaries and bindings are distributed under the Boost Software License 1.0. See [THIRD_PARTY.md](THIRD_PARTY.md) for all attributions.
