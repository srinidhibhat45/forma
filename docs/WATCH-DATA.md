# Samsung watch records in Forma

The native phone application reads **Samsung Health → Health Connect → Forma**. Your Watch6 Classic remains paired through Galaxy Wearable. No additional Bluetooth pairing, server, account or Play Store listing is required by Forma. Samsung Health must publish each measurement into Health Connect; live readings visible inside Samsung Health do not guarantee the same publication time in Health Connect. One UI Watch updates do not guarantee access to new fields.

## First connection

1. Open Samsung Health, allow the watch to finish syncing, and enable the desired measurements in its Health Connect sharing settings.
2. Open Forma's watch connection or **Health settings → Your watch & connections** and choose **Allow access & connect**. Grant the measurements you want. Grant older-history access if offered.
3. Recent records appear first. Older days import in batches, with progress and a pause control. Completed batches remain on the phone, so retrying can continue an interrupted import. Pause disables scheduled health reads until Sync is resumed; optional background refresh can then be enabled again separately.
4. If a field says **Not shared**, check both Samsung's sharing settings and Forma's read permission. HRV, resting HR and temperature may be absent even when Samsung displays other records.

Without older-history permission, the initial scan uses 29 recent calendar days; a preceding-day lookback includes overnight records while staying conservatively within the default 30-day access limit. With that permission, Forma scans up to one year of shared history. This is an explicit bounded import, not a promise to recover all Samsung Health history. Samsung may expose less history. Revoking history permission does not erase previously saved days; clear local records if you want them removed.

## Refresh and fidelity

While open, Forma checks Health Connect changes approximately every minute. Unchanged records avoid a full reread. Recent insertions refresh the recent window; older corrections, deleted records and expired checkpoints trigger reconciliation of the allowed history. A changes checkpoint is captured before the snapshot and saved only after the import completes, so changes during a read are not silently consumed. Foreground and background reads share a lock. Optional background work is requested every 15 minutes; Android controls actual timing. Clearing records stops health refresh until the user explicitly resumes it. Importing a JSON file also pauses automatic health reads to prevent silently replacing that file.

- Today rings show steps, calories, and the union of recorded exercise-session minutes. Active calories are preferred; when only total calories arrive, the ring explicitly switches to **Shared calories** and explains that Samsung may export exercise calories rather than a full-day activity total. Exercise minutes do not mean every active minute. Missing measurements are not zero; an absent exercise session is not assumed to be a verified zero.
- Latest heart rate comes from the latest actual shared sample, with its timestamp. Hourly averages and a recorded daily min/max remain distinct. No resting HR or HRV is invented from ordinary HR samples.
- Cumulative measurements use Health Connect aggregation restricted to Samsung Health's package. Daily buckets follow civil days, including 23/25-hour daylight-saving days.
- VO₂ max, recorded speed/power averages and peaks, shared total calories, basal metabolic rate and height are also read when permitted and published. Each has a discoverable history view; no fitness ranking or clinical status is invented from those fields.
- Overlapping exercise sessions count once. Sleep stages are clipped and reconciled; generic **Asleep** stages count as sleep, while unknown intervals remain visible gaps. The longest nightly session supplies the sleep estimate; additional known sleep outside it supplies nap minutes.
- Sleep HR fallback uses actual samples inside known sleeping intervals of the primary night, requires at least 12 observed minute bins spanning half the session, and is explicitly labeled **Sleeping heart rate**.
- Overnight oxygen/breathing are separate from daily medians. Recorded absolute skin temperature uses the supplied baseline plus measured deltas; skin temperature change is displayed separately.
- Latest **reported record** time and **last read** time have different labels. Successfully reading a datastore does not make old measurements current.
- Only device metadata identifies an individual record as watch-origin. Samsung can combine phone and watch measurements.

## Readiness and energy estimates

Forma's composites are transparent experimental wellness estimates, independent of Samsung, WHOOP and Garmin. They are not clinically validated and cannot perfectly indicate a person's overall health. At least seven distinct prior days of a matching heart-rate or HRV vital are required for a personal baseline. The previous 28 days supply robust medians and median absolute deviations; HRV comparisons use logarithms. Sleep is required for readiness. Missing or incomplete sleep prevents the composite rather than producing a confident score from heart data alone.

Activity-based body energy starts from readiness, takes the strongest overlapping steps/exercise/heart load once, and scales heart load for a partial hourly bucket. It does not accumulate three representations of the same exercise. Native readings do not invent restful minutes; only explicit imported rest can add energy. The formula, its weights, minimum history and variability floors are product heuristics, not medically validated thresholds. Literature supporting personal HRV context does not validate Forma's composite: [HRV-guided training study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7663087/) and [HRV monitoring review](https://pmc.ncbi.nlm.nih.gov/articles/PMC8507742/).

The three displayed integers can match because the calculations share inputs and round to whole points. In 1.4.1, tapping a score explains the actual matching values, shows normalized contributing weights and the activity window timestamp, and offers **Copy score details**. This copies only the selected date's score inputs and calculation details to the local clipboard; it does not send them anywhere. Energy changes smaller than half a point may be hidden by rounding. Today's energy is flagged as waiting for newer activity if its recorded window is over two hours old; historical days are not incorrectly treated as current stale readings. Direct companion readings still do not enter the historical score model.

Samsung's proprietary sleep/energy scores, raw ECG and PPG are not available through this adapter. Samsung Health Data SDK would need a separate implementation and Samsung's applicable app-verification/partnership process; it is not implemented here.

