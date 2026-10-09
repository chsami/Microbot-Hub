# GE Flipper 1.2.70 review validation

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
- Waiting mouse: opt-in defaults, randomized deadlines and chance checks, one movement per continuous wait, virtual cursor position and exit state, an already outside cursor, action/pause/reset cancellation, and final state/generation checks. Synthetic Copilot APIs reject stale paused WAIT, errors, missing suggestions, and unavailable reflection methods.
- Popup gating: a visible empty GE popup scaffold does not block waiting; visible text or widget operations do. Warning confirmation matching remains unchanged.
- Slider: one Swing slider in GE Flipper's own native settings row controls chance and automatic timing. Exact plugin ownership checks leave other settings panels untouched. Refresh and cleanup do not save preferences; only explicit user edits save the owned frequency key. Disabling waiting mouse movement dims and disables the slider. Profile changes cancel pending drags, including when the new profile has the same stored frequency. Rebuilt and hidden controls cannot save stale values; shutdown restores the native spinner and releases listeners, queued scans, and references. Standard Swing events update bindings when the settings tree changes; no periodic timer is used.
- Finish: an explicit native button click requests an idempotent, transient session. Profile/default/Reset events cannot issue the command. Temporary sell-only mode is restored on cleanup without writing Copilot preferences or resetting pause/item strategy. Buy and Modify-buy suggestions are rejected; cancellation checks current buy-side and supported slot actions. Missing offer/inventory data cannot establish completion. A fresh healthy Wait plus verified client state is required before stopping only this plugin on EDT; stale completion callbacks cannot stop a restarted generation.

## Reproduction

Use a clean checkout and JDK 11. The normal build resolves the current published client; the second command checks the declared minimum:

```sh
./gradlew clean build
./gradlew test --tests 'net.runelite.client.plugins.microbot.geflipper.*' -PpluginList=FlipperPlugin -PmicrobotClientVersion=2.6.26
```

For offline validation, add `-PmicrobotClientPath=/absolute/path/to/microbot-2.6.26.jar`. Automated tests do not require a live game account. Offline validation does not establish complete live Hotkey/Mouse behavior on the final release.

The Hub's expected PR build is `./gradlew clean build` with JDK 11. Required CI must pass before merging; a focused plugin build alone is insufficient.

For 1.2.70, the focused Microbot 2.6.26 run passed all 152 GE Flipper tests across 16 suites with JDK 11. The normal Hub clean build against Microbot 2.6.29 passed all 232 tests across 31 suites, including those same 152 GE Flipper tests. Settings coverage uses the SDK's real generated rows, configuration proxy, checkbox, search, Reset menu, and Finish button. Finish coverage includes buy-side cancellation snapshots, verified dynamic Collect controls, temporary mode restoration, fresh suggestions, stable completion, profile interruption, and shutdown retries after a declined queued stop. The final JAR contains 42 GE Flipper classes with Java 11 bytecode; all 42 link against the minimum 2.6.26 SDK. Its manifest and plugin descriptor declare 1.2.70, and its contents contain no unexpected files or private diagnostic data. Fresh required GitHub CI must still pass for the submitted commit.

Microbot 2.6.26 and 2.6.29 provide no public custom-widget hook for their native plugin settings. The integration uses standard Swing tree/visibility events instead of polling and retains a narrow, read-only descriptor lookup to prove exact plugin/group/key ownership. Unsupported descriptors or row layouts keep the native bounded integer field. No client classes or shared preferences are modified.

Live testing of the new option remains necessary: enable it manually, wait for an active Copilot WAIT on the GE overview, observe the virtual cursor exit, then verify that normal suggested actions resume. The option defaults off. The shared mouse primitive has no success result or guarantee of immediate cancellation after a path starts.

The user reported End / Finish working in a live test of 1.2.70. This report does not cover every offer, inventory, or interruption condition. With GE Flipper running, click End / Finish and verify that buys are cancelled, collected items are listed using Copilot's recommendations, available sell modifications are handled, and only GE Flipper stops. Existing sells should remain listed and the previous temporary mode should be restored. No live Finish command was issued during offline validation.

## Packaging and publication

The normal Hub build creates the versioned plugin JAR. The main-branch workflow generates release metadata and documentation, then publishes assets under `latest-release`. The plugin implements no independent updater.

Inspect the JAR for Java 11 bytecode and GE Flipper classes/resources only. Exclude raw logs, exports, screenshots of private sessions, backups, machine paths, credentials, and account details from the submitted change and release assets. No release or repository update is performed by this validation document.
