# Known limitations

Nothing in the app has run on a DHU or in a car yet. The Step 0 spikes launched on a DHU (Android 15 emulator, Android Auto 18.0.664264, Car App API level 8), with "Unknown sources" on and off.

## Library limits

- **Four tabs, not six.** `TabTemplate` allows 2 to 4 tabs. Constraints share the Screen tab, the lifecycle log is one screen deeper, and phone data sits in Overview.
- **Paging is limited by depth.** A host allows 5 templates per task. Extra pages open as new screens, so a list goes at most 4 screens deep. Rows beyond that are only in the saved report.
- **No refresh rate number is documented.** The constraints tab says so instead of showing a measured value.
- **Content limits.** `ConstraintManager` returns the library default when the host call fails, so that case looks like a real value.
- **Rows are short.** A row holds two text lines of 60 characters; longer values are cut with "…".

## Touch and surface

- Whether a POI app's `MapWithContentTemplate` gets a surface and gesture callbacks on a real host is untested. Gestures need the `Action.PAN` button. If no surface arrives within 3 s the tester shows UNAVAILABLE and a tap latency test replaces it.
- Visible and stable area are known only after the touch tester was opened, and only on Car App API 7+.

## Hardware

- Speed, fuel and mileage need the user to grant car permissions on the phone. A missing grant can show as an error or an empty value, depending on the host.
- `EvStatus` is experimental in the library and has no documented Android Auto permission.
- Odometer comes from `Mileage.getOdometerMeters`, which returns kilometers according to the docs.
- Sensors return `STATUS_UNIMPLEMENTED` on Android Automotive OS. AutoScope targets Android Auto.

## Values

- The reported refresh rate belongs to the library's virtual display, not the real panel, and needs Android 11.
- The library version comes from a non-public resource string and may change in a later release.
- Process PSS is sampled at most every 10 s.
- Thermal status is unavailable on Android 9 (API 28).
- Wi-Fi band and speed describe the phone's current Wi-Fi network, not necessarily the wireless Android Auto link. On API 28 they come from the deprecated `WifiManager.connectionInfo`.
- "Time connected" counts from the first value the process saw. "Reconnects" counts connections after the first one in this process.
- Android Auto version fields need the `<queries>` entry on API 30+.

## Distribution

- The host validator accepts any host (`ALLOW_ALL_HOSTS_VALIDATOR`). That is fine for a sideloaded personal tool and must not ship to Google Play.
- IOT is not used: it is experimental in the library. If only a media app launches on some real car, the Step 0 spike B shows the fallback, which would lose the touch tester, surface data and template limits.
