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
