# Contributing to Zerogram

First off, thank you for considering contributing to Zerogram! It's people like you that make open source great.

## Development Workflow

1. **Fork the repository** and clone it locally.
2. **Read the [Build Guide](docs/BUILD.md)** to set up your environment.
3. **Create a branch** for your feature or bug fix:
   - `feat/add-new-feature`
   - `fix/resolve-crash-bug`
   - `docs/update-readme`
   - `refactor/clean-up-code`
4. **Make your changes**. Ensure that you follow the existing architecture (Clean Architecture + MVVM) and code style.
5. **Run lint and tests**:
   ```bash
   ./gradlew lintDebug
   ./gradlew testDebugUnitTest
   ```
6. **Commit your changes** using descriptive commit messages.
7. **Push to your fork** and submit a Pull Request.

## Code Style
- We follow standard Kotlin conventions. 
- Please format your code using Android Studio's default Kotlin formatter before committing.
- Do not bypass `SecureLogger` by using `android.util.Log` directly in production code.

## Pull Requests
- Fill out the PR template completely.
- Keep PRs focused on a single issue or feature.
- If your PR introduces a UI change, please include screenshots.
