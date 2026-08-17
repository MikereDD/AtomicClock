## [0.7.0] - 2026-08-16

### Added
- Production-ready Typezer∅ Android self-updater with Stable and Development channels.
- Schema-2 release manifests with updater-protocol compatibility and Git source provenance.
- Independent RSA/SHA-256 detached release signatures and locally pinned release-key identity.
- Secure app-controlled update staging with size, SHA-256, package, version, and signing-certificate validation.
- Android system package-installer handoff with per-app install-permission support.
- Local release-manifest validation harness for reproducible fail-closed trust tests.

### Changed
- Update trust checks are repeated immediately before installer handoff.
- Updater now surfaces Downloading and Verifying states and clearer offline/timeout failures.
- Failed, rejected, abandoned, and successfully installed staging data is cleaned automatically.
- Mandatory manifests are presented as required updates without bypassing Android installer confirmation.
- Promoted the validated v0.6.1 development updater line to stable v0.7.0.

### Validated
- Release-signed dev.5 -> dev.6 in-app upgrade.
- Settings/widget persistence across normal signed update.
- Permission return flow and installer cancellation/retry.
- Tampered APK and detached-signature rejection.
- Unapproved release-key, wrong package ID, wrong APK signer, wrong channel, wrong public-key fingerprint, and incompatible updater-protocol rejection.
## 0.6.1-dev.6

- Dedicated updater hardening validation target for the v0.6.1 development line.
- No updater trust-model changes from dev.5.
- Used to validate install-permission retry, installer cancellation/retry, secure staging cleanup, and normal signed upgrade from dev.5.
## 0.6.1-dev.5

- Harden updater staging cleanup after failed, abandoned, and successfully installed updates.
- Surface the real cryptographic/package verification phase in updater status.
- Fix Android unknown-app-source permission return/retry behavior.
- Add clearer offline and timeout failures.
- Distinguish mandatory manifests as required updates without bypassing Android installer confirmation.
- Add the Typezer∅ release-validation checklist for the updater release candidate path.
## 0.6.1-dev.4

- First dedicated end-to-end updater target for the v0.6.1 development line.
- No updater trust-model changes from dev.3; this build exists to validate secure discovery, download, verification, and Android installer handoff from an installed dev.3 build.
## 0.6.1-dev.3

- Add secure staged download of APK and detached release signature.
- Verify exact asset sizes and SHA-256 digests before trusting downloaded files.
- Verify RSA/SHA-256 detached release signatures against the embedded pinned public key.
- Inspect downloaded APK package ID, version, and Android signing certificate.
- Repeat all critical verification immediately before Android installer handoff.
- Add private FileProvider installer handoff and Android unknown-source permission flow.
- Add updater download, verification, ready-to-install, permission, and installer UI states.
## 0.6.1-dev.2

- Add the permanent detached Typezer∅ release-signing trust anchor.
- Generate and immediately verify RSA/SHA-256 .apk.sig release signatures.
- Generate schema-2 manifests with exact signature metadata, updater protocol compatibility, and source provenance.
- Pin the release-key ID and SPKI SHA-256 in the Android updater.
- Embed only the verified public release key; private signing material remains outside the repository.
- Add runtime detached-signature verification support for the upcoming staged-download/install flow.
# Changelog

## 0.6.1-dev.1

- Begin Typezer∅ Android updater implementation.
- Add exact numeric Typezer∅ version parsing/comparison for stable and multi-part development versions.
- Add Stable/Development update-channel preference.
- Add approved-origin GitHub release discovery and schema/app/platform/channel/protocol validation.
- Pin Atomic Clock package ID and APK signing-certificate SHA-256 locally.
- Add Settings update-status UI and manual Check for updates action.
- Installation remains intentionally disabled until detached release signing and final trust-boundary verification are implemented.

All notable changes to Atomic Clock are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Added the precision **Dial** widget family with selectable **Midnight**, **Arctic**, **Retro Brass**, and **Emerald** themes.
- Added theme-specific face artwork and live hand sets while preserving one verified mechanical dial geometry.
- Added release tooling for signed APK builds, SHA-256 checksums, signer verification, release manifests, and GitHub release packaging.

### Changed
- Enlarged and repositioned the Dial date complication for stronger readability and better lower-center balance.
- Enlarged and lowered the weather and humidity complications, keeping both sides symmetrical across all four themes.
- Updated the About link and public documentation for the standalone GitHub repository.

## [0.6.0] - 2026-08-12

### Changed
- Began the **battery & reliability hardening** milestone on the road to v1.0.
- Background widget maintenance now runs hourly instead of every 15 minutes; the clock digits still advance independently through Android `TextClock`.
- Removed the redundant 30-minute AppWidget system update broadcast. WorkManager is now the single scheduler for network-backed widget data.
- Periodic work requires network connectivity and a battery-not-low state so nonessential refreshes are deferred when power is constrained.
- Background NTP sync and weather refresh are freshness-aware and skip network requests when cached data is still current.
- Background NTP uses two samples instead of four; the foreground/manual sync retains the higher-precision sampling path.
- Live background location is limited to roughly once every three hours, only with `Allow all the time` permission and while Battery Saver is off. Hourly weather can reuse cached coordinates between location fixes.
- Foreground auto-sync/weather cadence reduced from 10/15 minutes to 30 minutes.
- Duplicate launch/resume weather requests are coalesced with a short freshness throttle.
- Background-location guidance now clarifies that cached-location weather can still refresh without `Allow all the time`; that permission is needed for the widget to follow location changes while travelling.
- Widget storage/redraw is skipped when the snapshot has not changed.
- Debug-only refresh-reason logging added for periodic, widget, and boot-triggered maintenance.
- Removed temporary patch README files from the standalone source tree.
- Bumped app version to **0.6.0**.

