package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

/**
 * Settings of [Dro] Colosseum Prayers: every Colosseum setting under its own group, with the two assist
 * checkboxes replaced by one Mode drop-down and Fight input removed (both modes use direct invokes). Full automatic play cannot be selected here.
 */
@ConfigGroup(DroColosseumPrayersConfig.PRAYERS_GROUP)
@ConfigInformation(ColosseumRequirements.HTML)
public interface DroColosseumPrayersConfig extends DroColosseumConfig {
    String PRAYERS_GROUP = "drocolosseumprayers";

    @ConfigItem(keyName="mode", name="Mode", description="Prayers only: only switch protection and offensive prayers; you play everything else (movement, attacks, gear, potions, inventory). Prayers are invoked directly without opening the prayer book or moving the mouse. Auto no movement: the script plays everything except walking and most attacks; it attacks only the Fremennik warband and casts the last-enemy Blood heal from where you stand, and you move and attack everything else (direct invokes are used; the Javelin sidestep stays automatic). Applies on the next run.", position=5)
    default ArenaAssistMode mode() { return ArenaAssistMode.PRAYERS_ONLY; }

    /** Replaced by Mode; hidden and never read by the Prayers plugin. */
    @ConfigItem(keyName="prayersOnly", name="Prayers only", description="Replaced by Mode.", hidden=true, position=5)
    @Override default boolean prayersOnly() { return false; }

    /** Replaced by Mode; hidden and never read by the Prayers plugin. */
    @ConfigItem(keyName="manualMovement", name="Manual movement", description="Replaced by Mode.", hidden=true, position=6)
    @Override default boolean manualMovement() { return false; }

    /** Both modes use direct invokes; hidden and never read by the Prayers plugin. */
    @ConfigItem(keyName="fightInput", name="Fight input", description="Not used: both modes send direct invokes.", hidden=true, position=4)
    @Override default ArenaFightInput fightInput() { return ArenaFightInput.INVOKES; }

    /** AIO startup is not part of the Hub build: you gear up and enter yourself. */
    @ConfigItem(keyName="automateStartup", name="Automate startup", description="Not available in this plugin.", hidden=true, position=1)
    @Override default boolean automateStartup() { return false; }

    /** Inventory Setups are only used by the AIO startup. */
    @ConfigItem(keyName="setup", name="Inventory Setups", description="Not available in this plugin.", hidden=true, position=2)
    @Override default net.runelite.client.plugins.microbot.inventorysetups.InventorySetup setup() { return null; }
}
