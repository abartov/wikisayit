# Contributing to WikiSayIt

Thanks for considering a contribution! WikiSayIt is an Android app for
recording and contributing audio to Wikimedia projects. Pull requests are
welcome.

## Getting started

1. Fork the repo and clone your fork.
2. Open the project in Android Studio, or build from the command line with
   the included wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
3. Create a branch for your change: `git checkout -b my-fix`.

## Before you open a pull request

- **Add tests.** New behavior should come with unit tests
  (`app/src/test`) or instrumented tests (`app/src/androidTest`) as
  appropriate. Bug fixes should include a test that fails before the fix
  and passes after.
- **Run the test suite:**
  ```bash
  ./gradlew test
  ```
- **Run lint/formatting checks** (the project uses ktlint):
  ```bash
  ./gradlew ktlintCheck
  ```
  Fix formatting issues automatically with `./gradlew ktlintFormat`.
- **Keep changes focused.** Prefer small, reviewable pull requests over
  large ones that mix unrelated changes.
- **Write a clear commit message and PR description** explaining *why*
  the change is needed, not just what it does.

## Filing issues

Bug reports and feature requests are welcome. Please include steps to
reproduce, expected vs. actual behavior, and your device/Android version
for bugs.

## Code style

Follow the conventions already used in the surrounding code (Kotlin
idioms, existing package structure, naming). `ktlintCheck` enforces
formatting; there's no need to debate style in review.

## License

By contributing, you agree that your contributions will be licensed
under the project's [Apache License 2.0](LICENSE).
