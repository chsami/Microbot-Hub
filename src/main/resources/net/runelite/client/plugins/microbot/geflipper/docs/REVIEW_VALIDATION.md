# GE Flipper 1.2.80 review validation

This combined update includes the public changes since 1.2.70: waiting reliability, one-point slider controls, optional mouse-speed variation, mouse-only presets and fatigue clocks. It retains the existing Copilot trading and End / Finish behavior. Only GE Flipper sources, ordinary JUnit tests and its documentation belong in the change.

## Review requirements and boundaries

[Chsami's #550 review](https://github.com/chsami/Microbot-Hub/pull/550#pullrequestreview-5219550419) requires shared logging to remain intact and supported Modify behavior to work. GE Flipper therefore configures only its own package logger, preserves shared chat appenders and preferences, and keeps both documented Modify/Abort routes.

Minimum supported client version is 2.6.26 and builds target Java 11. Shared mouse factories, speed managers, antiban preferences, client classes and other plugins are outside the change. SDK-managed movement keeps its normal behavior. Startup, profile replay and passive UI refresh save no preferences. Only explicit GE Flipper control actions save their documented owned settings.

## Automated coverage

- Logging, Modify/Abort, GE warning bounds, unavailable UI reads and privacy lifecycle retain their existing regression coverage.
- Waiting movement confirms an actual cursor exit before parking, retries a refused movement after a fresh randomized delay, limits successful exits to one per continuous wait, and rejects stale completion after reset.
- The native slider uses one-unit track, page and keyboard steps without changing global UI defaults. Exact plugin/group/key ownership prevents another panel from being patched. Closing or rebuilding rows removes listeners and rejects stale edits.
- Optional mouse speed samples one bounded factor per movement through a private policy copy. Pause, real input, stop, configuration changes and Finish invalidate pending gestures without changing the shared speed manager.
- Preset tests cover distinct bounded ranges, retained session offsets, slow drift, gradual fatigue, Custom precision, and no persistence of computed values.
- Daily clock tests cover Morning, Mid-day, Night, typed HH:mm and computer-local time; midnight, invalid custom input, elapsed-time updates, wall-clock and timezone changes, and speed-off fallback without reading the inactive clock.
- Real SDK settings fixtures cover disabled/blank clock controls, hidden inactive custom-time input, saved-value restoration, explicit speed-enable selection of fatigue, profile/Reset rebuilding, passive replay and native Swing listeners.
- Finish coverage retains explicit transient requests, buy-side cancellation, collection validation, temporary sell-only mode restoration, fresh completion evidence and protection from stale queued shutdown callbacks.

## Reproduction and candidate evidence

Use a clean checkout and JDK 11:

```sh
./gradlew clean build
./gradlew test --tests 'net.runelite.client.plugins.microbot.geflipper.*' -PpluginList=FlipperPlugin -PmicrobotClientVersion=2.6.26
```

With JDK 11, the final combined 1.2.80 candidate passed all 238 GE Flipper tests across 21 suites against minimum SDK 2.6.26. Its JAR declares version 1.2.80 and minimum client 2.6.26, contains 63 packaged class files with Java 11 bytecode, and passed packaging/privacy inspection and static linkage against SDKs 2.6.26 and 2.6.30. Production and test Java sources retain the independently reviewed public behavior; only the submission version changed from its preparation label. The required GitHub CI normal full Hub clean build must pass on this combined PR head before merge. Historical results for other versions do not validate this candidate.

Inspect the resulting JAR for declared version 1.2.80, minimum client 2.6.26, Java 11 bytecode, and GE Flipper classes/resources only. Check linkage against the minimum and current SDKs. Exclude logs, account/trade exports, screenshots of private sessions, backups, credentials, personal paths and local diagnostics from source, documentation and artifacts.

## Supervised live checks

Enable waiting movement manually and observe a cursor exit during a healthy Copilot WAIT, then resumed trading. Check the plain slider in one-unit steps, saved Custom values and disabled styling. With speed initially off, enable it explicitly and verify Time of day / Fatigue is selected only by that click. Loading saved settings must leave the chosen preset unchanged.

Exercise each preset, the computer-local clock and a typed virtual start. Confirm the displayed effective value updates without overwriting the manual value. Inactive clock controls must be blank/disabled and custom input hidden when unused; returning to the relevant option restores the saved input. Turning speed off stops fatigue and restores the documented waiting behavior. Verify pause, human input, plugin restart and profile changes.

Test supported Hotkey/Mouse price input, confirmation and Modify/Abort. Test End / Finish separately with supervised offers and inventory; existing sells remain listed, temporary mode restores, and only GE Flipper stops. Automated fixtures do not establish every live server, reflection, timing or mouse condition. No live actions are authorized by this document.
