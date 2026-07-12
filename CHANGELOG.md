# Changelog

All notable changes to CampbellXGM FlowOS will be documented in this file.

## [1.1.0] - 2026-07-10

### Added
- **Auto-teardown** — Game mode now auto-restores when you leave a game (Usage Access permission required)
- **Auto-start game mode** — Monitors for game launches and activates game mode automatically
- **FPS overlay** — Shows real-time frame rate during gameplay
- **Keep Screen Awake** — Prevents screen from turning off while playing (acquires WakeLock for entire session)
- **Auto-Brightness Lock** — Disables auto-brightness to keep lighting consistent during gameplay
- **Notification Filter** — Only allows calls and priority messages during gameplay
- **Usage Access permission** in onboarding flow for foreground app detection
- **Expanded system package exclusion list** — 60+ Google/Android system packages now properly excluded from freezing
- **PID-level process killing** — Kills stubborn background processes by process ID
- **DNS failure feedback** — Shows error message when DNS change fails (requires Device Owner or WRITE_SECURE_SETTINGS)

### Fixed
- **CPU governor restore** — Saves original governor before setting "performance", restores the actual original (not hardcoded "schedutil")
- **Cooldown monitoring** — Restores CPU max frequency when temperature drops below 38°C
- **VPN blackhole** — Removed `addRoute("0.0.0.0", 0)` that routed all traffic into an unread TUN device, breaking device internet
- **Foreground service crash** — `startForeground()` now called before targetPackage check on Android 12+
- **Double restore guard** — `isRestored` flag prevents `restoreSystemState()` from running twice
- **SafetyInterceptor race condition** — Synchronized `pendingPackages` access to prevent TOCTOU race
- **System apps in selection dialog** — Google/Android system apps no longer appear in game selection
- **README ADB command** — Fixed package name from `com.example.campbellxgm` to `com.campbell.xgm`
- **POST_NOTIFICATIONS permission** — Now requested before launching game mode on Android 13+

### Improved
- **App freezing** — Now finds ALL running processes via `ActivityManager.runningAppProcesses`, not just launcher apps
- **Network boost** — Added actual network binding and background sync disable
- **Battery profile** — Now attempts to disable battery saver via `Settings.Global`
- **Wake lock** — Indefinite acquire for entire gameplay session (no more 10-minute timeout)
- **Freeze priority** — Falls back to kill + periodic re-kill even without Device Admin or Owner privileges

### Removed
- Dead stub files (`GameEngine.kt`, `DataRepository.kt`, `MainScreenViewModel.kt`, `StateSnapshotEntity.kt`, `CampbellAccessibilityService.kt`)
- Unused `isAllowed` field from `GameTargetEntity`
- Dead pre-Oreo code branch in FPS overlay
- Unused imports (`Handler`, `Looper`, `Build`, `WifiManager`)

## [1.0.0] - 2026-06-01

### Added
- Initial release
- Aggressive app freezing via Device Owner, Device Admin, or Accessibility
- Ping stabilizer VPN engine
- DNS provider selection (Google, Cloudflare, OpenDNS, Quad9, AdGuard)
- Do Not Disturb mode
- CPU/GPU tuner (Device Owner only)
- Battery profile
- Storage cleaner
- Network boost
- Cooldown monitoring with thermal throttling
- FPS overlay
- Settings dashboard with 13 toggles
- Device Owner and Device Admin management
- Accessibility service for force-stopping apps
- Sleek Alienware-style UI with Jetpack Compose
