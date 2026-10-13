# Forma — your health, in perspective

Forma is a **native Android phone app** built with Kotlin, Jetpack Compose and Material 3. The redesigned interface uses near-black surfaces, warm ivory typography, an acid-lime energy instrument, deep-blue sleep views and orange movement meters. Plus Jakarta Sans is bundled locally. Charts and the segmented energy dial are drawn natively; the outdoor editorial image was created with ChatGPT Images. The React/Material UI files at the repository root are a design preview only; the Android project in `android/` is the actual phone application. There is no WebView in the Android app.

## Install on your phone

1. Download the [latest signed Forma APK](https://github.com/srinidhibhat45/forma/releases/latest/download/forma.apk) to your Android phone and open it to install. Install this updater-enabled build once, including over an existing Forma preview. After that, use **You → App updates**. Android asks you to confirm installation; updates replace Forma in place and preserve its local records.
2. Open Forma. The initial dashboard contains clearly labeled sample data.
3. In Samsung Health, sync your Galaxy Watch6 Classic. Open **Samsung Health → Settings → Health Connect** and enable the records you want to share.
4. In Forma, open **You → Your watch & connections → Grant Health Connect access & sync**. Select the permissions you want to grant. Partial permissions are supported.
5. Once Samsung Health shares actual records, Forma replaces the demo. Recovery needs recorded sleep plus at least one personal heart-rate or HRV baseline. Body energy also needs hourly activity samples.
6. Optionally enable **Background refresh**. This separately requests Android’s background-read permission when the feature is available.

Health Connect runs on compatible Android phones. It is integrated into Android 14+; older supported phones may require the Health Connect app. Android 9 is the app’s minimum installation version, while Health Connect itself requires a supported provider and device. Actual watch/phone data and permissions must be tested on your Samsung phone; the emulator tests use synthetic records.

## What works

- Today dashboard with recovery, sleep score and day-long body-energy estimates.
- Dedicated Sleep, Move, Trends and Your Space screens, and a recovery detail view.
- Native chart inspection; 7- and 28-day metric histories; date selection.
- Sleep stage visualization, efficiency, recorded overnight oxygen/breathing/temperature.
- Steps, distance, active calories, recorded exercise time and floors when shared.
- HRV, resting and sleeping heart rate, body composition, weight, blood pressure and glucose when shared.
- Local journal check-ins, habits, mood, manually logged workouts and hydration.
- Adjustable sleep, step, hydration and bedtime goals.
- A two-minute breathing exercise with an animated circle.
- Health Connect permission requests, foreground refresh and optional periodic background work.
- Forma v1 JSON import and health-record export through Android’s file picker.
- Private on-device health storage, backup/transfer exclusions and local reset. No advertising SDK, analytics or health-data cloud upload.
- Automatic GitHub release checks, verified APK downloads, progress/cancellation and native Android installation. Internet access is used only for updates.

## Data fidelity

Forma filters Health Connect to Samsung Health’s package (`com.sec.android.app.shealth`). Cumulative metrics use the aggregation API so multiple sources are not blindly added together. Samsung Health may aggregate phone and watch measurements; the app cannot guarantee that every shared measurement originated on the watch itself.

Resting HR and HRV are not guaranteed to be shared by Samsung. When recorded resting HR is absent, Forma can use **sleeping heart rate**, computed from actual heart samples during known sleep stages. It is explicitly labeled and never presented as Samsung’s resting HR. No HRV is inferred from simple heart-rate measurements. Sleep records without known asleep stages do not become invented time asleep. The primary nightly session is used for sleep scoring; known additional sessions are retained as nap minutes.

Health Connect does not expose every Samsung-only field, proprietary energy-score input, raw PPG signal or ECG waveform. For richer access, a future Samsung Health Data SDK integration requires Samsung’s SDK and its applicable app-verification/developer-mode or partnership process. That adapter is **not implemented** in this preview. A future One UI Watch update alone does not guarantee additional app access.

Foreground sync runs around every five minutes while the app is open. Optional WorkManager refresh is scheduled around every 15 minutes; Android can delay it. Samsung Health must first sync the watch. Forma is not a continuous live sensor feed.

## Estimates

The score model is transparent and experimental, not clinically validated. It is independent of WHOOP, Garmin Body Battery and Samsung algorithms.

- Sleep: 50% duration vs goal, 30% efficiency, 20% imported schedule regularity. Missing inputs normalize weights and reduce coverage. The native Health Connect reader does not manufacture regularity.
- Recovery: 60% sleep, 25% HRV vs personal baseline, 15% resting/sleeping HR vs personal baseline. Sleep and at least one baseline vital are required; baselines use up to 28 prior days. “Moderate” describes available coverage, not validated accuracy.
- Body energy: starts at recovery, then responds to recorded hourly steps, exercise minutes and heart-rate load. Recorded rest can replenish it when explicitly provided. Health Connect does not directly provide restful minutes, so the native reader never invents them. Overlapping activity inputs can amplify load; this is a rough model.
- Manual journal, hydration and workout entries do not artificially increase scores.

## Build

Open `android/` in Android Studio, using JDK 17 and Android SDK 36. The Gradle wrapper is included. `android/local.properties` is machine-specific and ignored; set your own SDK location.

```sh
cd android
./gradlew assembleRelease testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The `release` variant is optimized and unsigned locally. GitHub Actions tests, builds and signs every push to `main`, then publishes the APK and update manifest. A dedicated Forma signing key is stored in encrypted repository secrets; no private key is committed. Public signing lineage allows the original preview to upgrade to the dedicated key. See [OTA updates](docs/OTA-UPDATES.md) for setup, signing, timing and recovery.

Actual granted/denied/partial permissions and Samsung data availability still require testing on your phone. The wellness algorithms remain experimental.

The React design preview can be run with `npm install` and `npm run dev`; it has file imports and local interactions, but no direct Android data access.

## Verification

- Native APK assembly and optimization: passed.
- Native score and update-policy tests: 17 passed.
- Nine native screen and interaction checks, including update controls: passed on an isolated Pixel 6 / Android 14 emulator. Live release download and signing migration have separate integration probes.
- Original preview → dedicated signing key upgrade: passed; private files, preferences and original installation identity preserved.
- Android compatibility checks: passed with non-blocking dependency/style warnings.
- Actual Galaxy Watch and Samsung phone syncing: requires your device; not verified here.

See `docs/DESIGN.md` for the new visual system, `docs/DATA-CONTRACT.md` for the import schema and `docs/IMAGE-PROVENANCE.md` for the generated artwork.

Primary integration references: [Android Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types), [read and aggregate records](https://developer.android.com/health-and-fitness/health-connect/read-data), [Samsung Health Data SDK](https://developer.samsung.com/health/data/overview.html), and [Samsung app verification](https://developer.samsung.com/health/data/guide/app-verification.html).
