# Anonymous Mode – Microbot RuneLite Client

![preview](assets/img.gif)

The **Anonymous Mode** plugin helps you capture screenshots, record clips, stream, or share debug information without exposing your in‑game identity or account details. It masks a wide range of client UI elements locally so you can focus on demonstrating features or reporting bugs safely.

---

## Feature Overview

| Feature | Description |
|---------|-------------|
| **Character Name Mask** | Replaces your name in chat with a generic placeholder. |
| **Title Bar Clean** | Removes your character name from the window title. |
| **HP / Prayer Globes** | Forces displayed values to 99 for privacy consistency. |
| **Character Visual Wipe** | Hides worn gear by rendering a stripped model (locally only). |
| **Skill Levels Mask** | Displays all visible skill levels as 99. |
| **Skill / Total Tooltips** | Masks tooltip values (e.g. 2147M / 2B style). |
| **Combat Level Mask** | Shows combat level as 126. |
| **Inventory Item Quantity Mask** | Overlays stack sizes with 2147M. |
| **Bank Item Quantity Mask** | Masks bank stack quantities (visual only). |
| **XP Drop Counter Mask** | Overrides XP drop totals with a high capped value. |
| **Selective Toggle** | Enable only the elements you need to hide. |
| **Live, Client‑Side Only** | No server interaction; purely visual modifications. |
| **Fast Update Loop** | Applies masks every frame (BeforeRender) for consistency. |

---

## Requirements
- Microbot RuneLite client
- Plugin enabled in the Microbot plugin list

---

## Configuration Options
Each masking feature can be toggled individually under: Microbot Plugins -> Anonymous.
- Character Name
- Character Name (Title)
- HP Globe
- Prayer Globe
- Character Visual
- Skill Levels
- Skill Tooltips
- Combat Level
- Inventory Items Quantity
- Bank Items Quantity
- XP Drops Counter

Tip: Disable unused masks to reduce overhead on low spec machines.

---

## How It Works
1. On startup the script acquires the active RuneLite client instance.
2. A lightweight loop hooks into the BeforeRender event.
3. For each enabled mask it replaces the specific Text widget values.
4. All changes are ephemeral and purely client side (nothing is sent to the game servers).
5. When disabled, at the first refresh of the affected widgets, the original values are restored.

---

## Limitations
- Only affects what you see (and what you capture).
- Intensive masking on very low-end hardware may slightly impact FPS.

---

## Best Practices
- Enjoy :)

---

## Disclaimer
This plugin is intended for privacy while demonstrating or debugging within the **Microbot RuneLite Client**. It does not provide gameplay advantages and does not alter server data.

---

## Feedback
Open an issue or feel free to contribute improvements.

