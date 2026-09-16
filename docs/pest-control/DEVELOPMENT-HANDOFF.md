# Pest Control PR Validation Handoff

Last refreshed: 2026-08-28 (Australia/Sydney)

## Source

- Upstream PR: `chsami/Microbot-Hub#522`
- PR title: `feat: add adaptive Pest Control strategy`
- Repository/branch: `JogOnJohn/Microbot-Hub`, `fix/pest-control-adaptive-strategy`
- Checkpoint before this documentation package: `d5edead2a9012efcb7592f4960ec65bc0cd09b60`
- Plugin version: `2.5.7`
- Minimum Microbot client: `2.6.15`
- Host worktree: `F:\vmware boxs\MBOT\repos\Microbot-Hub-pest-control`
- Operator manifest: `F:\vmware boxs\MBOT\operator-work\plugins\pest-control.psd1`
- Full local handoff: `F:\vmware boxs\MBOT\docs\handoffs\pest-control\PEST-CONTROL-HANDOFF.md`

The older `fix/pest-control-strategy` branch contains the long development
history but is not the PR head. Test and patch this branch only.

## Preserved Rollbacks

```text
2.5.4 known-good tribrid:
AD3532925A0A95941D4D219447C4BC0E26BADC7AEE35E0C15C357F88D0793E5B

2.4.34 known-good ranged:
213B84CEB2B496D733C062CFF6B287C3A8138561749588003A9E2607B35054AC
```

Do not delete or overwrite those archived artifacts. No exact `2.5.7` installed
hash was verified when this handoff was written.

## Validation Status

PR #522 records successful packaging against Microbot `2.6.15`, a packaged and
smoked `2.5.6`, and one complete `2.5.7` round on Microbot `2.6.16`. That is a
smoke checkpoint, not sufficient acceptance for the state-machine breadth.

On 2026-08-28, this branch was rebased onto `upstream/development` at
`64d1d0f` and source-validated with
`gradlew.bat test PestControlPluginJar -PpluginList=PestControlPlugin`. The
JUnit XML report recorded 17 Pest Control tests, with zero failures or errors.
This includes the focused combat-tab resolver coverage: configured Crush accepts
the visible `Crush`, `Pummel`, and `Smash` labels, while Stab, Slash, and Rapid
continue to require exact labels. The built `PestControlPlugin-2.5.7.jar` hash
was `594DB36A8A2FF2F7AEBD81EBACBC8369D7B17D72B1A79C728B69074562E5D886`.
This is build/test evidence only; it does not establish a loaded or live-tested
artifact.

Further live evidence should cover:

- several ranged paid rounds;
- paid win, unrewarded team win and Void Knight loss accounting;
- delayed result evidence and sub-five-second requeue;
- intact, open and destroyed gates;
- activity recovery, Brawler flank/fallback, and portal/Spinner flow;
- Ranged, Magic, Yellow Stab/Slash, Red Crush and tribrid profiles;
- one-hand/off-hand, two-handed, full-Void and non-Void setups; and
- portal-only specials with energy and duplicate-toggle checks.

Record exact artifact identity and per-round evidence. Patch only reproduced
failures. Preserve current client lifecycle unless explicitly authorized.
