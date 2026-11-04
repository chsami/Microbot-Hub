/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.client.config.*;

@ConfigGroup(DroSulliuscepConfig.GROUP)
@ConfigInformation(
        "[Dro] Sulliuscep Run - script-owned travel + recorded swamp course.<br/><br/>" +
        "<b>Requirements:</b> 65 Woodcutting, Fossil Island/Bone Voyage access, a rake, a usable axe, and travel items for the selected mode. " +
        "Ferox requires a dueling ring and Digsite pendant. POH requires an ornate rejuvenation pool and mounted Digsite pendant, plus a house tablet or Construction cape.<br/><br/>" +
        "The script handles Digsite pendant and Mushtree travel directly. Built-in WebWalk handles pool/bank approaches when their objects are not nearby. " +
        "Once Sticky Swamp is reached, the walker is cancelled and the six Sulliusceps use the recorded tree/vine/rake route directly. " +
        "Bank and restoration travel follows the selected mode. " +
        "Select Jump on mushroom for no banking re-runs, autofills mudpit with 9 mushrooms. Will bank when no food detected.<br/><br/>" +
        "Numulites/fossils always kept. Choose to drop mushrooms, Mort myre fungus, or both. " +
        "Food and antipoison are suggested (disabled by default)."
)
public interface DroSulliuscepConfig extends Config
{
    String GROUP = "droSulliuscep";

    @ConfigSection(name = "Run", description = "Core Sulliuscep behavior learned from the recorded run.", position = 0)
    String run = "run";

    @ConfigItem(
            keyName = "dropMode",
            name = "Drop when full",
            description = "Choose mushrooms/fungus and optionally small or all unidentified fossils to drop when full. Numulite, caps, supplies and gear are kept.",
            position = 1,
            section = run
    )
    default DroSulliuscepDropMode dropMode() { return DroSulliuscepDropMode.BOTH; }

    @ConfigItem(
            keyName = "useAxeSpecial",
            name = "Use axe special",
            description = "Use the equipped axe special when 100% special energy is available. The recording used it during the run.",
            position = 2,
            section = run
    )
    default boolean useAxeSpecial() { return true; }

    @ConfigItem(
            keyName = "jumpRubberCapMushroom",
            name = "Jump on Rubber cap mushroom",
            description = "Loop with the Rubber cap mushroom when ready. Mushrooms are NOT required to start: if 9 are available at tree #1 the mud pit is filled immediately; otherwise the course runs normally and gathers them. After tree #6, a loaded pit triggers Jump on even with no spare mushrooms. Only tree #6 starts reserving mushrooms, and only with more than 15 usable slots after disposable cleanup; otherwise normal dropping continues and it banks after the complete run. Tree #1 keeps any shortfall until nine are available, fills the pit, then resumes normal dropping. Returns to the selected bank if the pit is not loaded or enabled food supplies are exhausted.",
            position = 5,
            section = run
    )
    default boolean jumpRubberCapMushroom() { return false; }

    @Range(min = 10, max = 60)
    @ConfigItem(
            keyName = "emergencyRetreatHp",
            name = "Emergency retreat HP %",
            description = "Independent safety floor. At or below this HP percentage the script leaves for the selected bank even when food is disabled.",
            position = 6,
            section = run
    )
    default int emergencyRetreatHp() { return 25; }

    @ConfigSection(name = "Optional supplies", description = "Optional safety supplies. The recorded run used neither food nor antipoison.", position = 1)
    String supplies = "supplies";

    @ConfigItem(
            keyName = "useFood",
            name = "Use food",
            description = "Enable food withdrawal/eating. Disabled by default to match the recording.",
            position = 10,
            section = supplies
    )
    default boolean useFood() { return false; }

    @ConfigItem(keyName = "foodName", name = "Food", description = "Food name to maintain at the selected bank when Use food is enabled.", position = 11, section = supplies)
    default String foodName() { return "Shark"; }

    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Food items to maintain per trip when enabled.", position = 12, section = supplies)
    default int foodAmount() { return 8; }

    @Range(min = 20, max = 95)
    @ConfigItem(keyName = "eatAt", name = "Eat at HP %", description = "Eat at or below this HP percentage when food is enabled.", position = 13, section = supplies)
    default int eatAt() { return 55; }

