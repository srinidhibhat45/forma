# Forma APK updates

Every successful push to `main` in [srinidhibhat45/forma](https://github.com/srinidhibhat45/forma) runs the native unit tests and Android lint, builds an optimized APK, signs it with Forma’s dedicated key, and publishes a GitHub Release. A failed check publishes no update. Changes on other branches are published after they reach `main`. You can also run the workflow manually.

## Phone experience

Install the [latest APK](https://github.com/srinidhibhat45/forma/releases/latest/download/forma.apk) once to get the updater. Existing Forma previews can be upgraded in place. Open **You → App updates** to check immediately. Available releases also appear on Today.

Automatic checks are enabled initially. While the app is open, checks are limited to once per 15 minutes; a background check is scheduled every six hours. Android may defer background work. You can turn automatic checks off and continue using manual checks. A push must finish building and publishing before the phone can discover it.

Tap **Download & update**. Forma verifies the size, SHA-256, package identity, Android version code and signing identity before passing the APK to Android’s installer. If necessary, Android first asks you to allow installations from Forma. Confirm the installation to replace the existing app. Your private health records, goals and journal remain in the app’s storage. Do not uninstall Forma to update it.

These are full native APK updates. Android requires installation confirmation; repository pushes cannot silently replace native code on an ordinary phone. Forma does not inject downloaded JavaScript or use a WebView. Downloads are initiated by you; background checks only discover versions.

## Release identity

The app reads the public GitHub latest-release API. Each release includes exactly `forma.apk`, `update.json`, and `forma.apk.sha256`. The manifest contains schema version 1, package `com.forma.health`, version code/name, APK byte size and SHA-256. Asset links must belong to this repository. HTTPS redirects are restricted to GitHub’s release hosts; APKs are limited to 100 MB. Only increasing Android version codes are accepted.

`scripts/next-version.py` reads the previous published manifest and increases its version code. The first automatic release is build 4; local updater development starts at build 3. Workflow reruns also receive a fresh build code. Releases use tags `build-N`. Version name is currently `1.2.0`; change the script’s version name for a named milestone. Builds are serialized so concurrent pushes cannot choose the same version.

## Signing and continuity

Three encrypted GitHub Actions secrets are configured: `FORMA_KEYSTORE_BASE64`, `FORMA_STORE_PASSWORD`, and `FORMA_KEY_PASSWORD`. They hold a dedicated PKCS12 keystore, its password and the key password. Alias is `forma`. Actions decode the key into runner temporary storage and remove it after signing. The existing machine-wide Android debug private key is never uploaded.

`android/signing/forma.lineage` is a public, cryptographically signed certificate-rotation record from the original preview key to the dedicated Forma key. APKs use v3 signing with rotation support from Android 9, the app’s minimum version. Android can therefore retain the package and its data through this signing transition. Future builds must keep this key and lineage. Replacing the key without a valid rotation breaks upgrades.

A private local backup is in `.local-signing/forma-release.p12` and `.local-signing/password.txt`, excluded from Git and source archives. Back these up securely outside the repository. Never commit them or share them publicly. GitHub secrets cannot be downloaded later, so losing both the local backup and secrets can prevent signing future updates.

The Gradle release output is unsigned. `scripts/sign-apk.sh` signs it using `ANDROID_HOME`, `FORMA_KEYSTORE`, `FORMA_STORE_PASSWORD`, and `FORMA_KEY_PASSWORD` from the environment. `scripts/release-manifest.py` checks the signed APK’s actual identity before writing metadata. Do not manually publish a mismatched APK or edit manifest hashes.

## Local checks

Use JDK 17 and Android SDK 36:

```sh
cd android
./gradlew assembleRelease testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The opt-in `AppUpdateIntegrationTest` downloads the published release and checks its digest and signing identity against the installed build. Run it on a development build older than the published release with the `liveUpdateTest=true` instrumentation argument. `UpdateMigrationTest` writes and reads synthetic private-storage probes across an actual package replacement; enable with `migrationProbe=true` and run the write/read methods on the corresponding sides of the upgrade.

## Privacy and failure handling

The new Internet permission is used for version metadata and APK downloads only. Health records, journal entries, account credentials and watch measurements are never included in those requests. No GitHub authentication token is embedded in the APK. Health Connect permission handling stays separate. The installer has access only to the APK cache through a restricted FileProvider.

A network failure leaves the installed app usable. Interrupted downloads can be retried. Failed integrity/signature checks cannot launch the installer. Disabling automatic checks cancels the background update job. If GitHub is rate-limiting the phone, wait and retry manually. Failed builds are visible in the repository’s Actions page; resolve the failure and push again.
