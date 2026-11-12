package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.droelf.enums.ThievingFood;

@ConfigGroup("DroElf")
@ConfigInformation("Start near the elf you want to thieve. DroElf locks that elf area, the nearest Prifddinas bank, and one route for the entire session.")
public interface DroElfConfig extends Config {
    @ConfigSection(name = "Consumables", description = "Food and necklace restocking.", position = 0)
    String consumables = "consumables";

    @ConfigItem(keyName = "useFood", name = "Use food", description = "Eat and restock food.", position = 0, section = consumables)
    default boolean useFood() { return true; }

    @ConfigItem(keyName = "food", name = "Food", description = "Food withdrawn from the locked bank.", position = 1, section = consumables)
    default ThievingFood food() { return ThievingFood.MONKFISH; }

    @ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Number withdrawn each trip.", position = 2, section = consumables)
    default int foodAmount() { return 5; }

    @ConfigItem(keyName = "eatBelow", name = "Eat below HP %", description = "Eat below this health percentage. With no food to eat at or below it, the plugin stops.", position = 3, section = consumables)
    default int eatBelow() { return 30; }

    @ConfigItem(keyName = "dodgyAmount", name = "Dodgy necklaces", description = "Number withdrawn each trip.", position = 4, section = consumables)
    default int dodgyAmount() { return 5; }

    @ConfigItem(keyName = "keepItems", name = "Keep items", description = "Comma-separated item names to retain when banking.", position = 5, section = consumables)
    default String keepItems() { return ""; }

    @ConfigItem(keyName = "keepItemsAboveValue", name = "Keep items above value", description = "Do not drop items worth at least this many coins.", position = 6, section = consumables)
    default int keepItemsAboveValue() { return 10000; }

    @ConfigSection(name = "Magic", description = "Optional Shadow Veil support.", position = 1)
    String magic = "magic";

    @ConfigItem(keyName = "shadowVeil", name = "Shadow Veil", description = "Cast Shadow Veil when available. Requires the normal runes or a lava battlestaff.", position = 0, section = magic)
    default boolean shadowVeil() { return false; }

    @ConfigSection(name = "Adaptive behavior", description = "MKE-inspired native profile, timing, mouse and camera behavior adapted for elf thieving.", position = 2)
    String antiban = "antiban";

    @ConfigItem(keyName = "scheduledRests", name = "Short rests", description = "Keep DroElf's occasional 1-7 second rests while safely at the elf.", position = 0, section = antiban)
    default boolean scheduledRests() { return true; }

    @ConfigItem(keyName = "timedPauses", name = "Timed pauses", description = "Keep occasional 1-4 second pauses while safely at the elf.", position = 1, section = antiban)
    default boolean timedPauses() { return true; }

    @ConfigItem(keyName = "safeMisclicks", name = "Safe misclicks", description = "Keep the occasional nearby walkable-tile dead click while at the elf.", position = 2, section = antiban)
    default boolean safeMisclicks() { return true; }

    @ConfigItem(keyName = "humanizedTiming", name = "Adaptive timing", description = "Use MKE-style Gaussian reaction timing and occasional hesitation.", position = 3, section = antiban)
    default boolean humanizedTiming() { return true; }

    @ConfigItem(keyName = "randomMouseMovements", name = "Adaptive mouse nudges", description = "Allow occasional single post-click Natural Mouse nudges with anti-jitter throttling.", position = 4, section = antiban)
    default boolean randomMouseMovements() { return true; }

    @ConfigItem(keyName = "cameraTurns", name = "Subtle camera turns", description = "Occasionally use small MKE-style yaw/pitch turns while idle at the elf. No zoom changes.", position = 5, section = antiban)
    default boolean cameraTurns() { return true; }

    @Range(min = 5, max = 120)
    @ConfigItem(keyName = "cameraMovementFrequency", name = "Camera turn interval", description = "Minimum seconds before a subtle camera turn can be considered. Higher = less frequent.", position = 6, section = antiban)
    default int cameraMovementFrequency() { return 15; }

    @ConfigItem(keyName = "adaptiveActionCooldowns", name = "Adaptive action cooldowns", description = "Use Microbot's native play-style cooldown system after successful pickpockets.", position = 7, section = antiban)
    default boolean adaptiveActionCooldowns() { return true; }

    @ConfigSection(name = "Break manager", description = "MKE-style safe AFK/logout breaks. Due breaks wait until DroElf is naturally back at the locked elf.", position = 3)
    String breaks = "breaks";

    @ConfigItem(keyName = "enableCustomBreaks", name = "Enable smart breaks", description = "Enable MKE-style long AFK/logout breaks in addition to short DroElf rests.", position = 0, section = breaks)
    default boolean enableCustomBreaks() { return true; }

    @Range(min = 1, max = 360)
    @ConfigItem(keyName = "minBreakInterval", name = "Min break interval", description = "Minimum minutes between long breaks.", position = 1, section = breaks)
    default int minBreakInterval() { return 20; }

    @Range(min = 1, max = 480)
    @ConfigItem(keyName = "maxBreakInterval", name = "Max break interval", description = "Maximum minutes between long breaks.", position = 2, section = breaks)
    default int maxBreakInterval() { return 140; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "logoutBreakChance", name = "Logout break chance", description = "Chance that a long break logs out instead of remaining AFK.", position = 3, section = breaks)
    default int logoutBreakChance() { return 40; }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "afkBreakMinDuration", name = "AFK break min", description = "Minimum AFK break duration in minutes. Keep below your idle logout time.", position = 4, section = breaks)
    default int afkBreakMinDuration() { return 2; }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "afkBreakMaxDuration", name = "AFK break max", description = "Maximum AFK break duration in minutes. Keep below your idle logout time.", position = 5, section = breaks)
    default int afkBreakMaxDuration() { return 6; }

    @Range(min = 1, max = 180)
    @ConfigItem(keyName = "logoutBreakMinDuration", name = "Logout break min", description = "Minimum logout break duration in minutes.", position = 6, section = breaks)
    default int logoutBreakMinDuration() { return 5; }

    @Range(min = 1, max = 360)
    @ConfigItem(keyName = "logoutBreakMaxDuration", name = "Logout break max", description = "Maximum logout break duration in minutes.", position = 7, section = breaks)
    default int logoutBreakMaxDuration() { return 40; }

}
