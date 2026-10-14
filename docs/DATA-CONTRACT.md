# Forma v1 import format

Choose **Health settings → Your data → File import → Import Forma JSON**. Native Samsung Health CSV/ZIP downloads are not directly supported; normalize them into this format first. `output/forma-sample-data.json` is a complete demo sample.

```json
{
  "version": 1,
  "source": "Samsung Health · personal export",
  "demo": false,
  "days": [
    {
      "date": "2026-10-13",
      "sleepMinutes": 468,
      "sleepEfficiency": 90,
      "restingHeartRate": 58,
      "steps": 6842,
      "energySamples": [
        {"hour": 8, "steps": 1900, "activeMinutes": 18, "heartRate": 105}
      ]
    }
  ]
}
```

Daily records have a unique YYYY-MM-DD date and optional numeric fields. Omit unavailable measurements; never fill gaps with zeros unless a zero was actually recorded. Additional units: VO₂ max in ml/kg/min, speed in km/h, power in watts, basal metabolic rate in kcal/day, height in cm. Total calories are preserved as shared and never silently called active calories.

Metric units: sleep/nap/active time in minutes; heart rate in bpm; HRV in ms; oxygen and body fat in percent; respiratory rate in breaths/min; skin temperature in Celsius; active calories in kcal; distance in km; weight/lean mass in kg; blood pressure in mmHg; glucose in mmol/L.

Additional nutritional fields: `dietaryCalories` (kcal), `proteinGrams`, `carbsGrams`, `fatGrams`, `fiberGrams`, `sugarGrams` (g), `sodiumMg` (mg), `waterMl` (ml). These are shared Samsung records, not inferred watch measurements.

Fields: `totalCalories`, `vo2Max`, `speedKmh`, `maxSpeedKmh`, `powerWatts`, `maxPowerWatts`, `basalMetabolicRate`, `heightCm`, `heartRateLatest`, `heartRateAverage`, `heartRateMin`, `heartRateMax`, `sleepCoverage`, `sleepSpo2`, `sleepRespiratoryRate`, `skinTemperatureDelta`, `steps`, `sleepMinutes`, `sleepEfficiency`, `sleepRegularity`, `restingHeartRate`, `sleepHeartRate`, `hrv`, `spo2`, `respiratoryRate`, `skinTemperature`, `activeMinutes`, `activeCalories`, `distanceKm`, `floors`, `weightKg`, `bodyFat`, `leanMassKg`, `systolic`, `diastolic`, `bloodGlucose`, `napMinutes`, `samsungEnergy`.

Optional `sleepStart`/`sleepEnd` are ISO UTC timestamps. `sleepStages` is an ordered array of `{ "stage": "Light", "minutes": 42 }`; stages are Awake, REM, Light, Deep, Asleep, Unknown. Generic sleeping stages use Asleep; gaps use Unknown and must not be turned into Awake or omitted from the timeline.

`energySamples` contains **incremental hourly totals**, not cumulative daily counts. Each entry has a unique numeric `hour` between 0 and 26 (elapsed hours from local midnight, allowing a 25-hour DST day) and optional `steps`, `activeMinutes`, `restMinutes`, `heartRate`. Activity/rest minutes must sum to at most 60 per sample. Steps must be at most 200,000 per sample. Optional `durationMinutes` scales heart load (up to 60): it is the query-window duration for aggregate HR, or independently observed minute coverage for raw HR fallback. `recordedAt` is the activity-window end for aggregate/exercise data or the actual last reading when only raw HR contributes. Future/forecast data is not an observed sample and must not be inserted. Rest minutes should be supplied only if explicitly measured or recorded.

Optional `heartRecordedAt` is the actual latest HR sample timestamp; `heartTimeline` contains up to 26 recorded hourly averages as `{ "at": "2026-10-13T10:00:00Z", "bpm": 79 }`. These are distinct from latest HR. Optional top-level `syncReport` preserves native read metadata in local cache/export. User imports clear this metadata, label the source as Imported, and pause automatic health reads until Sync is resumed.

Imports are limited to 5 MB and 1–3,660 days. Invalid dates, duplicate days, duplicate sample hours, invalid ranges and malformed values are rejected. The native sync cache keeps up to 365 days. Export includes health records; local journal and hydration are not included in that export in this version.

Demo files carry `"demo": true`; their banner remains visible after import. The source label does not cryptographically verify an imported file. Only direct Health Connect readings come from the configured Android provider.

Optional `activityHours` stores up to 26 hourly rows with ISO `at`, optional incremental `steps`, `calories` (active kcal), and `exerciseMinutes`. The inspectable timeline uses actual local-hour timestamps.

Optional `recordedWorkouts` stores up to 200 sessions per calendar day. Each session contains `id`, Health Connect integer `type`, optional `title`, ISO `start`/`end`, optional scalar `values` and a `heart` list of `{ "at": "…", "bpm": 120 }`. Allowed session values are distance, active/shared-total calories, steps, recorded speed/power averages and peaks, and heart-rate average/min/max. Heart rows represent minute averages during the session, not beat-to-beat data. Up to 10,080 minute rows are accepted for a session of at most seven days. Minute timestamps must lie inside the session; malformed input is rejected. Session IDs are deduplicated. Exports retain these details while remaining Forma v1 compatible.

Native cache reports carry `dataVersion: 4`. Older native snapshots are re-read to recover sessions and post-wake activity. Optional per-day `energyRead` preserves read diagnostics: ISO `from`/`to`, permission flags `stepsAllowed`/`heartAllowed`, `returnedBuckets`, `matchedBuckets`, `exactQueries`, optional `exactPostWakeSteps`, `rawHeartMinutes`, optional ISO `latestRawHeartAt`, and `rawHeartFallbackBuckets`. These counts explain which read paths worked; they do not change scores. Old exports without this metadata remain compatible. The separate direct-watch cache stores the latest measurements with per-field timestamps; it is not merged into exported historical records. Imports turn direct-watch receipt off as well as pausing Health Connect refresh.
