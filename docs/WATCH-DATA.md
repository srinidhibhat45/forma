# Samsung watch records in Forma

The native phone application reads **Samsung Health → Health Connect → Forma**. Your Watch6 Classic remains paired through Galaxy Wearable. No additional Bluetooth pairing, server, account or Play Store listing is required by Forma. Samsung Health must publish each measurement into Health Connect; live readings visible inside Samsung Health do not guarantee the same publication time in Health Connect. One UI Watch updates do not guarantee access to new fields.

## First connection

1. Open Samsung Health, allow the watch to finish syncing, and enable the desired measurements in its Health Connect sharing settings.
2. Open Forma's watch connection or **You → Your watch & connections** and choose **Allow access & connect**. Grant the measurements you want. Grant older-history access if offered.
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

Samsung's proprietary sleep/energy scores, raw ECG and PPG are not available through this adapter. Samsung Health Data SDK would need a separate implementation and Samsung's applicable app-verification/partnership process; it is not implemented here.

## Verification boundary

Unit tests cover baseline sufficiency, bounds, missing stages, overlap, DST, stale records, history merging and JSON persistence. Native instrumentation exercises screens, goals, metric searching/history, updater controls and the reader against SDK response fixtures, including pagination, deletions and expired changes. These tests do not establish clinical accuracy or verify Samsung's actual publishing cadence. Physical Galaxy S24 Ultra / Android 17 / Watch6 Classic end-to-end syncing still needs the user's device.

Primary references: [Android record reads and history permissions](https://developer.android.com/health-and-fitness/health-connect/read-data), [aggregation](https://developer.android.com/health-and-fitness/health-connect/aggregate-data), [changes synchronization](https://developer.android.com/health-and-fitness/health-connect/sync-data), [Samsung Data SDK FAQ](https://developer.samsung.com/health/data/faq.html).

[Samsung’s sharing scope and calorie distinctions](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) explain why Samsung’s activity-ring totals may differ from the records published through Health Connect.
