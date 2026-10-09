package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroZulrahConfig.GROUP)
@ConfigInformation(
        "Start at any bank, with a saved Microbot Inventory Setup selected. The bot reads both its equipment "
        + "and inventory items to discover mage/range weapons and matching gear using item stats and style names. "
        + "Include both styles' weapons and switch pieces for hybrid: ranged is used against blue Zulrah, "
        + "magic against green/red. With only a mage weapon, it stays mage-only. "
        + "Zul-andra teleport scrolls are REQUIRED; include them and a charged Ring of dueling for return trips to Ferox. "
        + "The bot withdraws the saved setup from your bank; it does NOT buy or replenish your bank supplies. "
        + "Your bank must already contain every required item. Most food and commonly used combat/prayer potions are supported. "
        + "For emergency combo healing include main food (such as manta rays), Saradomin brews and super restores. "
        + "Thralls require Arceuus, Book of the dead and the appropriate runes. Keep plenty of runes in your rune pouch "
        + "for multiple trips so you do not need to replenish it each run. Charged weapons/ammunition must be ready to use."
)
public interface DroZulrahConfig extends Config
{
    String GROUP = "DroZulrah";

    @ConfigItem(keyName="hideOverlay",name="Hide overlay",description="Hide the compact session card while the script continues running.",position=11)
    default boolean hideOverlay(){return false;}

    @ConfigSection(name="Smart breaks", description="BaseProfileDro AFK/logout breaks, started only at Ferox between trips.",
            position=9, closedByDefault=false)
    String breakSection = "smartBreakSettings";

    @ConfigItem(
            keyName = "selectedInventorySetup",
            name = "Inventory Setup",
            description = "Saved Microbot Inventory Setup used for regear and for discovering mage/range switches.",
            position = -1
    )
    default InventorySetup inventorySetup()
    {
        return null;
    }

    @Range(min = 20, max = 90)
    @ConfigItem(keyName="eatAt", name="Eat at HP %", description="Normal food threshold.", position=0)
    default int eatAt(){ return 58; }

    @Range(min = 10, max = 70)
    @ConfigItem(keyName="panicAt", name="Panic eat HP %", description="Emergency threshold; eating overrides DPS and switching.", position=1)
    default int panicAt(){ return 38; }

    @Range(min = 1, max = 80)
    @ConfigItem(keyName="restoreAt", name="Restore prayer at", description="Drink prayer/super restore at or below this prayer level.", position=2)
    default int restoreAt(){ return 28; }

    @ConfigItem(keyName="useOffensivePrayer", name="Offensive prayers", description="Use the best available range/magic offensive prayer.", position=3)
    default boolean useOffensivePrayer(){ return true; }

    @ConfigItem(keyName="useThralls", name="Use thralls", description="Use the best available non-melee Arceuus thrall when the setup/spellbook/runes allow it.", position=4)
    default boolean useThralls(){ return true; }

    @Range(min = 0, max = 6)
    @ConfigItem(keyName="prepositionTicks", name="Pre-position lead (ticks)", description="Move toward the next known stand before the current phase ends.", position=5)
    default int prepositionTicks(){ return 3; }

    @ConfigItem(keyName="smartBreaks", name="Enable smart breaks", description="Enable BaseProfileDro smart breaks. Applies while running. Breaks only begin at Ferox between trips; active breaks finish their return cycle.", position=0, section=breakSection)
    default boolean smartBreaks(){ return true; }

    @ConfigItem(keyName="useBlowpipeSpecial", name="Use special attacks", description="Use supported equipped ranged and magic weapon specials at their required energy. Does not switch weapons just to spec.", position=7)
    default boolean useSpecialAttacks(){ return true; }

    @ConfigItem(keyName="showRotationHelperOverlay", name="Show Zulrah rotation helper",
            description="Show current/next phases, recommended tiles, prayers, countdown and clouds. Display only; does not change combat movement.", position=10)
    default boolean showRotationHelperOverlay(){ return false; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="minBreakIntervalMinutes", name="Minimum interval (minutes)",
            description="Minimum time between smart breaks. A due break waits for Ferox.", position=1, section=breakSection)
    default int minBreakIntervalMinutes(){ return 20; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="maxBreakIntervalMinutes", name="Maximum interval (minutes)",
            description="Maximum time between smart breaks; clamped to at least the minimum.", position=2, section=breakSection)
    default int maxBreakIntervalMinutes(){ return 140; }

    @Range(min=0, max=100)
    @ConfigItem(keyName="logoutBreakChance", name="Logout break chance (%)",
            description="Percent of breaks that log out. 0 means all AFK, 100 means all logout; the rest stay logged in.", position=3, section=breakSection)
    default int logoutBreakChance(){ return 100; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="afkBreakMinMinutes", name="Minimum AFK (minutes)",
            description="Minimum duration of a break that stays logged in.", position=4, section=breakSection)
    default int afkBreakMinMinutes(){ return 2; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="afkBreakMaxMinutes", name="Maximum AFK (minutes)",
            description="Maximum duration of a break that stays logged in; clamped to at least the minimum.", position=5, section=breakSection)
    default int afkBreakMaxMinutes(){ return 6; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="logoutBreakMinMinutes", name="Minimum logout (minutes)",
            description="Minimum duration of a logout break.", position=6, section=breakSection)
    default int logoutBreakMinMinutes(){ return 5; }

    @Range(min=1, max=1440)
    @ConfigItem(keyName="logoutBreakMaxMinutes", name="Maximum logout (minutes)",
            description="Maximum duration of a logout break; clamped to at least the minimum.", position=7, section=breakSection)
    default int logoutBreakMaxMinutes(){ return 40; }

    @Range(min=0, max=180)
    @ConfigItem(keyName="postLoginSettleSeconds", name="Post-login settle (seconds)",
            description="Seconds to settle after BaseProfileDro logs back in before resuming the trip.", position=8, section=breakSection)
    default int postLoginSettleSeconds(){ return 20; }
}
