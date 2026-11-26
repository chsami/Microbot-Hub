# Scoots Auto Herb, Tree, Fruit Farmer

Schedules herb, regular tree, and fruit-tree runs using separate patch timers.
The overlay shows Farming level, an estimate of herbs to the next level, and current activity.

## Setup

- Enable the herb locations you can reach and choose your herb seed. Auto Banking can use stocked supplies. The default herb check interval is 90 minutes.
- **Advanced → Buy seeds at GE** is off by default. Enabling it requires a free GE slot and coins in the bank, and authorizes a batch of 100 seeds at +99% when eligible stock is exhausted.
- **Random events → Use XP on Farming** is off by default. Enable it only if you want the plugin to spend eligible lamps and books you are carrying.
- For trees, enable regular trees and fruit trees independently and choose a sapling type. Stock ready-to-plant saplings, a rake, and a spade.
- Enable **Trees → Pay to clear trees** to pay 200 GP per mature tree after the health check. Keep coins in the bank. With the setting off, bring a usable axe for chopping.
- For Brimhaven, stock regular or super antipoison and five supported cooked foods per trip. Supported food includes salmon, lobster, swordfish, monkfish, and shark. The route defers Brimhaven if it cannot confirm supplies.
- Repeat Runs schedules new visits as patches come due. Logout Between Runs is optional.

## Settings layout and mouse movement

Farm setup and Run schedule contain the common choices. Herb patches, optional crop types, Random events, Custom inventory, Advanced, and Recovery are grouped below. Existing crop selections and saved configuration keys are preserved.

Advanced → Mouse profile defaults to **Client profile**, which leaves Microbot's mouse speed, reaction timing and movement providers untouched. Adjust those in the client's Mouse panel. **Legacy smooth** restores AutoFarmer's older additional smoothing if explicitly selected. These choices do not guarantee ban prevention.

## Recovery

If the plugin reports a failure, fix the cause, enable **Recovery → Clear failure**, and start AutoFarmer again. Failure reports and pending GE purchases are stored per character. Review any pending GE offer from older versions manually.

## Verification

Built against Microbot 2.6.59. The local build has completed herb runs and maple rounds. Verify the full fruit picking, gardener clearing, and replanting cycle and the Brimhaven recovery behavior in-game before treating those flows as tested.

Original AutoFarmer credit: Mocrosoft. Fork additions: Scoots. The BSD 2-Clause notice is included with the plugin.


### Random-event rewards (1.10.42)

Under Random events, **XP lamp skill** replaces the old Use XP on Farming checkbox. Choose any supported skill or **Keep lamp** for manual use. The new default is **Farming**, including profiles that previously left the old checkbox off; select Keep lamp to retain rewards. Standard random-event lamps and Books of knowledge use this setting; unrelated reward items remain protected. Successful use requires both item consumption and an observed XP gain in the selected skill.

Claimed random events now dispatch through Microbot's existing blocking executor from GameTick, without waiting for a long farming/travel operation to return. No blocking event work runs directly on the client thread. Existing pause controls, standalone-helper ownership and dismissal limits remain in effect. The mime invitation recognition was already present; this fixes the handoff that could leave it recognized but not acted on during travel. Live validation of a subsequent mime encounter is still pending.
