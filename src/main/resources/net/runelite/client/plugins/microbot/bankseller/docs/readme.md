# Bank Seller Plugin

Sells unprotected, Grand Exchange-eligible bank items while preserving starting item types and the terms of existing GE offers.

## Features

- **Running-version overlay**: shows the version loaded by the active plugin above the bank and Grand Exchange interfaces, independently of the Hub card's cached version. Turn **Show version overlay** on or off in Bank Seller settings; it defaults to on and takes effect immediately.
- **Always protects starting items**: snapshots item types in inventory and equipped gear before any banking or GE actions. Those types and matching bank copies are never sold. Noted and unnoted forms share protection; different charge variants retain their own identities. Starting inventory may be banked to free space; worn gear is not deposited. There is no toggle, and old saved disabled settings are ignored.
- **Empty-start support**: a genuinely empty inventory or fully unequipped character can have no item container. After stable login, the plugin verifies complete explicit empty slots in the inventory/equipment tabs (including ring and ammo) before proceeding. If the bank or GE is already open and hides these tabs, it closes only that panel first, without cancelling or collecting any offers. Missing or partially loaded data still stops safely. It may briefly open those tabs during startup; it never equips or removes gear to perform this check.
- **Automatically parks existing GE offers**: always saves original buy/sell offers before cancelling them, banks returned assets, and restores the original direction, item, unit price and only the unfilled quantity at the end. Trades completed before cancellation are not repeated; queue position and transaction history cannot be recreated. Original offer item types are excluded from bank liquidation. There is no parking toggle.
- **Interruption recovery**: saved original offers are kept in an account-bound recovery journal. Each enable captures the current starting inventory/gear before any actions. Re-enabling after an interruption first restores saved offers, then automatically continues into a fresh bank-selling run using that enable's protected item snapshot. The fresh offer boundary is saved and parked before liquidation; original item types remain protected across recovery, and offers are restored again at the end. If recovery is ambiguous or blocked by unrelated offers, the plugin stops without discarding the saved terms. Do not switch accounts or run other GE automation during a run.
- **Banks first**: deposits inventory, then withdraws unprotected GE-sellable bank items as notes
- **Sells full stacks**: the entire quantity of an item is sold in a single Grand Exchange offer
- **Includes GE-sellable members' items on free worlds**: uses the item's Grand Exchange eligibility instead of its player-trading flag. Burnt food, quest items, partially charged jewellery and other items that cannot be listed stay banked.
- **Always uses overview Collect**: collects completed sales together using Collect-to-bank even while other owned offers are still selling. It never enters an item's offer screen to collect. If a pre-existing/unrelated offer would also be collected, it stops safely instead of collecting that offer.
- **Continues the current inventory batch**: fills the usable GE slots, collects completed sales, and sells the remaining inventory before withdrawing again.
- **Aggressive stale-offer repricing**: after two completed selling batches, an unsold offer is cancelled and re-listed at 10% of its previous asking price (90% lower, minimum 1gp). A 90-second per-offer timeout also triggers repricing when no further batches can finish. Each re-list resets its age; offers are repriced one at a time and recovered using overview Collect-to-inventory, preserving room for the returned items and coins.
- **Works on F2P trade-restricted accounts**: items the Grand Exchange refuses to sell (e.g. trade-restricted items on new F2P accounts) are detected from the offer screen and skipped. The plugin clears the rejected setup before selecting the next item, puts refused items back in the bank and carries on.
- **Initial discount pricing**: new offers start at 50% of the actively traded price; this does not guarantee a buyer.
- **No-buyer handling**: stale offers keep receiving the larger price reductions down to 1gp. If all remaining owned offers are still unsold at 1gp after 60 seconds and the bank is drained or the GE is full, the plugin cancels and banks them before restoring saved original offers. If there are no originals to restore, it leaves the 1gp offers listed and stops.
- **Original offer terms are never discounted**: the 90% price reductions apply only to new Bank Seller offers. Existing offers are parked/restored separately; completed/cancelled original offers are banked without replaying completed trades.
- **Recovery verification**: collection requires the exact frozen cancellation result. Every restored or unchanged original offer is rechecked at completion before the recovery journal is removed. Changed, missing or externally cancelled offers stop the run and retain recovery instead of repeating a trade. Overview Collect also refuses to clear completed preserved offers while other originals still need recovery.
- **Coins and platinum tokens are never sold**
- **Waits for pending offers to sell and collects the coins before stopping**

## How It Works

The plugin withdraws and sells unprotected GE-eligible bank items, finishes the current inventory batch, and restores saved original offers before stopping.


## Usage

1. **Start near a bank at the Grand Exchange.**
2. **Enter your bank PIN before starting, if required.**
3. **Existing GE offers are automatically saved and parked to make room.**
4. **Do not run another Grand Exchange automation at the same time**


## Technical Details

- **Plugin Version**: 1.0.6
- **Author**: KSP
- **Minimum Client Version**: 2.6.25
- **Dependencies**: N/A
- **Compatibility**: RuneLite with Microbot integration


## Support

For issues, questions, or feature requests, use the [Microbot Discord topic](https://discord.com/channels/1087718903985221642/1405996818323738644).

---

*This plugin automates Withdrawal of items in noted form and Selling on the Grand Exchange.*
