# Zulrah Slayer Plugin

The **Zulrah Slayer Plugin** is a RuneLite plugin for the Microbot framework that automates slaying Zulrah in Old School RuneScape (OSRS). It is designed to efficiently detect which rotation is active, engage in the fight, loot the drops, resupply and travel back to Zulrah

---

## Features

- **Automated fighting:** Automatically fights Zulrah, using the appropriate combat style
- **Looting:** Loots the drops automatically after the kill has succeeded
- **Resupply:** Resupplies at the Grand Exchange
- **Travel:** Automatically travels to Zulrah, using a fairy ring in your PoH
- **Venom handling:** Automatically drinks antipoison if the venom damage is more than 10 to prevent taking too much passive damage

---

## How It Works

1. **Configure:** Set your starting point. Either it is banking, travelling to Zulrah or beginning of the fight (this is after you've clicked the boat and go past the dialog)
2. **Gear:** Create 2 inventory setups, once for magic and one for ranging. It will equip the gear that is available in each setup respectively, depending on the phase. It will always go back to the magic gear before banking, as every rotation always starts with magic. If you are using a Twisted bow, just select the same gear setup twice.

---

## Requirements

- Fairy ring in your PoH
- An Altar in your PoH
- A Teleportation Portal to the Grand Exchange
- A form of antipoison (if you do not have a serpentine helm)
- Lobsters (eats these between fights to replenish HP)
- Ring of recoil
---

## Limitations

- This plugin only uses Range/Mage. Using melee will not work, as the tiles it stands on are not made for melee
- Does not regear or get gear back in case of death (if your stats are high enough, this shouldn't really happen)
- Does not support Zul-Andra teleport scrolls
- Does not support Ring of Suffering

## Disclaimer

This plugin was made with the restrictions of my current account in mind. Since I do not have a Noxious Halberd, Zul-Andra teleports or a Ring of Suffering, I didn't bother implementing these features. I understand that these are important for regular main accounts, so feel free to make contributions.