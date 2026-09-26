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

## Translations

Wiki-Say-It! is translated on [translatewiki.net](https://translatewiki.net).
To help translate, sign up there — please don't send pull requests that
edit `app/src/main/res/values-<lang>/strings.xml`; those files are
exported from translatewiki.net and manual changes will be overwritten.

When changing user-facing text in code:

- **Edit only the English source**, `app/src/main/res/values/strings.xml`.
- **Document every new message** in `app/src/main/res/values-qq/strings.xml`
  (same key): where it appears, what each parameter is, and any length
  limits. Describe parameters in words — don't paste `%1$s`-style
  placeholders into that file, as lint checks them against the English.
- **Changing a message's meaning? Use a new key**, so translators see it
  as new work instead of the old translations silently going stale. Fixing
  a typo or wording without changing meaning can keep the key.
- **Use numbered placeholders** (`%1$s`, `%2$d`) whenever a message has
  more than one, since other languages may need a different order, and
  don't assemble sentences by concatenating separate strings.
- **Use `<plurals>`** for any message that includes a count.

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
