# Sailing Plugin — Changelog

All notable changes to this plugin are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [2.2.34]

### Fixed
- **Shipwreck highlight overlay**: Wreck lists are refreshed on the client each game tick and read by the overlay from that snapshot, so highlights draw reliably instead of depending on cache queries from the overlay render path.
- **Cargo hold + full inventory**: With **Use Cargo Hold** enabled, a full inventory that is only salvage is handled by depositing into the hold or entering hold processing; the script no longer tries to deploy the hook in that situation.

### Added
- **Idle inventory cleanup**: When there is no salvage in your inventory but **Open Caskets**, **Drop Items**, or **Enable Alching** would change the inventory, the plugin can run one drop / casket / alch pass before salvaging so leftover loot does not sit in the way.

### Changed
- **Cargo hold behaviour**:
  - Opens the hold and uses **Deposit inventory** in the cargo UI (not the salvaging station) when banking salvage from a full inventory.
  - Tracks **total occupied slots** and **salvage stacks** separately from the hold item grid so “full” and **processing** decisions match how the hold actually behaves (non-salvage items can occupy slots while salvage remains to process).
  - Re-reads the grid periodically while the hold flow is active; when you are idle (no nearby wreck and no salvage in inventory), the script avoids opening the hold on a timer just to resync counts.
  - Initialises the hold as soon as cargo-hold mode is active, before the “already salvaging” wait, so boarding or toggling the option is not blocked by animation checks.
  - Resolves the hold game object using the player world view first, with a fallback lookup if needed.

---

## [2.2.0]

### Added
- **Use Cargo Hold** (config checkbox, default: off)
  When enabled while salvaging, shipwreck salvage is deposited into your boat cargo hold via the hold interface (**Deposit inventory**) instead of using the salvaging station. Capacity and occupied slots are read from the cargo hold interface on first use; if the hold is full or would overflow the next deposit (using the same conservative rule as the design spec: zero free slots, or hold free slots below current empty inventory slots), the script enters a **processing** phase: it withdraws salvage from the hold in batches, closes the UI, and reuses the existing full-inventory routine (drop junk, open caskets, high alch) until salvage stacks in the hold reach zero, then resumes hook deployment. Manual hold interactions can desync the internal count; the script re-reads the interface when the hold object ID switches between no-cargo and cargo visuals. If the cargo UI fails to open repeatedly during processing, processing mode stops to avoid an infinite loop.

---

## [2.1.0]

### Fixed
- **Alch loop exhausts all stacks**: Previously the plugin alched one of each named
  item per loop pass (e.g. one gold ring, then one diamond ring, then moved on).
  The loop now continues alching each item name until none remain in the inventory
  before moving to the next entry on the list.
- **Inventory check before wreck detection**: The plugin previously located a nearby
  shipwreck first, then checked whether inventory was full. The check order has been
  corrected — inventory is now evaluated on every tick before any decision to salvage
  is made, preventing the hook from being deployed when the inventory is already full.

### Added
- **Open Caskets** (config checkbox, default: off)
  When enabled, all caskets in the inventory are opened as part of the full-inventory
  routine. The order of operations ensures loot always has space to land:
  1. Drop configured junk items (frees slots)
  2. Open all caskets
  3. High alch configured items, including any loot from the caskets
  4. Drop again to catch any casket loot that is also on the drop list

- **Alch Order** (config dropdown, default: LIST_ORDER)
  Controls the order in which alchable items are targeted across the inventory.
  Useful for making the alching behaviour look more natural and less robotic.

  | Option | Behaviour |
  |---|---|
  | `LIST_ORDER` | Alches by item name order in the Alch Items config list (original behaviour) |
  | `LEFT_TO_RIGHT` | Sweeps inventory row by row, left to right (slot 0 → 27) |
  | `RIGHT_TO_LEFT` | Sweeps inventory row by row, right to left (slot 3 → 24) |
  | `TOP_TO_BOTTOM` | Sweeps inventory column by column, top to bottom (0, 4, 8... then 1, 5, 9...) |
  | `BOTTOM_TO_TOP` | Sweeps inventory column by column, bottom to top (24, 20, 16... then 25, 21...) |

---

## [2.0.1] — Original Release

### Added
- Automatic shipwreck detection and salvaging within a 15-tile radius
- Hook deployment on nearby active shipwrecks
- Inventory management: high alching configured items and dropping configured junk
  when inventory is full
- Salvaging station support: deposits salvage at the station on your boat if present,
  otherwise falls back to dropping junk
- Shipwreck highlighting overlay with configurable colours for active wrecks,
  inactive stumps, and wrecks above the player's sailing level
- Barracuda Trials automation support
