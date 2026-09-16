# Pest Control Codex Task Prompt

Continue live validation of `chsami/Microbot-Hub#522` on
`fix/pest-control-adaptive-strategy` in `JogOnJohn/Microbot-Hub`.

Read the operator root `AGENTS.md`,
`F:\vmware boxs\MBOT\operator-work\plugins\pest-control.psd1`, this directory's
`DEVELOPMENT-HANDOFF.md`, the Pest Control package `AGENTS.md`, and the full
local handoff before acting.

Use `F:\vmware boxs\MBOT\repos\Microbot-Hub-pest-control` for source. Use only
the newer `Bizza 12345` VM (`clanker\vmadmin2`) for runtime validation. Agent
Server is guest-local on port 8081 and requires `X-Agent-Token`; never expose
the token.

Start read-only. Confirm the current PR head/checks, both Git statuses, guest
checkout path, client/plugin state and installed JAR hash. Preserve the known-good
`2.5.4` tribrid artifact. Do not edit, build, install or alter client lifecycle
state until instructed.
