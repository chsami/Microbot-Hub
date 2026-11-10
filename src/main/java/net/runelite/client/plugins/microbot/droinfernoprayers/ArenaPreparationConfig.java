package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/** Settings shared by local preparation and local combat. No remote service settings. */
public interface ArenaPreparationConfig extends Config {
    String BUY_WARNING = "Manual startup does not load a setup or check bank supplies, darts or runes.";

    @ConfigItem(keyName="automateStartup", name="Automate startup", description="Start at the GE: check owned supplies first, buy only shortages, load your Inventory Setup, prepare and travel to Inferno. Off: gear up yourself and start beside the entrance.", position=0)
    default boolean automateStartup() { return false; }

    @ConfigItem(keyName="fightInput", name="Fight input", description="Natural mouse and direct invokes use the same local fight decisions. Walking uses Natural mouse.", position=5)
    default ArenaFightInput fightInput() { return ArenaFightInput.MICROBOT_MOUSE; }

    @ConfigItem(keyName="prayersOnly", name="Prayers only", description="Only switch protection and offensive prayers; you play everything else (movement, attacks, gear, potions, inventory). Prayers are invoked directly without opening the prayer book or moving the mouse.", position=3)
    default boolean prayersOnly() { return false; }

    @ConfigItem(keyName="manualMovement", name="Manual movement", description="The script plays everything except walking: prayers, attacks, gear, potions, specials and Zuk stay automatic, and you move the player yourself (no position, shield or reserve steps); direct invokes are used. Attacks are sent only with the target in range and sight from your tile, so they never walk you. Prayers only takes precedence. Applies on the next run.", position=4)
    default boolean manualMovement() { return false; }

    @Range(min=1,max=99)
    @ConfigItem(keyName="vitalsPercent", name="Eat at HP %", description="Start brew recovery at or below this HP percentage. Zuk preparation and healer recovery can use a higher threshold. Applies to the next run.", position=7)
    default int vitalsPercent() { return 55; }

    @Range(min=1,max=69)
    @ConfigItem(keyName="rangingFromWave", name="Ranging potions from wave", description="Use ranging potions from this wave onward. 1 enables potions from the start. Applies to the next run.", position=8)
    default int rangingFromWave() { return 35; }

    @ConfigItem(keyName="rangeOnly", name="Range only", description="Use ranged attacks only. Applies to the next run.", position=9)
    default boolean rangeOnly() { return false; }

    @Range(min=0,max=2147483647)
    @ConfigItem(keyName="purchaseBudget", name="Supply purchase budget (gp)", description="Maximum spent buying missing supplies in this startup. Owned gear and supplies cost zero. Nothing is bought if the remaining basket exceeds this budget.", position=20)
    default int purchaseBudget() { return 100_000_000; }

    @Range(min=0,max=2147483647)
    @ConfigItem(keyName="coinReserve", name="Keep coins (gp)", description="Coins to leave unspent when buying supplies.", position=21)
    default int coinReserve() { return 100_000; }

    @Range(min=1,max=2147483647)
    @ConfigItem(keyName="maxUnitPrice", name="Maximum unit price (gp)", description="Reject missing supplies whose quoted price exceeds this cap.", position=22)
    default int maxUnitPrice() { return 250_000_000; }

    @Range(min=0,max=100)
    @ConfigItem(keyName="priceMarkupPercent", name="Initial price markup %", description="Add this percentage to the quoted price for actual shortages. Partial fills retry at most twice, adding 5% per retry within the same budget and cap.", position=23)
    default int priceMarkupPercent() { return 20; }

    @Range(min=15,max=300)
    @ConfigItem(keyName="offerWaitSeconds", name="Wait per GE offer (seconds)", description="Wait for the owned offer, then cancel only that slot and verify items/refund before retrying the remaining shortage.", position=24)
    default int offerWaitSeconds() { return 60; }

    @ConfigItem(keyName="buyRunePouchNote", name="Buy rune pouch note", description="If the selected setup requires a missing standard rune pouch, buy its tradeable note and confirm conversion with a banker. Divine or upgraded pouches must already be owned.", position=25)
    default boolean buyRunePouchNote() { return false; }
}
