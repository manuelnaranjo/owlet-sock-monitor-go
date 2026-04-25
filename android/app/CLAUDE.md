# Android App — Owlet Monitor

Single APK that runs on three form factors: **Android TV**, **Android phone**, and **Android Auto**.

Package: `com.owletmonitor.tv`  
Build system: **Bazel** (no Gradle). Build with:
```
bazel build //android/app:owlet_tv
```
Output: `bazel-bin/android/app/owlet_tv.apk`

---

## Form Factors

### Android TV
- **Entry point**: `MainActivity` — a settings screen that directs the user to enable the screensaver in system settings. Uses `LEANBACK_LAUNCHER` intent filter; shows up in the Google TV launcher.
- **Screensaver**: `OwletDreamService` — a `DreamService` that displays vitals full-screen. Runs a slow drift animation (40dp range, 20s cycle) to prevent OLED burn-in. Refreshes data every 30 seconds via `VitalsRepository`.

### Phone
- **Entry point**: `PhoneMainActivity` — a portrait-mode `Activity` with `LAUNCHER` intent filter. If run on a TV device (detected via `FEATURE_LEANBACK`), it immediately redirects to `MainActivity`.
- **Layout**: `res/layout/activity_phone_main.xml` — large cyan oxygen value, pink heart rate value, green status, dim timestamp, refresh button.

### Android Auto
- **Entry point**: `OwletCarAppService` (a `CarAppService`) declared with the `androidx.car.app.CarAppService` intent action.
- **Session**: `OwletCarSession` creates a `VitalsCarScreen`.
- **Screen**: `VitalsCarScreen` uses a `PaneTemplate` with two `Row`s (oxygen, heart rate). Custom views are not allowed in Android Auto — templates only.
- Uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (fine for personal use; restrict to OEM host signatures for Play Store).

---

## Data Layer

**`VitalsData`** — plain data class: `oxygenPercent: Int?`, `heartRateBpm: Int?`, `fetchedAtMs: Long`.

**`VitalsRepository`** — Kotlin `object` singleton shared by all three form factors. Currently returns **mock data** (98% O₂, 125 bpm).

```
TODO: replace mock with real fetch from Grafana Cloud Prometheus.
```

Usage pattern (all consumers follow this):
```kotlin
// start
VitalsRepository.addListener(myListener)
VitalsRepository.fetchNow()

// stop
VitalsRepository.removeListener(myListener)
```

Listeners are always called on the main thread.

---

## Key Files

| File | Purpose |
|---|---|
| `AndroidManifest.xml` | Declares all three form-factor entry points; leanback is `required="false"` |
| `MainActivity.kt` | TV settings screen — opens system screensaver settings |
| `OwletDreamService.kt` | TV screensaver with drift animation + VitalsRepository integration |
| `PhoneMainActivity.kt` | Phone launcher activity |
| `OwletCarAppService.kt` | Android Auto `CarAppService` |
| `OwletCarSession.kt` | Android Auto `Session` |
| `VitalsCarScreen.kt` | Android Auto `Screen` using `PaneTemplate` |
| `VitalsRepository.kt` | Shared data singleton (mock data, future: Grafana Cloud) |
| `VitalsData.kt` | Shared data class |
| `res/layout/activity_main.xml` | TV settings layout (black bg, remote-nav button) |
| `res/layout/dream_layout.xml` | TV screensaver layout (full-screen, drift container `widget_container`) |
| `res/layout/activity_phone_main.xml` | Phone portrait layout |
| `res/xml/network_security_config.xml` | Permits cleartext HTTP to localhost and 192.168.* for future data fetching |

---

## Build / Dependencies

- **`BUILD.bazel`**: one `kt_android_library` (all `.kt` sources + resources) + one `android_binary`. `minSdkVersion=23` (required by Car App Library).
- **`MODULE.bazel`**: uses `rules_jvm_external` (version 7.0) with `maven.install` to pull `androidx.car.app:app:1.7.0`. Lock file: `//:maven_install.json`. The `aar_import_bzl_label = "@rules_android//android:rules.bzl"` attribute is required because the project uses `rules_android` (not the legacy `build_bazel_rules_android`).
- To update Maven deps: `REPIN=1 bazel run @maven//:pin`

---

## SDK Versions

- `minSdkVersion`: 23 (Android 6.0) — Car App Library minimum
- `targetSdkVersion`: 34 (Android 14)
