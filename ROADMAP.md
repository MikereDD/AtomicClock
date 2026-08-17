# Atomic Clock Roadmap

Atomic Clock is moving toward a polished v1.0 release with two priorities:
dependable engineering underneath and a top-tier visual identity on top.

## v0.6.0 — Battery & Reliability

- Reduce unnecessary background wakeups and network work.
- Keep NTP and weather refresh freshness-aware.
- Reuse cached location between occasional live background fixes.
- Respect battery and network constraints.
- Avoid redundant widget redraws and duplicate foreground refreshes.
- Preserve the existing widget appearance while hardening its behavior.

## v0.7.0 — Secure Updates & Release Integrity

- [x] Add Stable and Development update channels.
- [x] Add exact Typezer∅ version comparison and updater-protocol compatibility.
- [x] Pin Atomic Clock's Android signing certificate locally.
- [x] Add independent Typezer∅ detached RSA/SHA-256 release signatures.
- [x] Pin the detached release public-key identity locally.
- [x] Generate schema-2 release manifests with source/tag/commit provenance.
- [x] Download APK + `.apk.sig` into app-controlled staging.
- [x] Verify sizes, SHA-256, detached signature, package ID, version, and APK signer.
- [x] Repeat trust validation at the final Android installer boundary.
- [x] Add permission/retry/offline/staging-cleanup hardening.
- [x] Validate normal signed upgrades and fail-closed tamper/trust cases on-device.

## v0.8.0 — Performance + Visual Redesign

The major Dial-widget portion of the visual milestone was implemented ahead of
schedule during the v0.6 development cycle.

### Performance

- [ ] Profile startup and rendering paths.
- [ ] Remove redundant allocations/work.
- [ ] Tighten Compose recomposition behavior.
- [ ] Prepare the rendering architecture for richer clock visuals without sacrificing efficiency.

### Visual identity

- [ ] Redesign the main Atomic Clock app around a precision-instrument identity.
- [x] Preserve the existing home-screen widget as **Classic**.
- [x] Introduce the precision **Dial** widget as the visual showpiece.
- [x] Build a curated Dial theme system with **Midnight**, **Retro Brass**, **Arctic**, and **Emerald**.
- [x] Keep all Dial themes on one verified mechanical geometry while allowing distinct faces and hands.
- [x] Polish the Dial date, weather, humidity, and NTP drift hierarchy.
- [ ] Complete the remaining 12/24-hour presentation review and final main-app visual redesign.

### Dial widget inspiration

The original idea for Atomic Clock's Dial widget is inspired by **TMWrath's Home
Launcher Widget from the Elysium project**. Atomic Clock uses its own
implementation and evolves the concept around its own visual design, 12/24-hour
dial modes, theme system, NTP integration, and Atomic Clock-specific functionality.

## v0.9.0 — Release Candidate

- Bug fixes only unless a change is necessary for release quality.
- Long-running battery and widget reliability testing.
- Reboot, process-death, launcher-resize, permission, offline, travel/location,
  and Battery Saver testing.
- Final documentation and release packaging review.

## v1.0.0 — Stable

A production-ready Atomic Clock with reliable NTP time, weather,
battery-conscious background behavior, secure signed updates, the preserved
Classic widget, and the precision-instrument visual identity.
