# Bank Seller Plugin

Will obliterate any tradeable item from your bank - In other worlds will sell any tradeable item on the Grand Exchange

## Features

- **Banks first**: deposits your whole inventory, then withdraws every tradeable item from the bank as notes
- **Sells full stacks**: the entire quantity of an item is sold in a single Grand Exchange offer
- **Works on F2P trade-restricted accounts**: items the Grand Exchange refuses to sell (e.g. trade-restricted items on new F2P accounts) are detected instantly from the offer screen and skipped - the plugin clicks straight through to the next item without ever closing the GE window, puts refused items back in the bank and carries on
- **Instant-sell pricing**: every offer is listed at 50% of the actively traded price so it fills immediately
- **Leftover-offer liquidation**: if an offer still has not sold at the end, it is aborted and re-listed at 1gp before the plugin finishes and disables itself
- **Leaves existing offers untouched**: only slots created by the current Bank Seller run are collected or liquidated
- **Coins and platinum tokens are never sold**
- **Waits for pending offers to sell and collects the coins before stopping**

## How It Works

The plugin will loop Withdrawing and Selling items till neither the bank & Inventory contain sellable items.


## Usage

1. **Start near a bank at the Grand Exchange.**
2. **Ensure that you already entered your bank ping or use QoL**
3. **Ensure that you have open GE slots**
4. **Do not run another Grand Exchange automation at the same time**


## Technical Details

- **Plugin Version**: 1.0.5
- **Author**: KSP
- **Minimum Client Version**: 1.9.8
- **Dependencies**: N/A
- **Compatibility**: RuneLite with Microbot integration


## Support

For issues, questions, or feature requests, please refer to the topic creaded on the [Microbot discord](https://discord.com/channels/1087718903985221642/1405996818323738644).

---

*This plugin automates Withdrawal of items in noted form and Selling on the Grand Exchange.*