### Preserved
- Existing widget appearance and behavior remain unchanged; this release is intentionally an engineering/battery pass.
- Manual refresh still bypasses the conservative automatic cadence.

## [0.5.1] - 2026-06-30

### Changed
- Polished the large widget layout so weather freshness appears on its own secondary line instead of crowding the weather row.
- Large widget weather row now prioritizes temperature, condition, humidity, and city.
- Weather freshness now uses muted text by default, amber after 1 hour, and red after 6 hours.
- Bumped app version to **0.5.1**.

## [0.5.0] - 2026-06-30

### Added
- **Clear background-location guidance** for automatic widget weather updates.
  Settings now tells the user whether widget weather can refresh while the app is closed and explains that Android requires Location set to **Allow all the time**.
- **Weather freshness timestamp** in the large widget. The widget now shows when the weather/city reading was last updated, so stale cached weather is obvious instead of mysterious.

### Changed
- Background widget update wording now changes based on permission state: location missing, background permission missing, or fully enabled.
- About screen continues to read the version from `BuildConfig.VERSION_NAME`, now bumped to **0.5.0**.

### Fixed
- Reduced confusion where the widget appeared broken even though Android was blocking background location. The app now guides users straight to the required permission path.

## [0.4.1] - 2026-06-22

### Added
- **Background widget updates** (opt-in, in Settings). With "Allow all the time"
  location granted, the 15-minute worker can refresh weather and city for a new
  location while the app is closed — so the widget keeps up as you travel instead
  of only updating when you open the app. Without it, the widget still updates on
  open and keeps its last good reading.
- **Background refresh** for the widget via WorkManager: the tile re-syncs time
  and re-fetches weather on its own roughly every 15 minutes, so it stays current
  without opening the app. Scheduled when a widget is placed and on app launch,
  and stopped when the last tile is removed.

### Changed
- The refresh button now also re-fetches weather (forcing a fresh location fix),
  and weather re-pulls whenever the app returns to the foreground. Previously the
  button only re-synced the clock and weather refreshed only every 15 minutes, so
  a reading could lag well behind real conditions.
- Widget weather line now reads `temp · condition … humidity · city` — the city
  trails the humidity, and the line is packed to the left rather than split to
  opposite edges.

### Fixed
- Current condition now reflects what's actually happening rather than the
  hour's forecast. Open-Meteo's `weather_code` covers a whole grid-cell hour, so
  it could announce a "Thunderstorm" while nothing was falling; when there's no
  current precipitation the app now shows the observed sky (clear / partly cloudy
  / cloudy / overcast) from cloud cover instead. Real precipitation still reads
  as drizzle/rain/snow/storm.
- Weather now resolves a **fresh location** instead of reusing a stale cached
  fix, so conditions and city update as you travel. Previously an old fix could
  make a reading like "Thunderstorm" persist from city to city while the actual
  sky had changed.
- The widget no longer blanks to "Weather unavailable" after a transient
  background failure. Snapshot writes now merge over the previous values, so a
  failed time sync or weather fetch keeps the last known reading.
- About dialog "View on GitHub" link corrected to the `Android/AtomicClock` path
  (it previously pointed at the old root path and 404'd).

## [0.3.0] - 2026-06-20

### Added
- Home-screen widget in two **separately pickable** sizes — a 2x1 and a 4x2 tile
  (both resizable). The time self-updates via `TextClock` (no service), alongside
  date, clock drift, sync source, and a weather line with **condition + humidity icons**.
  Tapping the widget opens the app.
- **Widget background** setting (Solid / Translucent / Clear), defaulting to
  Translucent so it sits lighter over the wallpaper.
- The app pushes a fresh widget snapshot after every time sync and weather refresh,
  and when units/format change; the system also refreshes it periodically.

## [0.2.0] - 2026-06-20

### Added
- Current weather: temperature, conditions, "feels like", humidity, wind (speed + direction), dew point, and city, shown beneath
  the clock. Powered by [Open-Meteo](https://open-meteo.com) — free and keyless.
- Coarse-location lookup via the platform `LocationManager` (no Play Services),
  with a graceful "Tap for weather" prompt when permission isn't granted yet.
- Independent unit toggles: temperature (°C / °F) and wind speed (km/h / mph), each defaulting by locale (US → °F + mph). Tap the temperature to switch °C / °F.
- Automatic weather refresh every 15 minutes.
- About screen with app version, credits (NTP sources, Open-Meteo), and a link to the repo.

### Changed
- Settings sheet now includes the temperature-unit selector.
- Added the `ACCESS_COARSE_LOCATION` permission (weather is fully optional;
  the clock works without it).

## [0.1.0] - 2026-06-20

### Added
- Initial release.
- SNTP (RFC 4330) client anchored to `SystemClock.elapsedRealtime` so corrected
  time stays valid even if the device wall clock is wrong or later changed.
- Best-of-N sampling per sync (keeps the lowest round-trip response) with
  automatic fallback across Google, Cloudflare, NTP Pool, Apple, and NIST.
- Frame-rate-smooth clock face with a sweeping millisecond ring and tabular figures.
- Live stats: clock drift vs. atomic time, estimated accuracy (±round-trip/2),
  and the active source server + stratum.
- Auto re-sync every 10 minutes plus a manual re-sync control.
- Settings (DataStore-persisted): 24-hour toggle, milliseconds toggle, server choice.
- Material 3 dark-first theme with edge-to-edge layout and an adaptive launcher icon.
