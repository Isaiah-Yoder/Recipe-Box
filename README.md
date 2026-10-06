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
- `app`: the Android app, with its database, screens, and backups.

The app has two build flavors. Code that only one flavor needs lives in its
source set, `app/src/github` or `app/src/play`, and each flavor's
`BuildFlavor` object supplies it to the shared code:

- `github`: the build that's released. It updates itself from this
  repository's GitHub releases and can read recipe cards with the user's own
  Gemini key.
- `play`: a build for Google Play. Google Play installs its updates, so it has
  no updater and no permission to install apps. It reads recipe cards on the
  phone.

Every text the app shows comes from string resources, so the app can be
translated. Each area has its own file in `app/src/main/res/values/`, such as
`strings_library.xml`; the updater's text is in the `github` flavor's
resources. Code without a `Context` gets text through `AppStrings`.

Recipe sources, such as web pages and recipe cards, each produce a
`RecipeDraft`, and `RecipeRepository` saves every new recipe the same way.
Recipes, categories, and grocery lists have permanent IDs, change times, and
deletion records, so libraries can be merged later.

## Release

The app updates itself from this repository's GitHub releases. When it opens,
it reads the latest release and offers an update when that release's version
is higher than the installed one. To publish a release so the app finds it,
follow these rules:

- Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
- Build the GitHub flavor with `./gradlew :app:assembleGithubRelease`. The APK
  is in `app/build/outputs/apk/github/release/`.
- Tag the release `vX.Y.Z`, matching `versionName`.
- Attach exactly one APK, signed with the release key, named
  `recipe-box-X.Y.Z.apk`.
- Write the release notes for the person using the app. The app shows them
  under **What's new**.
- Don't mark the release as a draft or prerelease; the app skips both.
