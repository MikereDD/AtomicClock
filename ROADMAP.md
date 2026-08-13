# Atomic Clock Roadmap

Atomic Clock is moving toward a polished v1.0 release with two priorities: dependable engineering underneath and a top-tier visual identity on top.

## v0.6.0 — Battery & Reliability

- Reduce unnecessary background wakeups and network work.
- Keep NTP and weather refresh freshness-aware.
- Reuse cached location between occasional live background fixes.
- Respect battery and network constraints.
- Avoid redundant widget redraws and duplicate foreground refreshes.
- Preserve the existing widget appearance while hardening its behavior.

## v0.7.0 — Performance

- Profile startup and rendering paths.
- Remove redundant allocations/work.
- Tighten Compose recomposition behavior.
- Prepare the rendering architecture for richer clock visuals without sacrificing efficiency.

## v0.8.0 — Visual Redesign

- Redesign the main Atomic Clock app around a precision-instrument identity.
- Preserve the current home-screen widget permanently as **Classic**.
- Introduce the new **Dial** widget as the visual showpiece.
- Support both **12-hour** and **24-hour** dial modes.
- Build a curated theme system, initially targeting **Midnight**, **Retro Brass**, **Arctic**, and **Emerald**.
- Keep themes visually distinct rather than simple recolors of one surface.

### Dial widget inspiration

The original idea for Atomic Clock's Dial widget is inspired by **TMWrath's Home Launcher Widget from the Elysium project**. Atomic Clock will use its own implementation and evolve the concept around its own visual design, 12/24-hour dial modes, theme system, NTP integration, and Atomic Clock-specific functionality.

## v0.9.0 — Release Candidate

- Bug fixes only unless a change is necessary for release quality.
- Long-running battery and widget reliability testing.
- Reboot, process-death, launcher-resize, permission, offline, travel/location, and Battery Saver testing.
- Final documentation and release packaging review.

## v1.0.0 — Stable

A production-ready Atomic Clock with reliable NTP time, weather, battery-conscious background behavior, the preserved Classic widget, and the new precision-instrument visual identity.
