# Install Forma on a Galaxy Watch

The phone APK cannot install a Wear OS application simply by opening it on the phone. The first companion installation is a separate, one-time watch step. It does not require publishing to the Play Store. Download [the latest signed watch APK](https://github.com/srinidhibhat45/forma/releases/latest/download/forma-watch.apk); use `forma.apk` for the phone and `forma-watch.apk` for the watch.

## First installation through wireless debugging

1. On the watch, open Settings → About watch → Software information and enable developer options by tapping Software version repeatedly. Menu names can vary by One UI Watch version.
2. In watch Developer options, enable ADB/wireless debugging. Connect the computer and watch to the same trusted Wi-Fi. Use the pairing address/code and connection address shown by the watch.
3. With Android platform tools on the computer, pair, connect and install the watch APK. Replace the example addresses with the watch's displayed addresses; pairing and connection ports are often different.

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:CONNECTION_PORT
adb -s WATCH_IP:CONNECTION_PORT install -r forma-watch.apk
```

4. Open Forma on the watch. Choose **Enable daily activity** and allow physical activity access. Choose **Measure heart rate** and allow sensor access for a 60-second foreground check.
5. On the phone, open Forma → Health settings → Devices → Watch companion. Enable **Receive watch measurements**, then **Find my Forma watch**. The app reuses your Galaxy Wearable pairing. Keep the two devices connected nearby.
6. Turn watch wireless debugging off after installation. It is not needed for normal Forma sensing or pairing.

## Updates

Every push to main publishes a signed phone APK and a matching watch APK. Phone updates stay in place through Forma's existing updater. On the watch, **App updates** checks/downloads the watch-specific asset and verifies file size, SHA-256, package/version, signing identity and watch device type before opening its installer. Installation requires Android's confirmation and install-source permission. Some watch builds may not expose a usable installer/settings screen; that physical-device behavior is not verified here. In that case, install a newer `forma-watch.apk` with the same `adb install -r` command to preserve the watch's configuration.

You do not need to update the companion just to receive phone layout changes. Both apps retain the versioned v1 transport protocol. Updates that change the watch implementation require updating the watch APK too.

## Timing and missing data

A live heart check ends after 60 seconds or when you leave the watch app. Daily activity can continue in the background but Wear OS decides its delivery cadence. The phone shows sensor timestamps, expires live HR after 30 seconds, and uses a direct step count for the ring only while it is a same-day reading no more than five minutes old. Sleep/HRV/workout history and the remaining totals still arrive through Samsung Health sharing. The companion does not replace Galaxy Wearable or guarantee every Samsung-only field is available.

[Official Wear OS debugging guidance](https://developer.android.com/training/wearables/get-started/debugging) and [Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions).
