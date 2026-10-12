package net.runelite.client.plugins.microbot.drokbd;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroKbdConfig.GROUP)
@ConfigInformation(
        "BETA: INVENTORY SET-UP ONLY. "
                + "DroKBD requires the selected Microbot Inventory Setup and uses Burning amulets to travel to the Lava Maze and Rings of dueling to return to Ferox Enclave. "
                + "Do not substitute other teleport methods. For crossbows, the selected bolts must be saved in the equipped ammunition slot. "
                + "Inventory/equipment reference images: https://imgur.com/a/j8OGZlu "
                + "Trips return to Ferox Enclave to restore stats and bank. Wilderness travel remains dangerous; only risk items you are willing to lose. "
                + "Weapon modes: Ruby bolts (e) and Ruby dragon bolts (e) use the equipped bolt quantity saved in the selected Inventory Setup; Toxic blowpipe skips ammunition handling and requires extended super antifire; Melee skips ammunition handling and the five-tile KBD spacing rule. "
                + "Ranged modes support ranging and divine ranging potions. Melee supports super combat and divine super combat potions. Anti-dragon and Dragonfire shields are both accepted where a shield is required."
)
public interface DroKbdConfig extends Config
{
    String GROUP = "DroKBD";

    @ConfigItem(keyName="hideOverlay",name="Hide overlay",description="Hide the compact session card while the script continues running.",position=5)
    default boolean hideOverlay(){return false;}

    @ConfigItem(
            keyName = "selectedInventorySetup",
            name = "Inventory Setup",
            description = "Select the saved Microbot Inventory Setup used for every DroKBD trip.",
            position = -2
    )
    default InventorySetup inventorySetup()
    {
        return null;
    }

    @ConfigItem(
            keyName = "ammunition",
            name = "Weapon / ammunition",
            description = "Choose Ruby bolts (e), Ruby dragon bolts (e), Toxic blowpipe, or Melee. Bolt modes equip the quantity saved in the selected Inventory Setup; Blowpipe and Melee perform no ammunition handling; Blowpipe requires extended super antifire.",
            position = -1
    )
    default DroKbdAmmo ammunition()
    {
        return DroKbdAmmo.RUBY_BOLTS_E;
    }

    @Range(min = 20, max = 90)
    @ConfigItem(
            keyName = "eatAt",
            name = "Eat at HP %",
            description = "Eat when health reaches this percentage.",
            position = 0
    )
    default int eatAt()
    {
        return 55;
    }

    @Range(min = 1, max = 80)
    @ConfigItem(
            keyName = "prayerAt",
            name = "Restore prayer at",
            description = "Drink prayer restoration at or below this many points.",
            position = 1
    )
    default int prayerAt()
    {
        return 25;
    }

    @Range(min = 1, max = 10000000)
    @ConfigItem(
            keyName = "minimumLootValue",
            name = "Minimum loot value",
            description = "Loot nearby drops worth at least this much, in addition to bones and hides.",
            position = 2
    )
    default int minimumLootValue()
    {
        return 5000;
    }

    @Range(min = 1, max = 64)
    @ConfigItem(
            keyName = "threatRadius",
            name = "Player safety radius",
            description = "Attempt logout when an attack-capable player is visible within this many tiles. If combat has begun, stop and accept the death.",
            position = 3
    )
    default int threatRadius()
    {
        return 32;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
            keyName = "upperCombatBuffer",
            name = "Upper combat buffer",
            description = "Extra combat levels above the normal Wilderness attack range treated as threatening.",
            position = 4
    )
    default int upperCombatBuffer()
    {
        return 5;
    }
}
