# Scoots Auto Herb, Tree, Fruit Farmer

Schedules herb, regular tree, and fruit-tree runs using separate patch timers.
The overlay shows Farming level, an estimate of herbs to the next level, and current activity.

## Setup

- Enable the herb locations you can reach and choose your herb seed. Auto Banking can use stocked supplies. The default herb check interval is 80 minutes.
- **GE seeds when empty** is off by default. Enabling it requires a free GE slot and coins in the bank, and authorizes a batch of 100 seeds at +99% when eligible stock is exhausted.
- **Use event XP rewards on Farming** is off by default. Enable it only if you want the plugin to spend eligible lamps and books you are carrying.
- For trees, enable regular trees and fruit trees independently and choose a sapling type. Stock ready-to-plant saplings, a rake, and a spade.
- Enable **Pay Gardener to Clear** to pay 200 GP per mature tree after the health check. Keep coins in the bank. With the setting off, bring a usable axe for chopping.
- For Brimhaven, stock regular or super antipoison and five supported cooked foods per trip. Supported food includes salmon, lobster, swordfish, monkfish, and shark. The route defers Brimhaven if it cannot confirm supplies.
- Repeat Runs schedules new visits as patches come due. Logout Between Runs is optional.

## Recovery

If the plugin reports a failure, fix the cause, enable **Clear failure and resume**, and start AutoFarmer again. Failure reports and pending GE purchases are stored per character. Review any pending GE offer from older versions manually.

## Verification

Built against Microbot 2.6.59. The local build has completed herb runs and maple rounds. Verify the full fruit picking, gardener clearing, and replanting cycle and the Brimhaven recovery behavior in-game before treating those flows as tested.

Original AutoFarmer credit: Mocrosoft. Fork additions: Scoots. The BSD 2-Clause notice is included with the plugin.
