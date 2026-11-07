package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings, grouped so the panel fits without scrolling: two everyday controls at the top, then folded sections.
 * Key names are never changed (saved profiles depend on them); only labels, descriptions and layout are.
 * The panel shows each description as a one-line tooltip, so long ones are broken with {@code <br>}.
 */
@ConfigGroup(FlipperConfig.GROUP)
public interface FlipperConfig extends Config {
    String GROUP = "Flipper Config";

    enum SelectionMethod {
        HOTKEY("Hotkey (E)"),
        MOUSE("Mouse");

        private final String name;

        SelectionMethod(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum RandomizationPreset {
        CUSTOM("Custom"), AFK("AFK"), SEMI_AFK("Semi-AFK"),
        ATTENTIVE_HUMAN("Attentive Human"), DAY_FATIGUE("Time of day / Fatigue");

        private final String label;

        RandomizationPreset(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    enum TimeOfDaySource {
        MORNING("Morning"), MID_DAY("Mid-day"), NIGHT("Night"), CUSTOM_TIME("Type a time"),
        LOCAL_TIME("Computer clock");

        private final String label;

        TimeOfDaySource(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    /** How a slot is worked when Copilot asks for a modify or an abort. */
    enum SlotAction {
        COPILOT_LEFT_CLICK("On", "Copilot left-click swap"),
        MENU_OPTION("Off", "Slot menu action");

        private final String label;
        final String actionDescription;

        SlotAction(String label, String actionDescription) {
            this.label = label;
            this.actionDescription = actionDescription;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum SecondScreenAfkMode {
        OFF("Off"),
        STAY_LOGGED_IN("Stay logged in"),
        LOGOUT_BETWEEN_CHECKS("Log out");

        private final String label;

        SecondScreenAfkMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    enum MuleMode {
        OFF("Off"),
        FLIPPER_SENDER("Flipper (sends GP)"),
        MULE_RECEIVER("Mule (receives GP)");

        private final String label;

        MuleMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    // ---- Sections (folded by default; the client remembers which ones you open until it restarts) ----

    @ConfigSection(name = "Trading", description = "How Copilot's suggestions are entered, and stopping for updates.",
        position = 10, closedByDefault = true)
    String tradingSection = "trading";

    @ConfigSection(name = "Mouse", description = "Idle mouse movement, mouse speed and blocking your own mouse.",
        position = 20, closedByDefault = true)
    String mouseSection = "mouse";

    @ConfigSection(name = "AFK breaks", description = "Finish offers in a burst, then go AFK before checking again.",
        position = 30, closedByDefault = true)
    String afkSection = "afk";

    @ConfigSection(name = "Muling", description = "Send GP above a set amount to your Mule account.",
        position = 40, closedByDefault = true)
    String muleSection = "muling";

    @ConfigSection(name = "Help", description = "How to use GE Flipper, and logging.",
        position = 60, closedByDefault = true)
    String helpSection = "help";

    // ---- Always visible ----

    @ConfigItem(
        keyName = "finish",
        name = "End / Finish",
        description = "Stop cleanly: cancel buy offers, collect items and list them<br>"
            + "with Copilot's sell suggestions. Sell offers stay on the GE.<br>"
            + "Then wait for the game to log out (no auto-login back in)<br>"
            + "and switch GE Flipper off.",
        position = 0
    )
    default ConfigButton finish() {
        return null;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Show the GE Flipper panel on the game screen:<br>status, profit, GE slots and mule progress.",
        position = 1
    )
    default boolean showOverlay() {
        return true;
    }

    // ---- Trading ----

    @ConfigItem(
        keyName = "selectionMethod",
        name = "Select suggestions with",
        description = "Enter Copilot's price and quantity with the<br>hotkey (E) or with mouse clicks.",
        position = 11,
        section = tradingSection
    )
    default SelectionMethod selectionMethod() {
        return SelectionMethod.HOTKEY;
    }

    @ConfigItem(
        keyName = "slotActionMode",
        name = "Copilot left-click swap",
        description = "On: use Copilot's swapped left-click for Modify/Abort<br>"
            + "(turn on slot swap in Copilot too).<br>"
            + "Off: use the slot's menu option instead.<br>"
            + "GE Flipper never changes Copilot's own setting.",
        position = 12,
        section = tradingSection
    )
    default SlotAction slotAction() {
        return SlotAction.COPILOT_LEFT_CLICK;
    }

    @ConfigItem(
        keyName = "autoFinishOnSystemUpdate",
        name = "Finish on OSRS update",
        description = "When an OSRS update countdown appears,<br>run End / Finish automatically.",
        position = 13,
        section = tradingSection
    )
    default boolean autoFinishOnSystemUpdate() { return false; }

    // ---- Mouse ----

    @ConfigItem(
        keyName = "waitingMouseOffScreen",
        name = "Move mouse off screen",
        description = "While Copilot says Wait, now and then move the cursor<br>"
            + "off the game screen, like someone looking away.<br>"
            + "Doesn't change Microbot's shared antiban settings.",
        position = 21,
        section = mouseSection
    )
    default boolean waitingMouseOffScreen() {
        return false;
    }

    @ConfigItem(
        keyName = "waitingMousePreset",
        name = "Preset",
        description = "AFK, Semi-AFK, Attentive Human or Time of day / Fatigue.<br>"
            + "Moving the slider picks Custom. Time of day / Fatigue needs<br>"
            + "'Randomize mouse speed'; otherwise the slider value is used.",
        position = 22,
        section = mouseSection
    )
    default RandomizationPreset waitingMousePreset() { return RandomizationPreset.CUSTOM; }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "waitingMouseChance",
        name = "Randomization",
        description = "How often and how soon the idle mouse moves.<br>Right = more often; fully left = never.",
        position = 23,
        section = mouseSection
    )
    default int waitingMouseChance() {
        return 30;
    }

    @ConfigItem(
        keyName = "waitingMouseTimeSource",
        name = "Fatigue clock",
        description = "Where the Time of day / Fatigue day starts: Morning (09:00),<br>"
            + "Mid-day (13:00), Night (21:00), a typed time or this computer's clock.<br>"
            + "Only used when that preset, 'Move mouse off screen'<br>"
            + "and 'Randomize mouse speed' are all on.",
        position = 24,
        section = mouseSection
    )
    default TimeOfDaySource waitingMouseTimeSource() { return TimeOfDaySource.MORNING; }

    @ConfigItem(
        keyName = "waitingMouseTime",
        name = "Start time (HH:mm)",
        description = "For 'Type a time': a 24-hour start time such as 14:30.<br>"
            + "Only shown when that option is picked.<br>"
            + "An invalid time pauses idle mouse movement until fixed.",
        position = 25,
        section = mouseSection
    )
    default String waitingMouseTime() { return "09:00"; }

    @ConfigItem(
        keyName = "randomizeMouseSpeed",
        name = "Randomize mouse speed",
        description = "Vary the speed of idle moves and GE Flipper's own clicks.<br>"
            + "Turning it on picks the Time of day / Fatigue preset.<br>"
            + "Walking and banking keep normal Microbot speed.",
        position = 26,
        section = mouseSection
    )
    default boolean randomizeMouseSpeed() { return false; }

    @ConfigItem(
        keyName = "blockManualMouseInput",
        name = "Block manual mouse input",
        description = "Ignore your physical mouse on the game screen while<br>"
            + "GE Flipper runs and you're logged in.<br>"
            + "The keyboard and this sidebar still work.",
        position = 27,
        section = mouseSection
    )
    default boolean blockManualMouseInput() { return false; }

    // ---- AFK breaks ----

    @ConfigItem(
        keyName = "secondScreenAfkMode",
        name = "AFK mode",
        description = "Like a player with the game on a second screen: finish<br>"
            + "the current Copilot suggestions, then go AFK before checking<br>"
            + "again. Stay logged in, or log out between check-ins.",
        position = 31,
        section = afkSection
    )
    default SecondScreenAfkMode secondScreenAfkMode() { return SecondScreenAfkMode.OFF; }

    @Range(min = 1, max = 60)
    @ConfigItem(
        keyName = "secondScreenAfkMinutes",
        name = "Check in every (min)",
        description = "Average minutes between check-ins.<br>Varies by about 35% each time.",
        position = 32,
        section = afkSection
    )
    default int secondScreenAfkMinutes() { return 10; }

    // ---- Muling ----

    @ConfigItem(
        keyName = "muleMode",
        name = "Mule mode",
        description = "Flipper: keep 'Keep for flipping', send the rest to your Mule.<br>"
            + "Mule: wait logged out, log in when a Flipper is ready to trade.<br>"
            + "The Flipper and Mule clients must run on this computer.",
        position = 41,
        section = muleSection
    )
    default MuleMode muleMode() { return MuleMode.OFF; }

    @ConfigItem(
        keyName = "muleRsn",
        name = "Mule name (RSN)",
        description = "Your Mule's in-game name. Needed on the Flipper,<br>which only trades this player.",
        position = 42,
        section = muleSection
    )
    default String muleRsn() { return ""; }

    // The key keeps its old name so existing profiles keep their value; it has always meant the amount kept.
    @ConfigItem(
        keyName = "muleThresholdGp",
        name = "Keep for flipping",
        description = "Total to keep for flipping: GP, items and GE offers (e.g. 50M).<br>"
            + "Only what's above this is sent. Items with no known price<br>"
            + "aren't counted, so a little extra may stay.",
        position = 43,
        section = muleSection
    )
    default String muleThresholdGp() { return "50M"; }

    @ConfigItem(
        keyName = "muleTriggerGp",
        name = "Mule when over",
        description = "Start a transfer once your total reaches this (e.g. 75M),<br>"
            + "then send everything above 'Keep for flipping'.<br>"
            + "0 = whenever there's at least 1,000 GP above it.",
        position = 44,
        section = muleSection
    )
    default String muleTriggerGp() { return "0"; }

    @ConfigItem(
        keyName = "muleBeforeUpdate",
        name = "Mule before OSRS update",
        description = "When an update countdown appears, send everything above<br>"
            + "'Keep for flipping' to the Mule before stopping. Only loose GP<br>"
            + "is sent; skipped if the update is too close.",
        position = 45,
        section = muleSection
    )
    default boolean muleBeforeUpdate() { return true; }

    @Range(min = 0, max = 600)
    @ConfigItem(
        keyName = "muleWorld",
        name = "Mule world (0 = Auto)",
        description = "0 = use the Mule's world and hop back after trading.<br>"
            + "Or enter a world number (e.g. 554) to always meet there.",
        position = 46,
        section = muleSection
    )
    default int muleWorld() { return 0; }

    @Range(min = 1024, max = 65535)
    @ConfigItem(
        keyName = "mulePort",
        name = "Mule port",
        description = "Port your Flipper and Mule clients on this computer<br>use to talk. Must match on both.",
        position = 47,
        section = muleSection
    )
    default int mulePort() { return 43599; }

    // ---- Help ----

    /** The guide's old key; its saved copy of the old text is removed once (see FlipperPlugin.removeOldGuideText). */
    String OLD_GUIDE_KEY = "guide";

    // A new key so the updated text shows; the old "guide" key held the previous text in saved profiles.
    @ConfigItem(
        keyName = "howToUse",
        name = "How to use",
        description = "Quick start for GE Flipper.",
        position = 61,
        section = helpSection
    )
    default String GUIDE() {
        return "1. Install Flipping Copilot from the Plugin Hub, log in to it and resume its suggestions.\n"
            + "2. Have GP in your inventory or bank (1M+ recommended).\n"
            + "3. Start at the Grand Exchange, or carry a teleport to it.\n"
            + "Press End / Finish to stop cleanly.\n"
            + "GE Flipper confirms GE price warning popups. Your Copilot, game and Microbot settings stay under "
            + "your control.\n"
            + "Made by chocken.";
    }

    @ConfigItem(
        keyName = "verboseLogging",
        name = "Detailed logs",
        description = "Log GE Flipper's own activity in detail. Leave off unless<br>"
            + "you're troubleshooting; other plugins' logging is not changed.",
        position = 62,
        section = helpSection
    )
    default boolean verboseLogging() {
        return false;
    }

}
