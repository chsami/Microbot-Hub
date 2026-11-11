# Requested spellbook preparation

Efficient Walker 1.12.34 exposes `prepareSpellbook(Rs2Spellbook)` for a script to poll before starting its travel. Both **Allow requested spellbook switches** and **Use world 330 hosted house** default off. Ordinary walk requests never switch spellbooks or hop worlds.

`READY` means the requested book was observed and any house exit was observed. While `PREPARING`, the script must let Walker own navigation and refrain from issuing its own walk, bank, or house actions. `DISABLED` means a switch is needed but the option is off. `BLOCKED` ends the request; `getSpellbookMessage()` explains it. Explicit `cancel()` clears the request and discards queued work. Requesting a different ordinary walk also cancels preparation.

The flow checks an owned Magic cape, then a usable altar in your own house if Max POH is selected, then the optional world 330 Rimmington hosted house. With hosted houses disabled, the permanent altar/Tyss remains available. Personal-house entry uses an owned Construction cape or house tab; an outside landing enters only the account's current house portal. Banked supplies respect **Use items from bank**. It withdraws one needed item and does not buy supplies or deposit unrelated gear. If already inside a selected Max POH, it checks the loaded altar without needing outside coordinates.

Hosted entry reads visible **Enter House** rows from the live board, never a fixed host name or recording slot. A missing or unsuitable hosted altar, or 90 seconds without observed progress, leaves that house and permits exactly one different advertised host. Failure of the second host leaves the house and uses Efficient Walker to return to the GE, then reports BLOCKED. Portal/stair exits use loaded scene coordinates and actual floors, with three spaced attempts per exit and a 60-second bound. Exhausted cape attempts retain the existing bounded fallback. Input holds and loading pause preparation. The existing Walker mouse scope is used; no global Antiban, rest, or mouse options are changed.

This service is available to main scripts; this change does not modify individual scripts or infer their spellbook requirements.
