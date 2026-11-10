package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

/**
 * Settings of [Dro] Inferno Prayers: every Inferno setting under its own group, with the two assist
 * checkboxes replaced by one Mode drop-down and Fight input removed (both modes use direct invokes). Full automatic play cannot be selected here.
 */
@ConfigGroup(DroInfernoPrayersConfig.PRAYERS_GROUP)
@ConfigInformation(InfernoRequirements.HTML)
public interface DroInfernoPrayersConfig extends DroInfernoConfig {
    String PRAYERS_GROUP = "droinfernoprayers";

    @ConfigItem(keyName="mode", name="Mode", description="Prayers only: only switch protection and offensive prayers; you play everything else (movement, attacks, gear, potions, inventory). Prayers are invoked directly without opening the prayer book or moving the mouse. Auto no movement: the script plays everything except walking (prayers, attacks, gear, potions, specials and Zuk stay automatic) and you move the player yourself; attacks are sent only with the target in range and sight from your tile (direct invokes are used). Applies on the next run.", position=5)
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

    /** GE purchasing and the firecape sacrifice are only used by the AIO startup. */
    @ConfigItem(keyName="purchaseBudget", name="purchaseBudget", description="Not available in this plugin.", hidden=true)
    @Override default int purchaseBudget() { return 100_000_000; }
    @ConfigItem(keyName="coinReserve", name="coinReserve", description="Not available in this plugin.", hidden=true)
    @Override default int coinReserve() { return 100_000; }
    @ConfigItem(keyName="maxUnitPrice", name="maxUnitPrice", description="Not available in this plugin.", hidden=true)
    @Override default int maxUnitPrice() { return 250_000_000; }
    @ConfigItem(keyName="priceMarkupPercent", name="priceMarkupPercent", description="Not available in this plugin.", hidden=true)
    @Override default int priceMarkupPercent() { return 20; }
    @ConfigItem(keyName="offerWaitSeconds", name="offerWaitSeconds", description="Not available in this plugin.", hidden=true)
    @Override default int offerWaitSeconds() { return 60; }
    @ConfigItem(keyName="buyRunePouchNote", name="buyRunePouchNote", description="Not available in this plugin.", hidden=true)
    @Override default boolean buyRunePouchNote() { return false; }
    @ConfigItem(keyName="sacrificeFirecape", name="sacrificeFirecape", description="Not available in this plugin.", hidden=true)
    @Override default boolean sacrificeFirecape() { return false; }
}
