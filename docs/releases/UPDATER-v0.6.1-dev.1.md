# Atomic Clock updater — v0.6.1-dev.1

This development milestone implements the safe update-discovery and eligibility layer from the Typezer∅ Android Updater Standard.

Implemented:
- exact numeric Typezer∅ version comparison;
- Stable/Development channel separation;
- approved HTTPS release-origin enforcement;
- GitHub release discovery;
- manifest schema/app/platform/channel validation;
- updater protocol compatibility validation;
- locally pinned package ID and APK signer certificate;
- locally pinned detached-signature key ID;
- safe rejection of missing/legacy/incompatible manifests;
- manual Settings update check.

Not implemented yet (therefore installation is intentionally unavailable):
- detached release-key generation/public-key pinning;
- APK + `.sig` download to app-controlled staging;
- size/SHA-256 validation;
- detached signature cryptographic verification;
- APK package/certificate inspection at the final trust boundary;
- FileProvider/system package-installer handoff;
- staging cleanup and cancellation behavior;
- full regression/tamper validation record.

The updater must remain fail-closed until those items are complete.
