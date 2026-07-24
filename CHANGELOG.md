# Changelog

All notable changes to CampbellXGM FlowOS will be documented in this file.

## [1.7.0] - 2026-07-24

### Added
- **Split APKs** — The release is now divided into architecture-specific APKs (arm64-v8a, armeabi-v7a, x86, x86_64) to significantly reduce the download size for individual devices. A universal APK is still available.

### Fixed
- **Memory Leaks** — Fixed `StaticFieldLeak` issues in `PermissionsViewModel` and `SafetyInterceptor` to ensure proper garbage collection of contexts.
- **Recomposition Performance** — Fixed an issue in `Navigation.kt` where a Jetpack Compose screen was observing `UpdateUiState` incorrectly.
- **UI Drawing Efficiency** — Fixed `GhostFingerSpeedometerView.kt` where multiple object allocations were happening inside the `onDraw` method, which is now optimized.
- **Resource IDs** — Fixed `GhostFingerOverlay.kt` improperly using hardcoded integer IDs.

## [1.6.0] - 2026-07-22

### Added
- **Ghost Finger Speedometer** — Animated visual overlay during the force-stop sequence to provide real-time feedback on apps frozen. Can be toggled in Settings.
- **Robust Force-Stopping** — Replaced the event-driven accessibility force-stop with a sequential polling mechanism, significantly improving reliability and preventing race conditions.

## [1.5.0] - 2026-07-15

### Added
- **In-app auto-update** — On launch the app checks GitHub Releases for a newer version and prompts to update automatically.
- **Update screen** — Shows the new version, release notes, and a live download progress bar with one-tap install.
- **Manual update check** — `Settings → App Updates → Check for Updates` lets you check and install updates any time.
- **Skip memory** — A skipped update is remembered and won't re-prompt until a newer version is published.
- **GitHub Releases distribution** — Signed release APKs are published to GitHub Releases (replacing the old `downloads/` folder); the updater downloads the APK directly from the release asset.
- **CI release pipeline** — `.github/workflows/release.yml` builds a signed APK and publishes it to GitHub Releases on a `v*` tag push or manual dispatch.

### Changed
- README download link now points to GitHub Releases instead of the local `downloads/` folder.

## [1.4.1] - 2026-07-13

