# GE Flipper 1.2.63 review validation

This documents reproducible checks for the logging and Modify concerns in [PR #550](https://github.com/chsami/Microbot-Hub/pull/550#pullrequestreview-5219550419), the GE warning/UI-read fixes, and preference/privacy boundaries.

## Scope

The update consists of GE Flipper production sources, automated tests, and its documentation. Client classes, other plugins, shared build configuration, generated release metadata, local diagnostics, backups, and personal session data are outside the change.

The plugin version and minimum client version are declared in `FlipperPlugin`. Minimum supported client version is 2.6.26, matching the verified client APIs. Builds target Java 11.

## Automated coverage

- Logging lifecycle: ROOT appenders, shared chat settings, and another script's INFO/WARN output survive plugin startup, verbosity changes, and shutdown.
- Modify/Abort: both Copilot swap modes, supported operations, final menu validation, unavailable actions, and actionable pauses.
- GE warnings: unrelated chat text, hidden widgets, exact Yes controls, changing bounds, and unsuccessful confirmation.
- UI availability: failed reads remain distinct from successfully closed interfaces, cannot confirm dismissal or reject Modify setup, and reset recovery timers.
- Preference and privacy lifecycle: startup/configuration/shutdown do not write preferences or override shared mouse/antiban settings; hidden/stopped/logged-out overlays clear cached statistics and reject stale queued reads.
- Script guard: login, pause, human-input priority, interruption, and blocking events remain effective without the inherited run/stamina preference writes.

## Reproduction

Use a clean checkout and JDK 11. The normal build resolves the current published client; the second command checks the declared minimum:

```sh
./gradlew clean build
./gradlew test --tests 'net.runelite.client.plugins.microbot.geflipper.*' -PpluginList=FlipperPlugin -PmicrobotClientVersion=2.6.26
```

For offline validation, add `-PmicrobotClientPath=/absolute/path/to/microbot-2.6.26.jar`. Automated tests do not require a live game account. Offline validation does not establish complete live Hotkey/Mouse behavior on the final release.

The Hub's expected PR build is `./gradlew clean build` with JDK 11. Required CI must pass before merging; a focused plugin build alone is insufficient.

The unchanged normal Hub build passed against Microbot 2.6.28: 144 tests across 23 suites, including 64 GE Flipper tests across 8 suites, with no failures, errors, or skips. A separate focused GE Flipper build against Microbot 2.6.26 also passed all 64 tests. Both packaged JARs contain 27 GE Flipper classes with Java 11 bytecode, version 1.2.63, and no private data files or client classes. These are local results; the submitted PR must still pass its required GitHub checks.

## Packaging and publication

The normal Hub build creates the versioned plugin JAR. The main-branch workflow generates release metadata and documentation, then publishes assets under `latest-release`. The plugin implements no independent updater.

Inspect the JAR for Java 11 bytecode and GE Flipper classes/resources only. Exclude raw logs, exports, screenshots of private sessions, backups, machine paths, credentials, and account details from the submitted change and release assets. No release or repository update is performed by this validation document.
