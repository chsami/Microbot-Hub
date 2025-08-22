# StickToTheScript's Varrock Anvil Smither

![preview](assets/smith.jpg)

The **Varrock Anvil Smither Script** is capable of smithing all available items at the Varrock West Anvils.

---

## Features

| Feature            | Description                                       |
|--------------------|---------------------------------------------------|
| **All Bar Types**  | Supports smithing all bar types.                  |
| **All Item Types** | Supports smithing all items of each bar type.     |
| **Banking**        | Banks all smithed items at the Varrock West Bank. |

---

## Requirements
- Microbot RuneLite client
- A hammer and the selected bar type in the bank or in the inventory.
- Player is in Varrock West Bank.

---

## Configuration Options
- **Bar Type**: The type of bar to be smithing with.
- **Smith Object**: The object to smith at the anvil.
- **Logout**: When completed all of the selected bar type, should we log out?
- **Debug**: Enable/disable the debug entry in the overlay. This is helpful for gathering additional information when bugs occur.

---

## Disclaimer
This script is intended for use within the **Microbot RuneLite Client** only.  
Use of automation software in Old School RuneScape is against Jagex’s rules and can result in penalties to your account.  
Use at your own risk.

---

## Changelog

### 1.0.2
- Fix out of bars logic.
- Fix hammer selection logic to ensure the correct hammer is taken from the bank.

### 1.0.1
- Increase timeout timer from 5 seconds to 10 seconds when waiting for the anvil interface to open.
- Increased wait time for XP drop from 4.5 seconds to 7.5 seconds.

### 1.0.0
- Initial Release