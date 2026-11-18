# Step 0: feasibility spike

Goal: find out which kind of app Android Auto launches when it is installed outside Google Play
with "Unknown sources" turned on.

| Spike | Module | Category | Template/Type |
| --- | --- | --- | --- |
| A-IOT | `:spikes:car` flavor `iot` | `androidx.car.app.category.IOT` | PaneTemplate |
| A-POI | `:spikes:car` flavor `poi` | `androidx.car.app.category.POI` | PaneTemplate |
| B | `:spikes:media` | media app | browsable text rows |

## Run

1. `scripts/setup-dhu.sh` (one time; installs DHU, Play Store emulator image, creates AVD `autoscope_api36`).
2. Start the AVD, sign in to Google, update **Android Auto** from the Play Store.
3. Android Auto > tap "Version" about 10 times > developer settings. Enable "Unknown sources",
   then "Start head unit server".
4. `scripts/install-spikes.sh`
5. `scripts/run-dhu.sh`, open the launcher in the DHU. Check each spike. Test each one **with and
   without** "Unknown sources" enabled. Repeat on a real phone if you have one.

## Results

Environment: Android 15 (SDK 35) arm64 emulator, Android Auto 18.0.664264-release, DHU 2.0 (build 2022-03-30), Car App API level reported by host: **8**.

| Spike | Unknown sources ON | Unknown sources OFF | Notes |
| --- | --- | --- | --- |
| A-IOT | launches, PaneTemplate renders | not tested | two Spike A screenshots received, IOT/POI not told apart |
| A-POI | launches, PaneTemplate renders | not tested | |
| B media | launches, tabs Overview/Device, rows readable | not tested | rows render as tappable list with chevron |

Limits of this result: DHU is a developer tool and may skip checks a real car enforces. Real-car test and
the "Unknown sources OFF" runs are still open.