## Verification boundary

Unit tests cover baseline sufficiency, bounds, missing stages, overlap, DST, stale records, history merging and JSON persistence. Native instrumentation exercises screens, goals, metric searching/history, updater controls and the reader against SDK response fixtures, including pagination, deletions and expired changes. These tests do not establish clinical accuracy or verify Samsung's actual publishing cadence. Physical Galaxy S24 Ultra / Android 17 / Watch6 Classic end-to-end syncing still needs the user's device.

Primary references: [Android record reads and history permissions](https://developer.android.com/health-and-fitness/health-connect/read-data), [aggregation](https://developer.android.com/health-and-fitness/health-connect/aggregate-data), [changes synchronization](https://developer.android.com/health-and-fitness/health-connect/sync-data), [Samsung Data SDK FAQ](https://developer.samsung.com/health/data/faq.html).

[Samsung’s sharing scope and calorie distinctions](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) explain why Samsung’s activity-ring totals may differ from the records published through Health Connect.

## Direct watch companion (1.4)

The repository now includes a separate native `:wear` application. It uses the same `com.forma.health` package and dedicated release signing key as the phone app, as required by the [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview). The phone and watch advertise Forma capabilities and discover the existing nearby Galaxy Wearable pairing automatically. No second Bluetooth pairing is introduced.

Install `forma-watch.apk` from the same GitHub release on the watch once; see [the companion installation guide](WATCH-COMPANION.md). In Forma phone Settings → Direct watch companion, enable **Receive watch measurements**. Open Forma on the watch and grant activity/heart-rate permissions when requested.

- **Measure heart rate** registers Health Services MeasureClient for at most 60 seconds while the watch activity is visible. Leaving the activity stops the measurement. Supported capabilities and sensor availability are checked. Readings are sent approximately every two seconds to nearby matching phone capabilities.
- **Enable daily activity** registers a permission-protected passive listener for the daily steps/calories/distance/floors types that the watch supports. It resumes after reboot if still enabled and permission remains granted. It does not request background body-sensor permission or begin an exercise session.
- Passive updates can be batched unpredictably by Wear OS. They are not marketed as continuous real-time steps. Every metric retains its sensor timestamp, distinct from transmission time.
- The phone's heart card shows **Live watch** only for a direct reading no more than 30 seconds old, on Today with an actual Samsung dataset. The direct-watch card remains separately sourced even while the phone is in demo mode. Today's step ring may use a same-day watch count no more than five minutes old; its source change is labeled. Other rings remain Samsung-derived.
- Watch-reported daily calories are separate from active-calorie rings. There is no attempt to invent active calories, HRV, sleep, ECG, or beat-to-beat data from spot HR measurements.
- Messages are bounded to 8 KB, schema/range/time validated, accepted only while explicitly enabled and from a nearby matching watch capability. Out-of-order packets reconcile each metric by its measurement time. A new HR packet cannot revive old steps. Turning reception off or clearing/importing data discards the phone's cache and rejects late packets.
- The phone's direct cache uses separate private preferences to avoid repeatedly rewriting historical health records. It is not merged into exported historical readings or experimental energy calculations.

Data Layer traffic is restricted by Google Play services to matching package/signing identities and encrypted. Forma addresses nearby nodes and accepts nearby senders; the underlying transport can still use Google's encrypted relay when connectivity changes. This is disclosed in setup and privacy screens; it is not claimed to be an exclusively local Bluetooth channel.

The companion also has GitHub **App updates** using the same version, file-size, SHA-256, signing-lineage and device-type checks as the phone. Watch and phone APKs have separate manifests. Android/Wear OS requires installation confirmation; watch installer support must be verified on the physical Galaxy watch. Wireless-debugging installation remains an in-place fallback.

Additional primary references: [spot measurements](https://developer.android.com/health-and-fitness/health-services/active-data/measure-client), [passive monitoring](https://developer.android.com/health-and-fitness/health-services/monitor-background), [sensor permissions, including Wear OS 6](https://developer.android.com/health-and-fitness/health-services/permissions), and [device compatibility and batching](https://developer.android.com/health-and-fitness/health-services/compatibility).

## Additional health features

Shared sessions now retain start/end, type/title, session-window aggregate metrics and observed minute-average heart traces. Workouts are deduplicated by provider identity and identical session boundaries/type; overall exercise duration still counts overlapping intervals once. Session-window totals may include simultaneous activity, and elapsed duration/pace includes pauses. BPM distribution counts observed minute bins only; sparse samples are not turned into time spent in prescribed training zones.

Hourly steps/active calories/exercise are retained independently of post-wake energy buckets. Seven-night sleep patterns exclude incomplete nights and preserve missingness; bedtime statistics use a noon-anchored local clock so times around midnight remain adjacent. Shortfall is summed against the user's goal for recorded nights, not estimated biological sleep debt. Personal breathing/oxygen/skin-temperature comparisons use the same prior-day robust median/MAD approach with heuristic variability floors (1 breath/min, 1 percentage point, 0.15 °C); these are descriptive, not clinical normal ranges.

Nutrition and hydration permissions are requested in addition to previous types. Samsung-shared food records supply energy/macronutrients/fiber/sugar/sodium when present, while water uses daily aggregation. Their absence does not imply zero intake. They are phone/Samsung records rather than claimed watch measurements. Cache schema version 2 triggers a history re-read to recover sessions older versions discarded.
