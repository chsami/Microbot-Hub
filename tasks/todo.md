# Motherload Mine - Drop Gems Feature

- [x] Update `MotherloadMineConfig.java` to add the `dropGems` option.
- [x] Update `MLMStatus.java` to add the `DROP_GEMS` status enum.
- [x] Update `MotherloadMineScript.java`:
  - [x] Add `hasGemsInInventory` helper method.
  - [x] Update `determineStatusFromInventory` to set `status = MLMStatus.DROP_GEMS` when gems are found and config is true.
  - [x] Update `executeTask` switch statement to handle `DROP_GEMS`.
  - [x] Implement `dropGems` method to drop `UNCUT_SAPPHIRE`, `UNCUT_EMERALD`, `UNCUT_RUBY`, `UNCUT_DIAMOND`.
- [x] Manual review of changes to ensure logic doesn't break existing bot behaviour (e.g. gem bag).
