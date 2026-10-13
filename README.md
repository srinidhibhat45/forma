# Forma — your health, in perspective

Forma is a **native Android phone app** built with Kotlin, Jetpack Compose and Material 3. The dense interface uses near-black surfaces, warm ivory typography, compact lime/orange/periwinkle activity rings, cyan vital signals, pink heart/body signals and deep-blue sleep views. Plus Jakarta Sans is bundled locally. Charts, rings, sparklines and activity timelines are drawn natively. Existing ChatGPT-generated artwork is retained in the repository, but the dashboard gives that space to health data. The React/Material UI files at the repository root are a design preview only; the Android project in `android/` is the actual phone application. There is no WebView in the Android app.

## Install on your phone

1. Download the [latest signed Forma APK](https://github.com/srinidhibhat45/forma/releases/latest/download/forma.apk) to your Android phone and open it to install. Install this updater-enabled build once, including over an existing Forma preview. After that, use **Health settings → App updates**. Android asks you to confirm installation; updates replace Forma in place and preserve its local records.
2. Open Forma. The initial dashboard contains clearly labeled sample data.
3. In Samsung Health, sync your Galaxy Watch6 Classic. Open **Samsung Health → Settings → Health Connect** and enable the records you want to share.
4. In Forma, open **Health settings → Your watch & connections → Allow access & connect**. Select the permissions you want to grant. Partial permissions are supported.
5. Once Samsung Health shares actual records, Forma replaces the demo. Recovery needs recorded sleep plus at least seven prior days of a matching heart-rate or HRV baseline. Body energy also needs hourly activity samples.
6. Optionally enable **Background refresh**. This separately requests Android’s background-read permission when the feature is available.

Health Connect runs on compatible Android phones. It is integrated into Android 14+; older supported phones may require the Health Connect app. Android 9 is the app’s minimum installation version, while Health Connect itself requires a supported provider and device. Actual watch/phone data and permissions must be tested on your Samsung phone; the emulator reader tests use synthetic SDK responses. Forma can be sideloaded on the Galaxy S24 Ultra without a Play Store listing, subject to Android/Samsung installation permissions and Auto Blocker.

## What works

- Today starts with animated steps, active-calorie and recorded-exercise rings, editable targets, amounts remaining, distance/floors and latest recorded heart rate with its timestamp. Recovery, sleep and body-energy estimates follow.
- Dedicated Sleep, Move, Trends and Health screens, with all 46 supported measurements grouped by health domain and reachable history views.
- Recorded workout history in 7/28/90/365-day ranges. Session details retain timing, distance/calories/steps, minute-average heart trace and observed BPM distribution, pace, speed and power when shared.
- Inspectable hourly activity, weekly goal completion and step streaks. Seven-night sleep duration, goal shortfall, bedtime/wake patterns, variation and overnight physiology.
- Robust personal baseline comparisons for HRV, resting/sleeping HR, breathing, oxygen and skin-temperature signals. Body readings retain explicit measurement dates when showing an older latest value.
- Samsung-shared nutrition energy, protein, carbs, fat, fiber, sugar, sodium and hydration, when present.
- Optional native Wear OS companion for 60-second foreground HR checks and batched daily activity. See [watch installation](docs/WATCH-COMPANION.md).
- Native chart inspection; 7-, 28- and 90-day histories for all supported metrics, a searchable metric browser; date selection.
- Sleep stage visualization, efficiency, recorded overnight oxygen/breathing/temperature.
- Steps, distance, active calories, recorded exercise time and floors when shared.
- HRV, latest/average/range/resting/sleeping heart rate, VO₂ max, speed/power, shared total calories, basal metabolic rate, height, body composition, weight, blood pressure and glucose when shared.
- Optional local journal, manual workouts and hydration in settings; none leads a main health page.
- Adjustable sleep, step, hydration and bedtime goals.
- A two-minute breathing exercise with an animated circle.
- Health Connect permission requests, foreground refresh and optional periodic background work.
- Forma v1 JSON import and health-record export through Android’s file picker.
- Private health storage, backup/transfer exclusions and local reset. No advertising or analytics. Optional watch transport uses the encrypted Wear OS Data Layer, which may use Google’s encrypted relay when connectivity changes; this is disclosed in setup.
- Automatic GitHub release checks, verified phone/watch APK downloads, progress/cancellation and Android installation. Updates require system confirmation. Watch installer behavior requires a physical Galaxy check; wireless-debugging update is the fallback.

## Data fidelity

Forma filters Health Connect to Samsung Health’s package (`com.sec.android.app.shealth`). Cumulative metrics use the aggregation API so multiple sources are not blindly added together. Samsung Health may aggregate phone and watch measurements; the app cannot guarantee that every shared measurement originated on the watch itself.

Resting HR and HRV are not guaranteed to be shared by Samsung. When recorded resting HR is absent, Forma can use **sleeping heart rate**, computed from actual heart samples during known sleep stages. It is explicitly labeled and never presented as Samsung’s resting HR. No HRV is inferred from simple heart-rate measurements. Sleep records without known asleep stages do not become invented time asleep. The primary nightly session is used for sleep scoring; known additional sessions are retained as nap minutes.

Health Connect does not expose every Samsung-only field, proprietary energy-score input, raw PPG signal or ECG waveform. For Samsung-only activity summaries and Samsung’s own energy score, the separate Samsung Health Data SDK requires production partnership/package/signature registration. Developer mode is documented by Samsung for testing/debugging only. That adapter is **not implemented**; the app does not claim Samsung proprietary score access. A future One UI Watch update alone does not guarantee additional app access.

Foreground refresh checks for changes around every minute while open, avoids rereading unchanged records, and reconciles historical corrections/deletions. Recent records appear first, followed by resumable batches of up to one year of shared history when older-history access is granted. Optional WorkManager refresh is scheduled around every 15 minutes; Android can delay it. Samsung Health must first sync the watch. This historical reader is separate from the new direct-watch companion. The latter supports explicit foreground HR checks and batched daily activity; per-metric timestamps and expiration prevent stale readings being labeled live.

## Estimates

The score model is transparent and experimental, not clinically validated. It is independent of WHOOP, Garmin Body Battery and Samsung algorithms.

- Sleep: 50% duration vs goal, 30% efficiency, 20% imported schedule regularity. Missing inputs normalize weights and reduce coverage. The native Health Connect reader does not manufacture regularity.
- Recovery: 60% sleep, 25% HRV vs personal baseline, 15% resting/sleeping HR vs personal baseline. Sleep and at least one baseline vital are required; baselines require seven distinct prior days and use robust medians/variability from up to 28 prior days, with logarithmic HRV comparisons. “Moderate” describes available coverage, not validated accuracy.
- Body energy: starts at recovery, then responds to recorded hourly steps, exercise minutes and heart-rate load. Recorded rest can replenish it when explicitly provided. Health Connect does not directly provide restful minutes, so the native reader never invents them. The strongest overlapping activity load is counted once; partial-hour heart load is scaled to duration. This is a rough model.
- Manual journal, hydration and workout entries do not artificially increase scores.

## Build

Open `android/` in Android Studio, using JDK 17 and Android SDK 36. The Gradle wrapper is included. `android/local.properties` is machine-specific and ignored; set your own SDK location.

```sh
cd android
./gradlew :app:assembleRelease :wear:assembleRelease :app:testDebugUnitTest :app:lintDebug :wear:lintDebug
ANDROID_SERIAL=PHONE_SERIAL ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=WATCH_SERIAL ./gradlew :wear:connectedDebugAndroidTest
```

The `release` variant is optimized and unsigned locally. GitHub Actions tests, builds and signs every push to `main`, then publishes both device APKs and separate update manifests. A dedicated Forma signing key is stored in encrypted repository secrets; no private key is committed. Public signing lineage allows the original preview to upgrade to the dedicated key. See [OTA updates](docs/OTA-UPDATES.md) for setup, signing, timing and recovery.

See [Watch connection and data fidelity](docs/WATCH-DATA.md) and [Galaxy sideload installation](docs/GALAXY-INSTALL.md) for setup, refresh behavior and security restrictions. Actual granted/denied/partial permissions and Samsung data availability still require testing on your phone. The wellness algorithms remain experimental.

The React design preview can be run with `npm install` and `npm run dev`; it has file imports and local interactions, but no direct Android data access.

## Verification

- Native APK assembly and optimization: passed.
- Native score, data-quality, rings, persistence, sleep/activity analytics, watch-protocol and update-policy tests: 48 passed.
- 23 native screen/interaction/reader-fixture checks passed on an isolated Pixel 6 / Android 14 emulator. Four companion checks passed on a separate round Wear OS emulator, including a Health Services synthetic HR callback. The separate opt-in live-update test is skipped by default on each device. Actual Samsung publishing remains a physical-device verification. Live release download and signing migration have separate integration probes.
- Original preview → dedicated signing key upgrade: passed; private files, preferences and original installation identity preserved.
- Android compatibility checks: passed with non-blocking dependency/style warnings.
- Actual Galaxy Watch and Samsung phone syncing: requires your device; not verified here.

See `docs/DESIGN.md` for the new visual system, `docs/DATA-CONTRACT.md` for the import schema and `docs/IMAGE-PROVENANCE.md` for the generated artwork.

Primary integration references: [Android Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types), [read and aggregate records](https://developer.android.com/health-and-fitness/health-connect/read-data), [Samsung Health Data SDK](https://developer.samsung.com/health/data/overview.html), and [Samsung app verification](https://developer.samsung.com/health/data/guide/app-verification.html).
