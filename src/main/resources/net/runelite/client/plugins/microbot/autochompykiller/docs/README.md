# Auto Chompy Killer Plugin

The **Auto Chompy Killer Plugin** automates chompy bird hunting for the Big Chompy Bird Hunting quest and Ranged training.

---

## Feature Overview

| Feature                    | Description                                                              |
|----------------------------|--------------------------------------------------------------------------|
| **Auto Bellows Filling**  | Automatically fills ogre bellows at swamp bubbles                       |
| **Toad Inflation**         | Inflates swamp toads to create bloated toads for bait                   |
| **Chompy Hunting**         | Automatically attacks and kills chompy birds                            |
| **Feather Plucking**       | Optional plucking of chompy birds for feathers                          |
| **Run Energy Management**  | Supports stamina potions, energy potions, and strange fruit             |
| **Kill Count Tracking**    | Tracks chompy bird kills with overlay                                   |
| **Stop Conditions**        | Stop on kill count or when receiving chompy chick pet                   |
| **Auto Logout**            | Optional logout when stop conditions are met                            |

---

## Requirements

- **Ogre bow or Zogre bow** equipped
- **Ogre arrows or Brutal arrows** equipped in ammo slot
- **Ogre bellows** in inventory
- Access to Feldip Hills (chompy bird hunting area)
- Completion of Big Chompy Bird Hunting quest (partial completion required)

---

## Configuration Options

| Setting                   | Description                                                    |
|---------------------------|----------------------------------------------------------------|
| **Pluck Chompys**         | Enable/disable plucking chompy birds for feathers            |
| **Run Energy Option**     | Choose energy restoration method (none, potions, strange fruit) |
| **Drop Empty Vials**      | Automatically drop empty vials from drinking potions         |
| **Stop on Kill Count**    | Stop script after reaching specified number of kills         |
| **Kill Count**            | Number of chompy birds to kill before stopping               |
| **Stop on Chompy Pet**    | Stop script when receiving the chompy chick pet              |
| **Logout on Completion**  | Logout when stop conditions are met (waits until out of combat) |

---

## Run Energy Options

- **None**: No energy restoration
- **Stamina Potion**: Uses stamina potions (4-dose to 1-dose)
- **Super Energy Potion**: Uses super energy potions (4-dose to 1-dose)
- **Energy Potion**: Uses regular energy potions (4-dose to 1-dose)
- **Strange Fruit**: Uses strange fruit from Tree Gnome Village

---

## How It Works

1. Configure your desired settings (plucking, energy management, stop conditions)
2. Equip your ogre/zogre bow and arrows
3. Have ogre bellows in your inventory
4. Position yourself in the Feldip Hills chompy hunting area
5. Start the plugin
6. The plugin will:
   - Fill bellows at swamp bubbles when needed
   - Inflate swamp toads to create bloated toad bait
   - Drop bloated toads to attract chompy birds
   - Attack and kill chompy birds
   - Optionally pluck dead chompys for feathers
   - Manage run energy with configured items
   - Track kills and stop when conditions are met

---

## Setup Instructions

1. Complete the Big Chompy Bird Hunting quest (at least to the point where you can hunt chompys)
2. Obtain an ogre bow and ogre arrows from the quest
3. Get ogre bellows from the quest
4. Travel to the Feldip Hills hunting area
5. Configure the plugin settings to your preference
6. Start the plugin

---

## Troubleshooting

- **"No ogre bow equipped"**: Ensure you have an ogre bow or zogre bow equipped
- **"No ammo"**: Equip ogre arrows or brutal arrows in your ammo slot
- **"No bellows found"**: Make sure you have ogre bellows in your inventory
- **Can't reach bubbles**: The plugin will try different bubble locations automatically
- **Plugin stops unexpectedly**: Check that you're in the correct hunting area

---

## Limitations

- Must be in the Feldip Hills chompy hunting area
- Requires completion of Big Chompy Bird Hunting quest
- Only works with ogre/zogre bows and appropriate arrows
- Cannot hunt chompys that belong to other players

