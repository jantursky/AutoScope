# AutoScope

Android Auto diagnostics. AutoScope runs on the car screen through Android Auto and shows what the host, the screen, the touch layer and the car hardware report. A phone screen shows the same live report and saves it as JSON and text.

It is a developer tool for personal use, installed outside Google Play. Everything stays on the device: there is no network permission and no analytics.

## Status

Built with `./gradlew assembleDebug`, 80+ unit tests on the JVM. Run on the Desktop Head Unit (DHU) so far: only the Step 0 spikes (see [SPIKE.md](SPIKE.md)). The full app is compiled and unit tested but not yet run on a DHU or in a car. Open points are listed in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

## What is in the car

Car UI uses `androidx.car.app` 1.7.0 templates only, category **POI**. Four tabs (the library allows at most four):

| Tab | Content |
| --- | --- |
| Overview | "Diagnostic tool" marker, Car App API level, **Mark this moment**, connection, host, phone, performance, link to the lifecycle log |
| Screen | Screen size, density, dark mode, locale, plus the template constraints |
| Touch | Opens a map screen with a drawing surface: crosshair, trail of the last 10 events, counters, events per second. Falls back to a tap latency test when no surface arrives |
| Hardware | Car info and sensors, plus a "Grant permissions" row |

On hosts below Car App API level 6 a list menu replaces the tabs. Templates refresh on a timer (default 1 s, 0.5 to 10 s, set on the phone) and only when the visible data changed.

## Setup

1. **Developer mode in Android Auto.** Phone Settings > Apps > Android Auto > Additional settings in the app. Scroll to "Version and permission info" and tap it about 10 times, then confirm.
2. **Unknown sources.** Open the three dot menu > Developer settings and enable "Unknown sources". The spikes launched on the DHU with this on and off; a real car may behave differently.
3. **Install.** `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. **Permissions.** Open AutoScope on the phone once. Car data permissions (speed, fuel, mileage) and location are granted from the Hardware tab ("Grant permissions"), which shows the dialog on the phone while the car is parked.

### Desktop Head Unit

```sh
scripts/setup-dhu.sh      # once: DHU, Play Store emulator image, AVD
scripts/run-dhu.sh        # starts the DHU, forwards port 5277
scripts/run-dhu.sh shot name   # screenshot into dhu-screenshots/
```

On the phone or emulator start Android Auto's "Start head unit server" (three dot menu in developer settings) before launching the DHU. The emulator needs the Play Store image and an updated, signed in Android Auto.

### Real car

Connect the phone, enable developer mode and "Unknown sources" as above, and look for AutoScope in the car's app launcher. If it does not show up, check the launcher customisation in Android Auto settings. Wired and wireless projection both work in principle; the Wi-Fi fields on the phone matter most for wireless.

## Reports

"Mark this moment" in the car and "Save snapshot" on the phone write `autoscope-<time>-<reason>.json` and `.txt` to the app's files directory. A report is also saved on every connection state change. The phone screen lists saved reports and shares them with `ACTION_SEND_MULTIPLE`.

## Fields

Status is one of OK, UNAVAILABLE, PERMISSION_MISSING, NOT_SUPPORTED_BY_HOST. "Tested on" is the Android Auto version the field was checked on; **not yet** means compiled and unit tested only.

| Field | API | Permission | Tested on |
| --- | --- | --- | --- |
| Connection type, time connected, reconnects | `CarConnection.getType` | none | not yet |
| Car App API level | `CarContext.getCarAppApiLevel` | none | 18.0.664264 (spike, level 8) |
| Host package, uid | `CarContext.getHostInfo` | none | not yet |
| Car App Library version | `R.string.car_app_library_version` (not public API) | none | not yet |
| Android Auto version name, code | `PackageManager.getPackageInfo` | `<queries>` entry (API 30+) | not yet |
| Phone Android, SDK, manufacturer, model | `Build` | none | not yet |
| Screen width, height, dpi, density, scaled density, dp sizes, smallest width, aspect ratio, orientation | `CarContext` resources, `DisplayMetrics`, `Configuration` | none | not yet |
| Refresh rate | `Display.getRefreshRate` (library virtual display, Android 11+) | none | not yet |
| Visible and stable area | `SurfaceCallback.onVisibleAreaChanged`, `onStableAreaChanged` | `androidx.car.app.ACCESS_SURFACE` | not yet |
| Dark mode, UI mode night, font scale, locale | `CarContext.isDarkMode`, `Configuration` | none | not yet |
| List, grid, pane, place list, route list limits | `ConstraintManager.getContentLimit` | none | not yet |
| App driven refresh | `ConstraintManager.isAppDrivenRefreshEnabled` (API 6) | none | not yet |
| Template step limit, refresh limit | documented values, not measured | none | n/a |
| Make, model, year | `CarInfo.fetchModel` | none on Android Auto | not yet |
| EV connector types, fuel types | `CarInfo.fetchEnergyProfile` | `com.google.android.gms.permission.CAR_FUEL` | not yet |
| Speed (raw, display) | `CarInfo.addSpeedListener` | `com.google.android.gms.permission.CAR_SPEED` | not yet |
| Odometer | `CarInfo.addMileageListener` | `com.google.android.gms.permission.CAR_MILEAGE` | not yet |
| Battery level, fuel level, range | `CarInfo.addEnergyLevelListener` | `com.google.android.gms.permission.CAR_FUEL` | not yet |
| Toll card | `CarInfo.addTollListener` | none | not yet |
| EV charge port | `CarInfo.addEvStatusListener` (experimental) | none documented | not yet |
| Accelerometer, gyroscope, compass | `CarSensors` | none documented | not yet |
| Car location | `CarSensors.addCarHardwareLocationListener` | `android.permission.ACCESS_FINE_LOCATION` | not yet |
| Touch events, counters, events per second | `SurfaceCallback` via `AppManager.setSurfaceCallback`, `MapWithContentTemplate` | `ACCESS_SURFACE`, `MAP_TEMPLATES` | not yet |
| Tap latency | `Screen.invalidate` to `onGetTemplate` | none | not yet |
| Session to first template, invalidate to template, templates per minute | `Session`, `Screen` timing | none | not yet |
| Java heap, native heap, process PSS | `Runtime`, `Debug` | none | not yet |
| Lifecycle log | `Session`, `Screen` lifecycle, `onCarConfigurationChanged` | none | not yet |
| Battery state, percent, temperature | `ACTION_BATTERY_CHANGED` | none | n/a (phone) |
| Thermal status | `PowerManager.getCurrentThermalStatus` (API 29+) | none | n/a (phone) |
| Power save mode | `PowerManager.isPowerSaveMode` | none | n/a (phone) |
| Bluetooth state | `BluetoothAdapter.getState` | none declared | n/a (phone) |
| Wi-Fi state, band, link speed | `WifiManager`, `WifiInfo` | `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` | n/a (phone) |
| Free memory, low memory | `ActivityManager.getMemoryInfo` | none | n/a (phone) |

## Layout

```
core/      pure Kotlin data layer: Field, Status, DiagnosticsRepository, report formats, ReportStore
app/       Android app: car side (car/), phone side (phone/), wiring (AppContainer)
spikes/    Step 0 feasibility spikes: car app (IOT, POI) and media app
scripts/   DHU setup, run, screenshots
```

## License

MIT
