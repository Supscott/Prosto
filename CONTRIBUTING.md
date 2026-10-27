# Contributing to Prosto Browser

First off, thank you for considering contributing to Prosto! Projects like this thrive on open collaboration.

## 📜 Code of Conduct

This project adheres to the Contributor Covenant [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## 🛠️ Development Setup

1. Fork the repository and clone your fork locally:
   ```bash
   git clone https://github.com/<your-username>/browser.git
   cd browser
   ```
2. Open the project in **Android Studio Ladybug (or newer)**.
3. Ensure JDK 17 or JDK 23 is configured as your Gradle JDK.
4. Run `./gradlew assembleDebug` to verify that everything builds cleanly.

## 🧪 Testing Guidelines

Before opening a pull request, ensure all tests pass:

```bash
./gradlew testDebugUnitTest
```

If you add new features or fix bugs:
- Add JVM unit tests under `app/src/test/java/com/jestk/browser/`.
- If modifying UI composables, update or add visual preview tests (using Robolectric Native Graphics).
- Adhere to the **Prosto Canvas** design system:
  - **Strictly NO BLUR**: never use backdrop blur shaders or frosted glass effects.
  - Respect bottom gesture safe areas and Android system bars.
  - Keep APK footprint minimal and performant.

## 🌿 Branching & Git Workflow

- Create a feature branch from `main`:
  ```bash
  git checkout -b feature/my-new-feature
  ```
- Use Conventional Commits for commit messages:
  - `feat(dock): add haptic feedback on tab spring release`
  - `fix(adblock): handle relative URL cosmetic collapsing`
  - `docs: update build prerequisites in README`
- Open a Pull Request targeting `main`. Provide a clear description and screenshots/GIFs of UI changes.

## 🐛 Reporting Bugs

- Search existing [Issues](https://github.com/Supscott/Prosto/issues) to ensure the bug hasn't already been reported.
- If not, open a new issue using our **Bug Report Template** and include:
  - Android version and device model.
  - Steps to reproduce.
  - Expected vs actual behavior.
  - Logcat or screenshots if applicable.

Thank you for helping build a simpler, faster mobile browser!
