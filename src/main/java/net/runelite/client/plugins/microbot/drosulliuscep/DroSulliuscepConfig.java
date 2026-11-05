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
        "[Dro] Sulliuscep Run - cuts the six Sulliusceps on Fossil Island.<br/><br/>" +
        "<b>Requirements:</b> 65 Woodcutting, Fossil Island access (Bone Voyage), a rake, a usable axe, and the travel items for your travel mode. " +
        "Ferox needs a Ring of dueling and a Digsite pendant. POH needs an ornate rejuvenation pool, a mounted Digsite pendant, and house tablets or a Construction cape.<br/><br/>" +
        "<b>On start:</b> auto retaliate is turned off, and at the first bank visit everything in your inventory except the rake is deposited. " +
        "The script then takes out the travel items and any supplies you enabled.<br/><br/>" +
        "It teleports with the Digsite pendant, takes the Magic Mushtree to Sticky Swamp, cuts the six trees in order and clears the vines on the way. " +
        "After the last tree it banks and restores HP using your travel mode. " +
        "Turn on Jump on Rubber cap mushroom to loop without banking; it fills the mud pit with 9 mushrooms and still banks when food runs out.<br/><br/>" +
        "Numulites, caps, supplies and gear are always kept. Drop when full chooses whether mushrooms, Mort myre fungus or unidentified fossils are dropped. " +
        "Food and antipoison are off by default; turning them on is recommended."
)
public interface DroSulliuscepConfig extends Config
{
    String GROUP = "droSulliuscep";

    @ConfigSection(name = "Run", description = "What the script does on each lap.", position = 0)
    String run = "run";

    @ConfigItem(
            keyName = "dropMode",
            name = "Drop when full",
            description = "What to drop when the inventory is full: mushrooms, Mort myre fungus or both. The \"+ small fossils\" and \"+ ALL fossils\" options also drop unidentified fossils. Numulites, caps, supplies and gear are always kept.",
            position = 1,
            section = run
    )
    default DroSulliuscepDropMode dropMode() { return DroSulliuscepDropMode.BOTH; }

    @ConfigItem(
            keyName = "useAxeSpecial",
            name = "Use axe special",
            description = "Use the equipped axe's special attack when special energy is full.",
            position = 2,
            section = run
    )
    default boolean useAxeSpecial() { return true; }

    @ConfigItem(
            keyName = "jumpRubberCapMushroom",
            name = "Jump on Rubber cap mushroom",
            description = "Instead of banking after the last tree, jump on the Rubber cap mushroom to start the next lap straight away. The script fills the mud pit with 9 mushrooms it cuts along the way, so you don't need any to start. It still banks when the pit isn't filled in time, your inventory is too full, your enabled food runs out, or HP reaches the emergency level.",
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

    @ConfigSection(name = "Optional supplies", description = "Optional food and antipoison. Both are off by default.", position = 1)
    String supplies = "supplies";

    @ConfigItem(
            keyName = "useFood",
            name = "Use food",
            description = "Take food from the bank and eat it during the run.",
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
            description = "Take antipoison from the bank and drink it when poisoned. The Ferox pool also cures poison each time you bank.",
            position = 14,
            section = supplies
    )
    default boolean useAntipoison() { return false; }

    @ConfigItem(keyName = "antiPoisonName", name = "Antipoison", description = "Potion name to maintain when antipoison is enabled.", position = 15, section = supplies)
    default String antiPoisonName() { return "Antipoison"; }

    @Range(min = 1, max = 4)
    @ConfigItem(keyName = "antiPoisonAmount", name = "Antipoison count", description = "Potion items to maintain per trip when enabled.", position = 16, section = supplies)
    default int antiPoisonAmount() { return 1; }

    @ConfigSection(name = "Travel", description = "How the script gets to the bank and back to Sticky Swamp.", position = 2)
    String travel = "travel";

    @ConfigItem(
            keyName = "travelMode",
            name = "Travel mode",
            description = "Ferox uses a dueling ring and carried Digsite pendant. POH modes bank first, then restore at an ornate pool and use a mounted Digsite pendant. An equipped or carried Construction cape is detected automatically. A banked Construction cape is withdrawn automatically; house tablets are the fallback. Restart the script after changing mode.",
            position = 20,
            section = travel
    )
    default DroSulliuscepTravelMode travelMode() { return DroSulliuscepTravelMode.FEROX_DUELING; }

    @ConfigSection(name = "Dro Profile", description = "Mouse movement and break settings.", position = 3)
    String profile = "profile";

    @ConfigItem(keyName = "mouseActivity", name = "Mouse activity", description = "How much the mouse moves around between actions. Lower settings move it less.", position = 30, section = profile)
    default BaseProfileDro.MouseActivity mouseActivity() { return BaseProfileDro.MouseActivity.BALANCED; }

    @ConfigItem(keyName = "parkSide", name = "Park side", description = "Which side of the game window the mouse rests off-screen while waiting.", position = 31, section = profile)
    default BaseProfileDro.AfkParkSide parkSide() { return BaseProfileDro.AfkParkSide.RANDOM; }

    @ConfigItem(keyName = "smartBreaks", name = "Smart breaks", description = "Take breaks at random times. Breaks only start at the bank, never in the swamp.", position = 32, section = profile)
    default boolean smartBreaks() { return false; }

    @Range(min = 1, max = 300)
    @ConfigItem(keyName = "breakMin", name = "Break interval min", description = "Shortest time in minutes between breaks.", position = 33, section = profile)
    default int breakIntervalMin() { return 35; }

    @Range(min = 1, max = 300)
    @ConfigItem(keyName = "breakMax", name = "Break interval max", description = "Longest time in minutes between breaks.", position = 34, section = profile)
    default int breakIntervalMax() { return 140; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "logoutBreakChance", name = "Logout break %", description = "Chance that a break logs you out. Otherwise you stay logged in and idle.", position = 35, section = profile)
    default int logoutBreakChance() { return 100; }

    @Range(min = 1, max = 60)
    @ConfigItem(keyName = "afkBreakMin", name = "AFK break min", description = "Shortest logged-in break, in minutes.", position = 36, section = profile)
    default int afkBreakMin() { return 2; }

    @Range(min = 1, max = 60)
    @ConfigItem(keyName = "afkBreakMax", name = "AFK break max", description = "Longest logged-in break, in minutes.", position = 37, section = profile)
    default int afkBreakMax() { return 6; }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "logoutBreakMin", name = "Logout min", description = "Shortest logged-out break, in minutes.", position = 38, section = profile)
    default int logoutBreakMin() { return 5; }

    @Range(min = 1, max = 180)
    @ConfigItem(keyName = "logoutBreakMax", name = "Logout max", description = "Longest logged-out break, in minutes.", position = 39, section = profile)
    default int logoutBreakMax() { return 40; }

    @Range(min = 0, max = 60)
    @ConfigItem(keyName = "postLoginSettle", name = "Post-login settle", description = "Seconds to wait after logging back in before the script continues.", position = 40, section = profile)
    default int postLoginSettleSeconds() { return 12; }
}
