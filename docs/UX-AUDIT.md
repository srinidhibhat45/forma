# Forma native UX audit — 1.5.0

Scope: every reachable phone screen, action, user input and supporting UI function; the watch companion and data/update plumbing were also traced. This audit addresses the native Android app. External app screens, real Samsung publication cadence and a physical Galaxy installer remain device-dependent checks.

## Decisions

- The header source badge is read-only. The watch icon opens **Devices**.
- **Your data** is a separate settings destination: recorded-day count, populated measurements, availability, export, optional import and local reset.
- Devices leads with connection state and one primary action. Sync settings and troubleshooting are expandable. Import/export and metric-coverage reports were removed from this flow.
- Score sheets lead with the score and contributions. Formulas, diagnostics and copying are expandable. Timestamp and unavailable-data labels remain visible where relevant.
- Settings has four main rows: Devices, Targets, Your data and App updates. Working journal, manual activity, water log and breathing tools remain optional.
- Removed name and bedtime inputs: their stored values were unused by any native feature. Legacy preferences remain readable.
- Removed the two import-only metric entries (`samsungEnergy`, `sleepRegularity`) from the native catalog; neither can arrive through the implemented Samsung connection. Legacy file parsing and score inputs remain compatible. There are 44 native metric views.
- Removed duplicate overnight rows, duplicated Today/Health baseline grids, the duplicate sleep-details link, a second companion-settings shortcut, generic recovery filler and unreachable habit/insight flows.
- Deleted 11 unreachable composables/helpers and the obsolete private metric/icon registry. No reachable production UI function is left without a caller in the static inventory.
- Fixed decimal precision in Trends, file operations on the UI thread, and export's false-success path when no output stream opened.

## Function and action audit

| Functions / flow | Result | Verification |
| --- | --- | --- |
| `FormaApp`, `FormaNavigation`, `AppHeader` | Retained; distinct device/data destinations, read-only source badge, parent back arrow and explicit dismiss | Native navigation and back-path interaction |
| Date picker / `select` | Retained; bounded to recorded dates and today | Native open/confirm; calendar-window unit checks |
| `FormaTheme`, `SurfaceCard`, `Heading`, `Eyebrow`, `Arrow`, `ValueRow`, `ActionRow`, `DetailDisclosure` | Shared visual primitives; expandable help replaces paragraphs | Compilation, flow tests, native screenshot review |
| `TodayActivity`, `RingProgress` | Retained; targets remain upfront, missing values not zero; Move opens step history | Native ring targets and shared-calorie labels; ring unit tests |
| `TodayHeart` | Retained; recorded/latest/live labels and timestamps remain distinct | Native rendering; timestamp round trip and watch-frame expiration tests |
| `ScoreDashboard`, `ScoreExplanation`, `ScoreDiagnosticsPanel`, `ScoreDiagnostics` | Retained; values before text, model/copy controls expandable | Native open/copy; equal-86 and changing-activity fixtures |
| `RecoveryHero`, `ScoreRing` | Retained; readiness and baseline context, no generic breathing promotion | Native Recovery render |
| `EnergyCard`, `NativeLineChart` | Retained; time/window label and tap inspection | Native chart inspection, gap and load tests |
| `SignalGrid`, `Sparkline`, `MetricTile` | Retained; compact, consistent units and absent-record gaps | Native dashboard, search and metric histories |
| `HealthSection`, `HealthPanel`, `OverviewFact` | Retained; compact section/value hierarchy | Native visual review |
| `SleepHero`, `SleepCard` | Retained; stages lead, duplicate detail link removed | Native sleep render and stage-quality unit checks |
| `SleepPatterns`, `sleepClockLabel` | Retained; explanation expandable | Midnight-wrap, coverage and native sleep checks |
| `WeeklyBars`, `MovementMomentum`, `DailyActivityTimeline` | Retained; actual periods, targets and selectable hours | Native render; activity/calendar calculations |
| `RecordedWorkouts`, `WorkoutHistory`, `workoutName`, `WorkoutDetail` | Retained; observed coverage visible, interpretation expandable | Native session open, heart context and range controls |
| `PersonalVitals` | Retained on Recovery; duplicate grids removed elsewhere | Baseline/deviation unit tests and native render |
| `TrendsScreen`, `MetricExplorer`, `MetricDetail` | Retained; decimal precision corrected; descriptions expandable; catalog searchable | Native ranges, chart selection, search/history |
| `DataArrivalCard`, `DataManagementPanel`, `DataCoveragePanel` | Retained; data operations separated from connection setup, coverage uses calendar days | Native routing, export/import callbacks, reset confirmation and screenshot |
| `PrivacyPanel` / Health Connect rationale route | Retained; short controls with full disclosure available | Native permission-management dispatch and policy expansion |
| `WatchConnectionPanel` | Rebuilt as compact Devices panel; supported permissions/history/background controls gated | Native allowed/busy/pause/history/background callback states |
| `DirectWatchCard`, `DirectWatchSetup` | Retained; compact status, explicit optional transport consent, install-guide link | Native consent/sharing independence and action checks |
| `AppUpdateBanner`, `AppUpdatePanel` | Retained; release notes and installer help expandable | Native check/download/cancel state controls |
| `PreferencesForm`, `PreferenceSlider` | Retained only for implemented ring/sleep/manual-water targets | Native save/reload and goal unit checks |
| `WorkoutForm`, `saveWorkout`, `deleteWorkout` | Retained under Personal tools; empty/invalid duration disabled | Native input validation, save and delete |
| `CheckIn`, `moodName`, `saveJournal` | Retained under Personal tools | Native note persistence |
| `HydrationCard`, `addWater` | Retained under Personal tools; manual label, undo | Native +250/undo and unchanged-score check |
| `BreathingPause` | Retained under Personal tools; run/pause/resume | Native controlled-clock progression |
| `EmptyState`, `InfoText`, `FactorRow`, `MetricRow`, `stateText`, `hourLabel` | Retained contextual primitives; repeated prose reduced | Used by tested screens |
| MainActivity file launchers | Retained native document contracts; bounded JSON import; file IO moved off main thread, null streams fail visibly | UI dispatch, codec/store round trip, invalid-input rejection; contract inspection |
| MainActivity health/history/background launchers | Retained; grants update connection state and trigger appropriate work | Request wiring inspected; reader permission/history fixtures |
| MainActivity external links / installer | Retained; failure feedback and real installation guide; no fake one-tap watch install | Intent wiring inspected; public APK signature and upgrade verification |
| `HealthStore`, `FormaViewModel`, `HealthSyncCoordinator` | Retained; actual save/import/clear/pause behavior | Native persistence/reset/import tests and data-quality unit checks |
| `HealthReader`, `HealthQuality`, `HealthAnalytics`, `Scores`, `PersonalSignals`, `HealthJson` | Retained; no formula recalibration in this UX change | Full unit suite and native response fixtures, including wake-hour and cache repair |
| `WatchLink`, `WatchMessageService`, `WatchFrame` | Retained; source identity, consent, timestamps and expiration | Protocol and opt-out tests; real device transport not claimed verified |
| `GitHubUpdates`, update store/view model/worker and `UpdatePolicy` | Retained; download integrity, signing lineage and version checks | Unit policy tests, previously verified live phone/watch downloads, signed APK upgrade checks |
| Wear `WatchScreen`, `WatchUpdateScreen`, `WatchActivity`, `WatchSensors`, passive service and boot receiver | Retained; brief controls; no phone UI duplication | Existing four native watch/Health Services checks and live watch updater evidence; no sensor changes in 1.5.0 |

