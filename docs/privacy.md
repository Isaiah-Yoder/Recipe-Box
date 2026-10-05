---
title: Recipe Box privacy policy
---

# Recipe Box privacy policy

Effective October 5, 2026.

Recipe Box stores your recipes, grocery lists, and settings on your phone. The
developer has no server and receives none of your data. The app contains no
analytics, advertising, or tracking.

## Data the app stores

Recipe Box stores the following data on your phone:

- Recipes you import or type, including their source links and cover photos
- Photos you take or choose, such as photos of recipe cards
- Categories, tags, notes, grocery lists, and settings
- The recipe part of each imported web page, so the app can read the recipe
  again after an update without downloading it. The app keeps these copies
  out of its backup files.
- An optional Gemini API key that you enter yourself. It stays on your
  phone: the app keeps it out of its own backup files and out of Android's
  automatic backup.

## Network connections

Recipe Box connects to other services only for the following tasks:

- **Importing or refreshing a recipe.** The app downloads the web page and
  photo from the link you import, as a web browser would. **Refresh recipes**
  in Settings downloads each saved recipe's page again in the same way. After
  an update that changes how the app reads recipes, the app downloads the
  pages it hasn't saved a copy of, on Wi-Fi only.
- **Google Drive backup.** If you turn on backups, the app asks for permission
  to create and manage only the files it creates in your Google Drive (the
  `drive.file` scope). It writes backup files to a "Recipe Box backups"
  folder. It can't see or open your other Drive files. The backups are stored
  in your Google account, and the developer can't access them.
- **Checking for updates.** When you open the app, it asks GitHub at most
  once an hour whether a newer Recipe Box release exists, and it downloads the
  update from GitHub when you tap **Update**. The request contains no recipe or
  account data. GitHub sees your phone's IP address, as it would for any
  download.
- **Optional cloud AI.** If you enter your own Gemini API key, the app sends
  the recipe card photos you ask it to read to Google's Gemini API. Google's
  terms for that key apply. On Gemini's free tier, Google may use what you
  send to improve its products.
- **On-device reading.** Without a key, or when Gemini isn't available, the
  app reads cards on the phone with Google's on-device AI or ML Kit text
  recognition. The photos stay on the phone. Google Play services downloads
  and updates these models, and Google's
  [ML Kit terms](https://developers.google.com/ml-kit/terms) describe the
  usage information ML Kit may send to Google.

## Google user data

Recipe Box uses Google user data only to save and restore your own backups in
your own Google Drive. It doesn't transfer that data to anyone else, use it for
advertising, or use it to train AI models. Recipe Box's use of information
received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

## Deleting your data

To delete the data on your phone, uninstall Recipe Box or clear its storage in
Android settings. To delete backups, delete the "Recipe Box backups" folder in
Google Drive. To remove the app's Drive access, open your Google Account, go to
**Security > Your connections to third-party apps and services**, and remove
Recipe Box.

## Contact

To ask a question about this policy, open an issue in the
[Recipe Box repository](https://github.com/Isaiah-Yoder/Recipe-Box/issues).
