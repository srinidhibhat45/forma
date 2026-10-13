# Install and update Forma on Galaxy S24 Ultra

Forma is distributed as a signed APK from [this repository's releases](https://github.com/srinidhibhat45/forma/releases/latest). It does not depend on a Play Store listing or a web host. Health records remain on the phone; only update checks and downloads use GitHub.

1. Download **forma.apk** from the latest release on your phone. Android may ask you to allow **Install unknown apps** for the browser or file manager opening it. Authorize that source if you choose to install the APK.
2. Open the signed APK and confirm Android's installation. Existing Forma installations with the supported signing lineage upgrade in place; do not uninstall merely to update, because uninstalling deletes the private local records.
3. For later releases, use **Health settings → App updates → Download & update**. Android asks separately for Forma's installation access the first time. Confirm the update in Android's installer. Repo changes produce a signed replacement APK through GitHub Actions; native Kotlin changes cannot be applied by silently downloading web content.

Samsung **Auto Blocker** may prevent installs from outside authorized stores and may disable the unknown-source control. It can also block updating an APK that was installed while Auto Blocker was off. If Android reports that block, review **Settings → Security and privacy → Auto Blocker** and decide whether to permit this sideloaded installation. Forma's update screen links to security settings. The app cannot override Samsung policy, work-profile administrator restrictions, or guarantee that Play Protect will never display a warning. A security warning alone is not a reason to assume the device is malfunctioning; check its stated reason and the download source.

A stable dedicated signing identity, public key-rotation lineage, release-mode builds, file checksums and signing/version checks support reliable updates. They do not exempt an APK from Android's security checks. If the installer rejects an update for a signing mismatch, retain the current app and export records before considering a reinstall; obtain the exact installer error for diagnosis.

[Samsung's Auto Blocker installation and update guidance](https://www.samsung.com/au/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/), [Android package installation access](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