## Removed functions

`EnergyInstrument`, `SupportingScore`, `InsightCard`, `VitalsCard`, `DaySummary`, `MovementCard`, `MovementStat`, `EditorialCard`, `HabitTip`, `SettingsRow`, and `TodayMetrics` had no live production call sites. The old private `metrics` registry and `icon` helper were also removed. The old “Daily insight” and “Small habits” sheet branches were unreachable.

## Evidence and limits

Verification: 53 unit checks and 39 distinct native phone checks passed. The full suite passed before the final unavailable-score adjustment; the 12 audit flows, including that new regression case, passed again afterward. Compatibility checks and both optimized APK builds passed. Native snapshots come from Compose instrumentation, not browser mockups. Tests use isolated emulators, SDK response fixtures and explicitly marked demo data. They do not establish clinical validity, guarantee Samsung-only fields, or verify a user's physical permissions/transport/installer. JSON import supports Forma v1 files up to 5 MB; health export excludes personal notes/manual logs, and is not a full app backup.

## Activity-reader follow-up — 1.5.1

Score details now include one compact before-rounding value on expansion. The copied report adds read permissions, returned/matched hourly-summary counts, exact-query counts and raw HR fallback evidence. Main screens retain their existing density. Energy can use precise post-wake aggregates or minute-binned timestamped HR if summaries are absent; its graph retains actual timestamps for raw-only observations. Day-wide steps and overnight HR remain excluded from post-wake load. Old native caches reconcile once at data version 4; Forma v1 exports retain optional diagnostics and old files remain readable.

Verification: 55 unit checks and 15 targeted native reader/score checks passed on an isolated Android 14 emulator, with the compact value checked again after its addition. The 14 reader cases include six new regressions for empty summaries, partial windows, pre-wake exclusion, raw minute coverage/timestamps, rejected overlapping summaries, and sparse summary precedence. This extends the earlier UX audit; it does not assert physical Samsung publication or validated health accuracy.
