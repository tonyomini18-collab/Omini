# Auto-updates for Omini POS (Windows + Android)

## Releasing an update (every time)

1. Commit and push your changes to `main`.
2. Create and push a version tag:
   ```
   git tag v1.0.1
   git push origin v1.0.1
   ```
   (Or open GitHub → Actions → "Release (Windows + Android)" → Run workflow, and type `1.0.1`.
   That form also lets you write the update notes and mark the update as mandatory for Android.)
3. Wait about 10-15 minutes. The workflow builds both apps and creates a GitHub Release with:
   - `Omini-POS-Setup-1.0.1.exe`, `.blockmap`, `latest.yml`  (Windows)
   - `Omini-POS-1.0.1.apk` and `android-version.json`        (Android)

Use a higher number each time. The version number decides what counts as "newer".

## What the apps do

**Windows:** checks 15 seconds after start and every 2 hours, downloads in the background, and installs
when the app closes. When the business day is closed and the cart is empty it also offers "Restart & install".
It never restarts in the middle of a day or a sale. No internet is fine: the check just fails quietly.

**Android:** checks at start and every hour. If a newer version exists and the cart is empty, a dialog appears.
"Download & Install" downloads the APK inside the app, verifies its SHA-256, and opens the Android installer.
Local data stays because it is a normal in-place update.
The first time, Android asks you to allow installs from Omini POS. Allow it once.

## One-time setup

1. **Repository must be public.** Both apps read the update files from GitHub Releases without a login.
   (Private repo: tell me and I will switch to a different host.)
2. **Android signing key** (needed for the APK step; Windows works without it):
   ```
   brew install gh
   gh auth login
   ./scripts/create-android-keystore.sh
   ```
   This creates `omini-upload-key.jks` and saves 3 secrets in GitHub. **Back up the `.jks` file and the password.**
   Every future APK must be signed with this same key, or phones will refuse the update.
   Until the secrets exist, releases still publish the Windows installer and just skip the APK.
3. **First install of each app must be a build from this setup.** Old builds do not contain the update code
   Install the first Windows and Android builds from the first release you make with this package.
   An Android phone that has the AI Studio build installed has a different signing key, so install the GitHub APK
   once (uninstall first; data comes back from Firebase sync) and update normally after that.

## Testing it

Release `v1.0.0`, install both apps, release `v1.0.1`, and open the old apps. Windows downloads quietly and
asks to restart when idle. Android shows the update dialog.

## Files changed

- `pc-desktop/package.json`: GitHub publish settings, `--publish never` build.
- `pc-desktop/src/electron/main.js`: safer updater (error handler, idle-only restart, silent install).
- `pc-desktop/scripts/ensure-latest-yml.js`: guarantees `latest.yml` exists.
- `app/.../data/update/GithubUpdater.kt` (new), `StoreViewModel.kt`, `MainScaffold.kt`: Android update check and installer.
- `app/build.gradle.kts`: version comes from the release tag. `AndroidManifest.xml`: install permission.
- `.github/workflows/release.yml` (new), `build-windows-exe.yml` (no longer publishes releases).
