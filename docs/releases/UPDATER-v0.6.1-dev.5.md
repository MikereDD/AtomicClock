# Updater implementation — v0.6.1-dev.5

This milestone hardens the already proven secure updater.

The Typezer∅ Android Updater Standard requires abandoned/obsolete update files to
be cleaned and installation permission to remain clearly distinct from update
availability. This pass closes both lifecycle gaps.

## Staging lifecycle

Update payloads live only under Atomic Clock's app-controlled cache directory:

`cache/updates/<version>/`

Rules:

1. incomplete or failed validation deletes that version's staging directory;
2. `.part` files are removed by the downloader on all exits;
3. abandoned version directories expire after 24 hours;
4. after a successful upgrade, the new application version deletes its matching
   staging directory on startup;
5. the private FileProvider remains scoped only to the `updates/` cache subtree.

## Permission lifecycle

If Android does not yet permit Atomic Clock to request package installs, updater
state becomes `PermissionRequired`.

The action is **Continue installation**:

- without permission, Android's per-app install-permission screen opens;
- after permission is granted and the user returns, the same action retries the
  fully verified installer handoff;
- all trust checks are repeated immediately before handoff.

No silent install or permission bypass exists.
