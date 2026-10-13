# Forma / Native interface, version 2

The dashboard is an instrument, with body energy as its first and largest reading. Recovery and sleep support it beneath the dial. One actionable signal follows; detailed traces, vitals and habits live farther down the scroll.

## Visual system

- Near-black background (#101211), ivory text (#F4F5EC), restrained charcoal surfaces.
- Acid lime (#D8FC70) identifies energy, readiness and the selected tab.
- Blue and periwinkle identify sleep; orange identifies movement.
- Locally bundled Plus Jakarta Sans, with heavy headlines, large readings and quieter labels.
- A custom double-stroke brand mark, slim navigation indicators and native Material 3 sheets, date picker, controls and permission flows.

Each screen has a distinct composition. Today centers a segmented radial gauge. Sleep begins with an immersive blue duration panel, followed by a four-lane sleep-stage timeline. Move uses oversized steps and a segmented horizontal goal meter. Trends has metric selection, 7/28-day range selection and inspectable traces. You presents journal, water and connection controls.

The energy dial is derived from the existing estimate; it is not a live sensor indicator. Its timestamp refers to the last recorded sample. Sleep stage widths follow recorded durations. Chart points retain missing-data gaps, and decorative orbits on the sleep panel convey no measurement. The existing generated landscape is used with a darker photographic overlay in the habits section.

All five tabs, recovery details, score calculations, check-in, manual activity, hydration, goals, connections, date selection, imports/exports, privacy and breathing remain available. The app uses native Compose components and Canvas; no embedded browser is used.

Font: [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans), distributed under the SIL Open Font License. Its license is included in `docs/licenses/` and the APK assets.
