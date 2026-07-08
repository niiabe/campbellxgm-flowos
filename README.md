<div align="center">
  <img src="app/src/main/res/drawable/logo.png" alt="CampbellXGM Logo" width="180"/>
  <h1>CampbellXGM FlowOS</h1>
  <p><strong>Extreme Gaming Mode App for Android</strong></p>
  <p>Locks pings, freezes background apps, and maximizes device performance.</p>

  <h3><a href="https://github.com/niiabe/campbellxgm-flowos/raw/master/downloads/CampbellXGMv1.apk">📥 Download CampbellXGMv1.apk</a></h3>
</div>

---

## ⚡ Overview

**CampbellXGM** is a high-performance, native Android app built to turn your device into an uninterrupted gaming console. Designed with a sleek, high-contrast **Pure Black, Neon Red, and Electric Blue** Alienware-style aesthetic, the app acts as a master control switch for your Android environment. 

When you launch a game through CampbellXGM, it aggressively shuts down system distractions to ensure zero dropped frames, locked pings, and peak hardware performance.

## 🚀 Key Features

*   **Ping Stabilizer (VPN Engine):** Uses a local Android `VpnService` to create a network "black hole." All background apps are forcefully routed into this void, instantly dropping their connections, while your selected game is explicitly whitelisted to receive 100% of your router's bandwidth.
*   **Aggressive App Freezing:** CampbellXGM leverages Android Accessibility Services (`SafetyInterceptor`) to literally click "Force Stop" on background apps, while simultaneously using `DevicePolicyManager` (Device Owner mode) to suspend non-essential packages.
*   **Zero-Latency Engine:** Built on Kotlin Coroutines (`Dispatchers.IO`), all background polling (cache clearing, process killing, temperature checking) is completely decoupled from the Main Thread to guarantee a frictionless, stutter-free UX.
*   **Thermal Cooldown Protection:** Actively monitors the battery temperature via system intents. If your device reaches dangerous thermal thresholds, it temporarily throttles performance to prevent hardware damage, before ramping back up.
*   **Network Lockdown & Custom DNS:** Overrides active Wi-Fi states, locks DNS to Private endpoints (Cloudflare, Google, AdGuard, Quad9), and silences all notifications via `ACCESS_NOTIFICATION_POLICY` to ensure your screen and connection are completely isolated.
*   **Automated Teardown (Crash Fail-safe):** When you exit a game (via the persistent system notification), CampbellXGM instantly disassembles the VPN, unfreezes your apps, and restores your original DND/Network states to normal.

## 🎛️ Settings Dashboard
The app features a fully categorized control center allowing deep customization:
- **System Privileges:** Manage Device Owner and Accessibility configurations.
- **Performance:** Toggle CPU Tuners, Auto-Brightness Locks, and Storage Cleaners.
- **Connectivity:** Configure the Ping Stabilizer and Custom DNS Providers.

## 💎 PREMIUM (Pro Features Roadmap)
The app includes a dedicated Settings section laying the groundwork for monetization. Future Pro features include:
- **Individual Game Profiles:** Per-game engine configurations.
- **Real-Time Hardware HUD:** A floating widget that displays live RAM, CPU, and Battery temperature over your game.
- **Custom Crosshair Overlay:** Customizable aiming reticle for FPS games.
- **Macro Recorder:** Record and replay exact touch sequences.

## 🎨 Design System

Built entirely in modern **Jetpack Compose**, the UI abandons standard material palettes for a deeply customized gaming interface:
*   **Background:** True AMOLED Black (`#000000`)
*   **Accents:** Electric Blue (`#00E5FF`) and Neon Red (`#FF003C`)
*   **Components:** Custom-built `AlienButton` and `SettingsCategoryCard` modifiers that simulate glowing hardware.

## 🛠️ Tech Stack & Architecture

*   **Language:** Kotlin Native
*   **UI Toolkit:** Jetpack Compose + Compose Navigation
*   **Concurrency:** Kotlin Coroutines & StateFlow
*   **Architecture:** Clean Architecture (`data`, `domain`, `ui` packages)
*   **System Integrations:** `DeviceAdminReceiver`, `AccessibilityService`, `VpnService`, `WifiManager`, `NotificationManager`

## ⚙️ Setup & Installation

> [!WARNING]
> Because CampbellXGM uses powerful APIs to freeze apps, the application must be granted specialized permissions to function at full capacity.

1. Build the APK using Android Studio or `./gradlew assembleDebug`.
2. Install the APK to your device.
3. Open the app and navigate to **Settings -> Permissions Dashboard**.
4. Grant the **Accessibility Service** permission (required for aggressive app force-stopping).
5. Grant the **Do Not Disturb (DND)** permission.
6. Enable the **Ping Stabilizer**, which will prompt you to trust the app as a VPN.
7. *(Optional)* For maximum suspension power, run the following ADB command from your computer to grant CampbellXGM **Device Owner** status:
   ```bash
   adb shell dpm set-device-owner com.example.campbellxgm/.domain.services.CampbellAdminReceiver
   ```
8. Navigate back to the Dashboard, select your game, and hit **LAUNCH**!
