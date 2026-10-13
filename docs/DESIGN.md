# Forma / Native interface, version 3

Forma is a dense, native health app with five destinations: Today, Sleep, Move, Trends, and Health. Journaling and manual activity logs are optional settings tools. They no longer lead a main page or occupy the Today dashboard.

Today begins with compact concentric activity rings, targets and amounts remaining; three color-coded sleep/recovery/energy cards; and latest heart rate with its recorded time, daily range and hourly trace. Three-column signal cards expose six daily vitals and six overnight signals, each with a seven-day sparkline. The rest of Today contains an inspectable hourly activity timeline, recorded workouts, personal vital comparisons, the energy trace, body/metabolic readings and shared nutrition. No decorative photograph displaces these measurements.

Health exposes all 46 supported scalar measurements grouped into Heart, Vitals, Body, Movement, Sleep and Nutrition. Each tile opens an inspectable 7/28/90-day history. Infrequent body, blood-pressure, glucose and fitness measurements can display their most recent recorded value on or before the selected date, with an explicit date when carried forward. Their graphs retain gaps rather than inventing daily measurements.

Move includes step-goal streaks, recorded weekly exercise duration, goal completion, daily activity timing, performance measurements and 7/28/90/365-day workout history. A session opens its actual start/end, elapsed duration, source-window distance/calories/steps, minute-average heart trace, observed BPM distribution, speed/power and elapsed pace. Unsupported fields remain unavailable. The distribution describes observed bins, not personalized training zones.

Sleep retains its deep-blue duration panel and staged-night chart, then adds seven-night duration, recorded shortfall vs the chosen goal, usual bedtime/wake time, bedtime variation and goal attainment. Missing and incomplete nights are excluded. Shortfall is not presented as measured biological sleep debt.

## Visual system

- Near-black #101211 and ivory #F4F5EC establish contrast.
- Acid lime #D8FC70 identifies steps and estimated capacity.
- Periwinkle #879AFF identifies sleep and the exercise ring.
- Orange #FF9465 identifies activity, performance and larger personal-pattern changes.
- Cyan #59E3D1 identifies physiology and personal baselines; pink #FF647F identifies heart/body signals.
- Plus Jakarta Sans is bundled locally; labels are quiet, readings prominent, and mini-charts are native Canvas graphics.
- Material 3 sheets, date selection, permission requests and controls are native Compose components. No WebView is used.

Watch readings have separate source and measurement timestamps. Heart rate is called live for at most 30 seconds after a direct measurement; direct steps can update today's ring for five minutes before reverting to shared history. Health Connect refresh time is never substituted for sensor measurement time. The companion offers a 60-second foreground heart check and optional batched daily activity.

Font: [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans), SIL Open Font License, bundled in `docs/licenses/` and APK assets. Existing generated artwork remains available in the repository; it is not used as filler on the health dashboard.
