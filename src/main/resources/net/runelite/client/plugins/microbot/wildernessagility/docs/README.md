# Wilderness Agility Script

An automated Wilderness Agility Course training script for Microbot.
This plugin is designed to handle agility training at the Wilderness Course, with optional mass-world support and future anti-PK features.  

---

## Features

- Automated agility training at the Wilderness Course.  
- Supports both solo and mass-world running.  
- Automatic walking to course and coin deposit if starting outside the course.  
- Integration with Webwalker and QOL plugins.
- Full looting bag support and inventory value monitoring.

---


## Configuration

### Inventory (Required)
- **150,000 coins** – Required to utilize the Agility Dispenser 
- **Knife** – Needed for the primary banking logic. (Walking to mage bank)
- **Looting bag** - Now required at all times.  Open the bag before starting.
- **Ice Plateau teleport** (optional, but highly recommended)  

### Plugin Settings
- **Player Monitor**
  - HIGHLY recommended for various avoidance features in the script, but not required

- **Webwalker**  
  - Enable, and disable the toggle “Avoid Wilderness.”  

- **QOL (Quality of Life)**  
  - Enable, and configure:  
    - Disable camera options  
    - Auto-eat: 55–90% threshold (higher health = less chance of dying) 
    - Auto-complete dialogue  
    - Auto Potion Manager (not fully tested with anti poisons yet)

- **Antiban Settings**  
  - Configure to preference. Script does not modify these settings.  

- **Break Handler**  
  - Turn OFF (not supported, may cause script stalls).  

- **Teleport Warnings**  
  - Disable teleport warnings if using Ice Plateau teleport.  

---

## Example Inventory & Gear Setup

Below is an example setup for a low level account, any armor tier is fine.  

![Example Inventory Setup](assets/InventorySetup.PNG)

This image is provided as a **general guideline only**.  

There are no strict requirements other than:  
- A **crossbow** (if running with the AFC Agility clan chat)  
- Some form of **armor** for basic protection  

Everything else is flexible depending on your personal preferences or available gear.  

---

## Start Conditions

### Starting at the Course
- Enable **“Start at course?”** in the config  
- Deposit coins (optional)  
- Ensure looting bag is open if using the related functions  
- Start the script  

### Starting Anywhere Else
- Disable **“Start at course?”** in the config  
- Have 150k coins, a knife, a looting bag, and optionally an Ice Plateau teleport  
- The script will navigate to the course and handle coin depositing automatically  

---

## Mass World Setup

- Equip a cheap ranged setup (Leather → Black d’hide with crossbow + bolts)  
- Join one of the following clan chats:  
  - `Agility Fc`  
  - `Free Agility`  
- Start script during active mass runs  
- Monitoring of your character is still strongly recommended as PKers may appear  

---

## Escape Settings
 
- Leave course at Health % will force the player to the nearest bank when uner the threshold
- If phoenix necklace expires  (and toggeled on) player will leave course, enable player monitor, and logout or world hop/bank

---

## In Development

- Idk what now?

---

## Known Issues

- Sometimes banking just fails?  It retries until it gets it right though so not a huge deal still weird though.

---

## Contributing

- Contributions, suggestions, and bug reports are much appreciated!    
- This script was originally made just for fun, please feel free to modify it anyway you see fit!

---

## Leaderboards

Submit your best lap, best individual bank haul, and squirrel pulls on the discord to get added!

![Leaderboard](assets/Leaderboard.png)

![Ending](assets/Ending.png)