package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroInfernoConfig.GROUP)
@ConfigInformation(InfernoRequirements.HTML)
public interface DroInfernoConfig extends ArenaPreparationConfig {
    String GROUP = "droinferno";

    @ConfigItem(keyName="sacrificeFirecape", name="Sacrifice firecape", description="With automatic startup: unlock Inferno using one Fire cape, return to the GE, then buy missing supplies and prepare. Does not sacrifice another cape after access is unlocked.", position=-1)
    default boolean sacrificeFirecape() { return false; }

    @ConfigItem(keyName="setup", name="Inventory Setups", description="Select the saved gear, inventory and pouch supplies to load before travelling. Automatic startup only.", position=2)
    default InventorySetup setup() { return null; }

    @Range(min=1,max=4)
    @ConfigItem(keyName="zukHealersAtOnce", name="Zuk healers at once", description="How many Zuk healers to handle together (1-4). Applies to the next run.", position=6)
    default int zukHealersAtOnce() { return 1; }

    @ConfigItem(keyName="showTilePack", name="Show Inferno tile pack", description="Draw the community Inferno tile pack (14 Ground Markers tiles: Zuk shield-path stand and unsafe tiles, wave spots) and each monster's true tile, coloured by the prayer it needs (green ranged, blue magic, red melee, orange Blob/Jad, yellow Zuk and its healers, white Zuk shield). Display only; the script does not use it.", position=11)
    default boolean showTilePack() { return false; }

    @ConfigItem(keyName="logoutAfterRun", name="Log out after death or cape", description="Log out when the run ends, then hold auto-login until you log in again.", position=10)
    default boolean logoutAfterRun() { return false; }
}
