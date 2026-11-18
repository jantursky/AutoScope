# Known limitations

Tested so far: the Step 0 spikes, an earlier build of the app on a DHU (Android 15 emulator, Android Auto 18.0.664264, Car App API level 8) and the 1.1.0 build in a real car, a 2017 Mazda CX-5 over USB (Android Auto 18.0.664264, Car App API level 8, parked) installed from the Google Play internal testing track. Not tried in a car: driving, wireless projection, other cars and other Android Auto versions, touch events on the car screen.

## Real cars: sideloaded installs run as a media app only

With a sideloaded APK and "Unknown sources" on, the Mazda's host validator denies the Car App Library part of AutoScope (`CAR.VALIDATOR: Package DENIED; failed all other checks`, Play service: "app owners empty"), so the template tabs never start. The DHU does not enforce this. A sideloaded media app is accepted, so AutoScope also ships a media entry point: Overview, Network, Permissions, Log and "Mark this moment" as browse rows, with phone side data only. The phone's Car tab then says "Media only".

Installed from Google Play (internal testing), the full template UI starts on the same car: the four tabs, the touch tester screen and the Visual dashboard open and show real values. The Play build is signed with Google's key, so a sideloaded build has to be uninstalled first.

What that Mazda reports: make, year, a hashed model and the screen size; display speed (0.0 while parked). It reports no value for odometer, fuel, battery, range, toll card or EV charge port, and "not implemented" for accelerometer, gyroscope, compass, car location, fuel types and EV connectors. The in-car "Grant permissions" dialog said it was not available while driving, so the permissions were granted with `adb shell pm grant`. Visible and stable area showed no value until a map screen was opened and were not checked afterwards.

## Library limits

- `TabTemplate` allows 2 to 4 tabs, so there are four, not six. Constraints share the Screen tab, phone data sits in Overview, and Touch, Visual, Sensors, Network, Permissions and Log are behind the More tab.
- Paging is limited by depth. A host allows 5 templates per task. Extra pages open as new screens, so a list goes at most 4 screens deep. Rows beyond that are only in the saved report.
- No refresh rate number is documented, so the constraints tab says so instead of showing a measured value.
- `ConstraintManager` returns the library default when the host call fails, so that case looks like a real value.
- A row holds two text lines of 60 characters; longer values are cut with "…". Car rows use short status words instead of full reasons; the phone and the saved report keep the full text.
- Live values do not tick. Speed, memory and other continuously changing values would reset the list scroll every refresh, so the car updates them only on "Refresh values". A list also counts as a refresh only while its row count and titles stay the same.

## Touch and surface

- Whether a POI app's `MapWithContentTemplate` gets a surface and gesture callbacks on a real host is untested. Gestures need the `Action.PAN` button. If no surface arrives within 3 s the tester shows UNAVAILABLE and a tap latency test replaces it.
- Visible and stable area are known only after the touch tester was opened, and only on Car App API 7+. The DHU did not report them.
- The Visual screen needs Car App API 7 and a map surface. On other hosts it shows a message instead. Its pane text wraps at 28 characters, and the speed arc is fixed at 0 to 200 km/h.

## Hardware

- Speed, fuel and mileage need the user to grant car permissions on the phone. A missing grant can show as an error or an empty value, depending on the host.
- `EvStatus` is experimental in the library and has no documented Android Auto permission.
- Odometer comes from `Mileage.getOdometerMeters`, which returns kilometers according to the docs.
- Accelerometer units (m/s²) and axes (x right, y up) are assumed from the docs and were never seen on a host. The DHU feeds no car data, so the compass, g-force dot, car rotation, energy bar and charge port have only been exercised with fakes.
- An energy percentage outside 0 to 100 is shown as missing.
- Sensors return `STATUS_UNIMPLEMENTED` on Android Automotive OS. AutoScope targets Android Auto.

## Values

- The reported refresh rate belongs to the library's virtual display, not the real panel, and needs Android 11.
- The library version comes from a non-public resource string and may change in a later release.
- Process PSS is sampled at most every 10 s.
- Thermal status is unavailable on Android 9 (API 28).
- Wi-Fi band and speed describe the phone's current Wi-Fi network, not necessarily the wireless Android Auto link. On API 28 they come from the deprecated `WifiManager.connectionInfo`.
- "Time connected" counts from the first value the process saw. "Reconnects" counts connections after the first one in this process.
- The Wi-Fi chip rates the phone's Wi-Fi: green needs 5 or 6 GHz at 200 Mbps or more, 2.4 GHz is never green.
- The phone's connection timeline keeps the last 50 changes and starts empty when the process starts.
- The screen drawing is to scale for the pixel size the host reports. Its diagonals only mark the corners; they carry no measurement.
- Android Auto version fields need the `<queries>` entry on API 30+.

## Phone app

- Copy report, Delete and Delete all are covered by unit tests but were not run on a device.
- The update time in the header can clip on screens narrower than about 360 dp.
- Report file names cut the reason after 32 characters.

## Distribution

- Release builds accept only the Android Auto host (`com.google.android.projection.gearhead`), checked by two signing certificate digests read from an installed Android Auto APK (`res/values/hosts_allowlist.xml`). An Android Auto build signed with another key would be refused. Debuggable builds accept any host.
- IOT is not used: it is experimental in the library. If only a media app launches on some real car, the Step 0 spike B shows the fallback, which would lose the touch tester, surface data and template limits.
