<div align="center">
  <img src="app/src/main/res/drawable/logo.png" alt="CampbellXGM Logo" width="180"/>
  <h1>CampbellXGM FlowOS</h1>
  <p><strong>Extreme Gaming Mode App for Android</strong></p>
  <p>Locks pings, freezes background apps, and maximizes device performance.</p>

  <h3><a href="https://github.com/niiabe/campbellxgm-flowos/releases/latest">📥 Download latest release (APK)</a></h3>
  <p>Or just install the app — it updates itself from GitHub Releases automatically.</p>
</div>

---

## Overview

**CampbellXGM** is a high-performance, native Android app built to turn your device into an uninterrupted gaming console. Designed with a sleek, high-contrast **Pure Black, Neon Red, and Electric Blue** Alienware-style aesthetic, the app acts as a master control switch for your Android environment. 

When you launch a game through CampbellXGM, it aggressively shuts down system distractions to ensure zero dropped frames, locked pings, and peak hardware performance.

## Key Features

*   **Aggressive App Freezing:** Kills ALL background apps using three methods — Device Owner suspension, Accessibility force-stop, or background process killing. Finds every running process (not just launcher apps) and periodically re-kills them every 5 seconds.
*   **Media Keep-Alive:** Your music and streaming apps are automatically detected and excluded from freezing, so audio keeps playing during gameplay. Grant Notification Access (Settings → Permissions) to enable it.
*   **Ghost Finger (opt-in):** The Accessibility force-stop method is now OFF by default. Enable it in Settings only if you need thorough force-stop without Device Owner — note it opens each app's Settings page during freezing.
*   **Auto-Teardown:** Detects when you leave a game via Usage Access monitoring and automatically restores all system settings — no manual "Stop Game Mode" tap needed.
*   **Auto-Start Game Mode:** Add your games to the dashboard, enable auto-start, and game mode activates automatically when you launch any saved game.
*   **FPS Overlay:** Shows real-time frame rate on screen during gameplay.
*   **Thermal Cooldown Protection:** Monitors battery temperature. If your device overheats, it throttles CPU to prevent hardware damage, then restores performance when safe.
*   **Network Boost & Custom DNS:** Disables background sync, binds to active network, and overrides DNS to fast private endpoints (Cloudflare, Google, AdGuard, Quad9).
*   **DND & Notification Filter:** Silences all notifications or allows only calls and priority messages during gameplay.
*   **Keep Screen Awake & Auto-Brightness Lock:** Prevents screen timeout and locks brightness for consistent gaming.
*   **Storage Cleaner:** Clears game cache and temp files before launch to free storage.
*   **Ping Stabilizer VPN:** Keeps mobile radio active with periodic keepalive packets to prevent connection drops.
*   **CPU/GPU Tuner:** Sets CPU governor to maximum performance mode (requires Device Owner).
*   **Battery Profile:** Disables battery saver and optimizes power for gaming.

## Settings Dashboard

The app features a fully categorized control center with 13 toggles:

- **System Privileges:** Device Admin, Device Owner (ADB), Accessibility Service, Usage Access
- **Performance:** Aggressive App Freezing, CPU/GPU Tuner, Battery Profile, Cool-down Mode, Storage Cleaner
- **Connectivity:** Network Boost, Ping Stabilizer, DNS Provider
- **Game Mode:** DND, Notification Filter, Auto-Start Game Mode, FPS Overlay, Keep Screen Awake, Auto-Brightness Lock

## Pro Features (Roadmap)

- **Individual Game Profiles:** Per-game engine configurations
- **Real-Time Hardware HUD:** Floating widget with live RAM, CPU, and Battery stats
- **Custom Crosshair Overlay:** Customizable aiming reticle for FPS games
- **Macro Recorder:** Record and replay touch sequences

## Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose + Compose Navigation
*   **Concurrency:** Kotlin Coroutines & StateFlow
*   **System APIs:** DeviceAdminReceiver, AccessibilityService, VpnService, UsageStatsManager, NotificationManager

## Setup & Installation

> **Warning:** CampbellXGM uses powerful system APIs. You must grant specialized permissions for full functionality.

### Install
1. Download the appropriate APK for your device architecture (e.g. `arm64-v8a`, or the `universal` APK if unsure) from the [Releases page](https://github.com/niiabe/campbellxgm-flowos/releases/latest) and install it (you may need to allow "Install unknown apps" for your file manager / browser).
2. Open the app — you'll be guided through the permission flow:
    - **Device Admin** — for aggressive app freezing
    - **Do Not Disturb** — to silence notifications during gameplay
    - **Notifications** — for the "Stop Game Mode" persistent notification
    - **Modify System Settings** — for Keep Screen Awake and Auto-Brightness Lock
    - **Accessibility Service** — for force-stopping background apps
    - **Usage Access** — for auto-teardown and auto-start game mode
3. *(Optional)* For maximum power, grant Device Owner via ADB:
    ```bash
    adb shell dpm set-device-owner com.campbell.xgm/.domain.services.CampbellAdminReceiver
    ```
4. Add games to the Dashboard, tap **Launch**, and game mode activates!

### In-App Auto-Update
CampbellXGM checks [GitHub Releases](https://github.com/niiabe/campbellxgm-flowos/releases) for a newer version every time it launches:
- **If an update is available**, a prompt appears with the release notes and a **Download & Install** button. Tap it to download the signed APK directly from the release and install it.
- **Skip** remembers that version so it won't prompt again until an even newer release ships.
- **Manual check:** `Settings → App Updates → Check for Updates` opens the same update screen any time.

## Building from source
```bash
./gradlew assembleDebug      # debug APK (app/build/outputs/apk/debug)
./gradlew assembleRelease    # signed release APK (needs signing config, see below)
```

### Release signing (CI)
`.github/workflows/release.yml` builds a **signed** release APK and publishes it to GitHub Releases. To enable it, add these repository secrets (`Settings → Secrets and variables → Actions`):

| Secret | Value |
| --- | --- |
| `CAMPBELL_KEYSTORE_B64` | `base64 -w0 app/release-key.jks` |
| `CAMPBELL_KEYSTORE_PWD` | keystore password |
| `CAMPBELL_KEYSTORE_ALIAS` | key alias (e.g. `campbellxgm`) |
| `CAMPBELL_KEYSTORE_KEY_PWD` | key password |

Then bump `versionName` / `versionCode` in `app/build.gradle.kts`, commit, and push a tag `vX.Y.Z` (e.g. `git tag v1.5.0 && git push origin v1.5.0`). The workflow creates the release automatically. The signing config also reads a local, gitignored `keystore.properties` for local release builds.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history.