    @ConfigItem(
            keyName = "useAntipoison",
            name = "Use antipoison",
            description = "Enable antipoison withdrawal/use. Disabled by default to match the recording; Ferox pool clears poison on normal resets.",
            position = 14,
            section = supplies
    )
    default boolean useAntipoison() { return false; }

    @ConfigItem(keyName = "antiPoisonName", name = "Antipoison", description = "Potion name to maintain when antipoison is enabled.", position = 15, section = supplies)
    default String antiPoisonName() { return "Antipoison"; }

    @Range(min = 1, max = 4)
    @ConfigItem(keyName = "antiPoisonAmount", name = "Antipoison count", description = "Potion items to maintain per trip when enabled.", position = 16, section = supplies)
    default int antiPoisonAmount() { return 1; }

    @ConfigSection(name = "Travel", description = "The script owns pendant/Mushtree travel and uses built-in WebWalk for ordinary approaches. The Sticky Swamp Sulliuscep course uses the recorded local route.", position = 2)
    String travel = "travel";

    @ConfigItem(
            keyName = "travelMode",
            name = "Travel mode",
            description = "Ferox uses a dueling ring and carried Digsite pendant. POH modes bank first, then restore at an ornate pool and use a mounted Digsite pendant. An equipped or carried Construction cape is detected automatically. A banked Construction cape is withdrawn automatically; house tablets are the fallback. Restart the script after changing mode.",
            position = 20,
            section = travel
    )
    default DroSulliuscepTravelMode travelMode() { return DroSulliuscepTravelMode.FEROX_DUELING; }

    @ConfigSection(name = "Dro Profile", description = "BaseProfileDro behavior.", position = 3)
    String profile = "profile";

    @ConfigItem(keyName = "mouseActivity", name = "Mouse activity", description = "Dro mouse activity profile.", position = 30, section = profile)
    default BaseProfileDro.MouseActivity mouseActivity() { return BaseProfileDro.MouseActivity.BALANCED; }

    @ConfigItem(keyName = "parkSide", name = "Park side", description = "Preferred off-screen parking side.", position = 31, section = profile)
    default BaseProfileDro.AfkParkSide parkSide() { return BaseProfileDro.AfkParkSide.RANDOM; }

    @ConfigItem(keyName = "smartBreaks", name = "Smart breaks", description = "Enable BaseProfileDro smart breaks. New breaks are only allowed from the selected safe bank area.", position = 32, section = profile)
    default boolean smartBreaks() { return false; }

    @Range(min = 1, max = 300)
    @ConfigItem(keyName = "breakMin", name = "Break interval min", description = "Minutes between smart breaks.", position = 33, section = profile)
    default int breakIntervalMin() { return 35; }

    @Range(min = 1, max = 300)
    @ConfigItem(keyName = "breakMax", name = "Break interval max", description = "Minutes between smart breaks.", position = 34, section = profile)
    default int breakIntervalMax() { return 140; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "logoutBreakChance", name = "Logout break %", description = "Chance a smart break logs out.", position = 35, section = profile)
    default int logoutBreakChance() { return 100; }

    @Range(min = 1, max = 60)
    @ConfigItem(keyName = "afkBreakMin", name = "AFK break min", description = "Minimum AFK-break minutes.", position = 36, section = profile)
    default int afkBreakMin() { return 2; }

    @Range(min = 1, max = 60)
    @ConfigItem(keyName = "afkBreakMax", name = "AFK break max", description = "Maximum AFK-break minutes.", position = 37, section = profile)
    default int afkBreakMax() { return 6; }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "logoutBreakMin", name = "Logout min", description = "Minimum logout-break minutes.", position = 38, section = profile)
    default int logoutBreakMin() { return 5; }

    @Range(min = 1, max = 180)
    @ConfigItem(keyName = "logoutBreakMax", name = "Logout max", description = "Maximum logout-break minutes.", position = 39, section = profile)
    default int logoutBreakMax() { return 40; }

    @Range(min = 0, max = 60)
    @ConfigItem(keyName = "postLoginSettle", name = "Post-login settle", description = "Seconds to settle after a logout break.", position = 40, section = profile)
    default int postLoginSettleSeconds() { return 12; }
}