### Security
- **Keystore credentials** — Release signing now reads from environment variables (`CAMPBELL_KEYSTORE_*`) with the gitignored `keystore.properties` as fallback; no plaintext secret in `build.gradle.kts`.
- **Backup attack surface** — `android:allowBackup` set to `false` to prevent adb backup extraction of stored preferences (exclusions, per-game profiles).
- **Broadcast safety** — `SafetyInterceptor` now registers its internal receiver via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`, avoiding a `SecurityException` crash on Android 13+.

### Fixed
- **Media during game mode** — Aggressive app freezing no longer kills background music/streaming apps. Active media sessions are detected (via a new `MediaSessionListenerService`) and excluded from freezing, both at launch and on every 5s freeze cycle.
- **Stats overlay CPU%** — Now computed from a two-sample `/proc/stat` delta instead of meaningless cumulative boot counters.
- **FPS overlay** — Uses a per-package frame baseline, so FPS is no longer corrupted when the foreground app changes.
- **Remove game** — Removing a game now only stops Game Mode if it is the actively running session (previously it could tear down a different active session).
- **Ping Stabilizer** — VPN service now posts a foreground notification, preventing Android 8+ from killing it.

### Changed
- **Ghost Finger (Accessibility force-stop)** — Now OFF by default and gated behind an explicit opt-in toggle with a warning. Previously it ran whenever the Accessibility service was enabled, hijacking the screen by opening each app's Settings page during gameplay.
- **Game Launch Monitor** — Poll interval 3s → 5s; dropped the deprecated `getRunningServices` API in favor of `PipelineService.isRunning`.
- **Per-game profile parsing** — Profile JSON is now parsed once per launch instead of ~15 times.

### UI
- **Settings** — Cooldown Mode, Network Boost, and Storage Cleaner now show a "Limited without Device Owner/root" note when the device lacks full system privileges.
- **Permissions** — Added an optional "Notification Access (Keep Media)" row used by the media-keep-alive feature.

## [1.4.0] - 2026-07-13

### Security
- **Keystore credentials** — Moved hardcoded signing credentials from build.gradle.kts to gitignored `keystore.properties`
- **Input validation** — Added hostname sanitization and permission pre-checks for all `Settings.Global` writes

### Performance
- **Parallel pipeline init** — Game mode activation now initializes all subsystems (DND, network boost, screen awake, etc.) concurrently via structured coroutines
- **Reduced polling** — FPS overlay interval 1s → 3s, Game Launch Monitor interval 3s → 8s
- **Memory efficiency** — App selection dialog no longer pre-loads all 400+ app icons into memory; icons fetched lazily per-row
- **UI thread offload** — Exclusion list dialog loads installed apps on background thread with loading indicator

### Reliability
- **Process death resilience** — Original system state (DND filter, brightness mode, CPU governor, WiFi logging, sync state) now persisted to SharedPreferences before mutation; auto-restored on service restart
- **SafetyInterceptor race fix** — Replaced fragile `AtomicBoolean` + `CopyOnWriteArrayList` with `synchronized` state machine; all `pendingPackages` accesses now atomic
- **OEM compatibility** — Added 5 vendor-specific Force Stop button resource IDs (Samsung, MIUI, OxygenOS, AOSP)

### Code Quality
- **Deduplication** — Consolidated 3 duplicate foreground app detectors into `ForegroundAppDetector`, 2 duplicate `getDirSize`/`deleteDir` into `FileUtils`, 3 duplicate permission check implementations into `PermissionUtils.checkAllPermissions()`
- **Dead code removed** — Removed orphaned `isDefaultNetworkActive` query in `enableNetworkBoost()`
- **Unused dependencies** — Removed Room, Navigation3, and lifecycle-viewmodel-nav3 from version catalog
- **Version sync** — AboutScreen now reads version dynamically from `PackageManager` instead of hardcoded `1.2.0`

### Repository Layer
- **`GameRepository`** — Interface + `SharedPrefsGameRepository` implementation for testable data access
- **`SettingsRepository`** — Centralized SharedPreferences management for all app settings

### UI
- **Widget** — Home screen widget now displays the first configured game name on the toggle button
- **Stats overlay** — Replaced emoji with text symbols for consistent cross-device rendering

## [1.3.0] - 2026-07-07

### Added
- **App Exclusion List** — prevent specific apps from being frozen during Game Mode
- **Home Screen Widget** — toggle Game Mode directly from your home screen
- **Per-Game Settings** — configure individual engine profiles for each game
- **System Stats Overlay** — floating HUD showing live RAM, CPU, and Battery usage
- **First-Run Tutorial** — onboarding walkthrough explaining permissions and features
- **Dark/Light Theme Toggle** — switch between dark and light themes in Settings

### Fixed
- Improved notifications — Game Mode notification now shows the active game name
- Fixed app name — display name now shows as 'CampbellXGM' instead of 'campbellxgm'

## [1.2.0] - 2026-07-01

### Fixed
- Fixed app freezing — now uses layered approach: killBackgroundProcesses + Accessibility force-stop + periodic re-kill
- Fixed auto-teardown — now uses queryEvents() for accurate foreground detection when returning to home screen
- Fixed FPS overlay — now measures actual game FPS via dumpsys gfxinfo instead of measuring its own UI thread
- Fixed keep screen awake — added missing WAKE_LOCK permission
- Fixed cache cleaner — no longer deletes the app's own cache on every game launch
- Fixed thread safety — all state variables now use @Volatile for cross-thread visibility
- Fixed race condition — restoreSystemState() now uses atomic flag to prevent double-restore
- Fixed stopForeground — notification is now properly removed when game mode ends
- Fixed SafetyInterceptor — added 12 language variants for Force Stop button, handles disabled state, resource ID search
- Fixed SafetyInterceptor thread safety — callback uses AtomicReference, pending list uses CopyOnWriteArrayList
- Fixed GameLaunchMonitorService — no longer spawns duplicate coroutines, reloads game list dynamically, doesn't stop when no games saved
- Fixed navigation — destination check now includes UsageStats permission, auto-navigates to permissions if any are revoked
- Fixed usage stats check — now uses AppOpsManager for reliable permission detection
- Fixed auto-start toggle — disable now uses stopService() directly
- Fixed game removal — removing a game now stops PipelineService if it's running
- Fixed DashboardScreen — removed broken raw permission request

### Improved
- VPN keepalive — TUN device now properly routes traffic
- Localization — SafetyInterceptor handles Chinese, Korean, Japanese, French, German, Spanish, Italian

### Removed
- No-op PID killing that failed silently on Android 8+
- Bluetooth and NFC from system exclusion list to prevent breaking accessories

### Added
- Release signing keystore for Play Store distribution
- ProGuard rules for coroutines, enums, and Compose
