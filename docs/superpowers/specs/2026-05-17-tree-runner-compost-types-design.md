# Farm Tree Runner: Compost Type Selection

## Summary

Replace the boolean `useCompost` config (which only supported bottomless compost bucket) with a dropdown enum that supports all four compost types: Compost, Supercompost, Ultracompost, and Bottomless Compost Bucket. Compost is only applied at patches where protection is not enabled.

## New Enum: `CompostType`

File: `src/main/java/net/runelite/client/plugins/microbot/farmtreerun/enums/CompostType.java`

| Value | ItemID constant | ID | Reusable | Notes |
|-------|----------------|----|----------|-------|
| `NONE` | — | -1 | — | No compost |
| `COMPOST` | `COMPOST` | 6032 | No | Regular compost |
| `SUPERCOMPOST` | `SUPERCOMPOST` | 6034 | No | Supercompost |
| `ULTRACOMPOST` | `ULTRACOMPOST` | 21483 | No | Ultracompost |
| `BOTTOMLESS_BUCKET` | `BOTTOMLESS_COMPOST_BUCKET_22997` | 22997 | Yes | Reusable, withdraw 1 |

Fields: `String name`, `int itemId`, `boolean reusable`.

## Config Change

File: `FarmTreeRunConfig.java`

- Remove `useCompost()` boolean (keyName `"useCompost"`)
- Add `compostType()` returning `CompostType`, default `NONE`
  - keyName: `"compostType"` (new key; old `useCompost` values ignored safely)
  - Section: `gearSection`, position 1
  - Description: "Select compost type. Only applied at patches without protection enabled."

## Script Changes

File: `FarmTreeRunScript.java`

### `isCompostEnabled(config)`

Change from `config.useCompost()` to `config.compostType() != CompostType.NONE`.

### `useCompostOnPatch(config, patch)`

Change `config.useCompost()` check to `config.compostType() != CompostType.NONE`. Rest of the method (protection gating per tree kind) stays identical.

### Banking: compost withdrawal block

Replace the current bottomless-only block:

1. If `compostType == NONE`: skip, `compostItemId = null`.
2. If `BOTTOMLESS_BUCKET`: check bank for item 22997, withdraw 1. Same as current.
3. If `COMPOST / SUPERCOMPOST / ULTRACOMPOST`: count unprotected patches across all three tree categories (regular trees if `!protectTrees()`, fruit trees if `!protectFruitTrees()`, hardwood if `!protectHardTrees()`). Withdraw that count of the selected compost item ID.

Unprotected patch count reuses the existing `getSelectedTreePatches()`, `getSelectedFruitTreePatches()`, `getSelectedHardTreePatches()` methods — sum their sizes, filtered by protection config.

### `handlePlantingTree()`: drop empty bucket after use

After applying consumable compost (not bottomless) and waiting for the Farming XP drop, drop the resulting empty bucket (`ItemID.BUCKET`, 1925) to free the inventory slot before planting the sapling.

### Non-banking path

The `else` branch (when `config.banking()` is false) currently hardcodes `compostItemId = ItemID.BOTTOMLESS_COMPOST_BUCKET_22997`. Change to set `compostItemId = config.compostType().getItemId()` (or `null` if `NONE`).

## Files Touched

| File | Action |
|------|--------|
| `enums/CompostType.java` | New |
| `FarmTreeRunConfig.java` | Modify: replace boolean with enum dropdown |
| `FarmTreeRunScript.java` | Modify: banking qty logic, `isCompostEnabled`, `useCompostOnPatch`, `handlePlantingTree` bucket drop, non-banking path |

## Config Information Update

Update the `@ConfigInformation` HTML on the config interface:
- Change "Filled Bottomless compost bucket" in the Optional items list to "Compost / Supercompost / Ultracompost / Bottomless compost bucket"
