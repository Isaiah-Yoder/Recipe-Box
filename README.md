# Recipe Box

Recipe Box is a personal Android app that saves recipes from links, notes, and
recipe card photos, scales them, and builds grocery lists. It has no server:
recipes stay on the phone, and backups go to the user's own Google Drive.

- [Homepage](https://isaiah-yoder.github.io/Recipe-Box/)
- [Privacy policy](https://isaiah-yoder.github.io/Recipe-Box/privacy.html)

## Build

To build the app, open this folder in Android Studio. Release builds are signed
only when `local.properties` points to a signing file outside the repository
with `recipebox.signing=PATH`. Never commit signing keys, passwords, or API
keys to this repository.

## Project layout

The project has the following modules:

- `core`: the logic that doesn't need Android, such as reading recipe pages
  and cards, parsing and scaling ingredients, tagging, and building grocery
  lists. It's a plain Kotlin module, so its tests run on the computer with
  `./gradlew :core:test`.
- `app`: the Android app, with its database, screens, backups, and updates.

## Release

The app updates itself from this repository's GitHub releases. When it opens,
it reads the latest release and offers an update when that release's version
is higher than the installed one. To publish a release so the app finds it,
follow these rules:

- Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
- Tag the release `vX.Y.Z`, matching `versionName`.
- Attach exactly one APK, signed with the release key, named
  `recipe-box-X.Y.Z.apk`.
- Write the release notes for the person using the app. The app shows them
  under **What's new**.
- Don't mark the release as a draft or prerelease; the app skips both.
