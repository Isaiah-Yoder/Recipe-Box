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
- An optional Gemini API key that you enter yourself

## Network connections

Recipe Box connects to other services only for the following tasks:

- **Importing a recipe.** The app downloads the web page and photo from the
  link you import, as a web browser would.
- **Google Drive backup.** If you turn on backups, the app asks for permission
  to create and manage only the files it creates in your Google Drive (the
  `drive.file` scope). It writes backup files to a "Recipe Box backups"
  folder. It can't see or open your other Drive files. The backups are stored
  in your Google account, and the developer can't access them.
- **Optional cloud AI.** If you enter your own Gemini API key, the app sends
  the recipe text or photo that needs help to Google's Gemini API. Google's
  terms for that key apply, including how Google uses free-tier content.
  On-device AI runs on the phone and sends nothing.

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
