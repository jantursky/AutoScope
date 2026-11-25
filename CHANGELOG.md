# Changelog

All notable changes to AutoScope. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow [Semantic Versioning](https://semver.org/). The short store text for each build is in `fastlane/metadata/android/en-US/changelogs/`.

## [Unreleased]

## [1.2.0] - 2026-11-25

### Added
- CI runs Android lint and enforces a line coverage floor on every push and pull request.
- A `v*` tag builds the signed release APK and creates the GitHub release with it attached.
- This changelog.

## [1.1.2] - 2026-11-18

### Added
- Phone Permissions tab has a "Grant car permissions" button. The in-car dialog is blocked while Android Auto thinks the car is driving.

### Changed
- Wi-Fi, location, GPS and Bluetooth are optional hardware features, so more devices can install the app.

## [1.1.1] - 2026-11-18

### Changed
- Release builds accept only the Android Auto host (`com.google.android.projection.gearhead`), checked by signing certificate digests. Debuggable builds still accept any host.

## [1.1.0] - 2026-11-18

### Added
- Media entry point, so a sideloaded install still launches on a real Android Auto host (phone side data only).
- "Media only" verdict on the phone Car tab when a car session has no car app.
- Demo data mode for screenshots (`--ez demo true`).
- Privacy policy and store graphics.

### Fixed
- Crash when the media service was created.

## [1.0.0] - 2026-11-18

First release: car templates (Overview, Screen, Hardware, More), touch tester and Visual dashboard, plus a phone app with the same live report, saved JSON and text reports, and a Changes view.
