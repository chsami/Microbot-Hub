package net.runelite.client.plugins.microbot.leaguestoolkit;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("LeaguesToolkit")
@ConfigInformation("<h2>Leagues Toolkit <em>(BETA)</em></h2>" +
        "<h3>Version: " + LeaguesToolkitPlugin.version + "</h3>" +
        "<p><em>This plugin is in BETA. Not all features are fully polished — some boss helpers " +
        "may have edge cases in instanced areas. Use Prayer/eat only mode for boss helpers " +
        "if the full automation isn't working reliably.</em></p>" +
        "<hr/>" +
        "<p><strong>Anti-AFK:</strong> Presses a random arrow key before the idle timer logs you out. " +
        "Great for long AFK sessions with auto-bank relics (e.g. Endless Harvest).</p>" +
        "<p><strong>Demonic Gorilla Prayer:</strong> Event-driven prayer switching — reacts instantly " +
        "to gorilla attack animations via onAnimationChanged (no polling delay). " +
        "Tracks blocked hits (0 damage) to predict style switches (3 blocked = switch incoming). " +
        "Detects the 'Rhaaaaaaa!' overhead scream to confirm switches.</p>" +
        "<p><strong>Toci's Gem Store:</strong> Automated gem trading at Toci in Aldarin. Three modes:</p>" +
        "<ul>" +
        "<li><em>Buy &amp; Bank</em> — fast stockpile uncut gems via Banker's Briefcase Last-destination.</li>" +
        "<li><em>Buy, Cut &amp; Sell</em> — buy uncut, cut with chisel, sell cut back to Toci.</li>" +
        "<li><em>Buy, Cut &amp; Bank</em> — buy uncut, cut, bank via briefcase.</li>" +
        "</ul>" +
        "<p><strong>Wealthy Citizen Thieving:</strong> Pickpockets Wealthy citizens (Larcenist relic required " +
        "for 100% success). Opens coin pouches at configurable threshold (max 280).</p>" +
        "<p><strong>Easy Clue Opener:</strong> Aldarin bank easy clue farming. Opens scroll boxes, " +
        "digs if clue ID is 29853, drops non-dig clues, stacks caskets. Configurable delays.</p>" +
        "<p><strong>Hespori (Echo):</strong> Prayer switches between Magic and Missiles based on attack animation. " +
        "Kills flowers with correct combat style based on their overhead prayer (weapon switching). " +
        "Vine dodge moves to opposite quadrant when projectile 3680 is detected. " +
        "Prayer/eat only mode available for manual combat with automated prayer.</p>" +
        "<p><strong>Kraken Boss:</strong> Full automation — disturbs tentacles one by one (Disturb → Attack → kill), " +
        "then wakes and kills the boss. Loots Trident of the seas, Kraken tentacle, Jar of dirt, Pet kraken. " +
        "Keeps Protect from Magic on. Start inside the boss room with a slayer task.</p>" +
        "<p><strong>Snape Grass Telegrab:</strong> Walks to spawn, casts Telekinetic Grab on repeat. " +
        "Banks via briefcase Last-destination or walks to nearest bank when full. " +
        "Requires 33 Magic, law runes, air runes or air staff.</p>" +
        "<p><strong>Ourania Altar:</strong> Crafts runes at ZMI, banks via briefcase to Eniola. " +
        "Deposits only crafted runes (keeps air runes + noted pure essence). " +
        "Works with Transmutation running concurrently. Eniola requires auto-pay runes.</p>" +
        "<p><strong>Transmutation:</strong> Casts Alchemic Divergence/Convergence on noted items " +
        "to upgrade/downgrade through tiers. Dropdown selection for start and target items. " +
        "Shop-aware timeout (pauses detection when shop is open). " +
        "Have starting items noted in inventory before enabling.</p>")
public interface LeaguesToolkitConfig extends Config {

    @ConfigSection(
            name = "Anti-AFK",
            description = "Prevents the idle-timeout logout during long AFK sessions",
            position = 0
    )
    String antiAfkSection = "antiAfkSection";

    @ConfigItem(
            keyName = "enableAntiAfk",
            name = "Enable anti-AFK",
            description = "Periodically triggers input to reset the idle timer so you never get logged out",
            position = 0,
            section = antiAfkSection
    )
    default boolean enableAntiAfk() {
        return true;
    }

    @ConfigItem(
            keyName = "antiAfkMethod",
            name = "Input method",
            description = "What kind of input to send. Random arrow keys look most natural.",
            position = 1,
            section = antiAfkSection
    )
    default AntiAfkMethod antiAfkMethod() {
        return AntiAfkMethod.RANDOM_ARROW_KEY;
    }

    @Range(min = 50, max = 5000)
    @ConfigItem(
            keyName = "antiAfkBufferMin",
            name = "Trigger buffer min (ticks)",
            description = "Minimum ticks before the client's AFK threshold at which to fire input",
            position = 2,
            section = antiAfkSection
    )
    default int antiAfkBufferMin() {
        return 500;
    }

    @Range(min = 50, max = 5000)
    @ConfigItem(
            keyName = "antiAfkBufferMax",
            name = "Trigger buffer max (ticks)",
            description = "Maximum ticks before the client's AFK threshold at which to fire input",
            position = 3,
            section = antiAfkSection
    )
    default int antiAfkBufferMax() {
        return 1500;
    }

    @ConfigSection(
            name = "Demonic Gorilla Prayer",
            description = "Auto-switches protection prayers based on the gorilla's current attack style",
            position = 1,
            closedByDefault = true
    )
    String gorillaPrayerSection = "gorillaPrayerSection";

    @ConfigItem(
            keyName = "enableGorillaPrayer",
            name = "Enable",
            description = "Automatically switches between Protect from Melee, Missiles, and Magic " +
                    "based on which demonic gorilla variant is attacking you. " +
                    "Detects style changes by NPC ID transformation.",
            position = 0,
            section = gorillaPrayerSection
    )
    default boolean enableGorillaPrayer() {
        return false;
    }

    @ConfigSection(
            name = "Toci's Gem Store",
            description = "Automated gem buying, cutting, and selling/banking at Toci in Aldarin",
            position = 2,
            closedByDefault = true
    )
    String gemCutterSection = "gemCutterSection";

    @ConfigItem(
            keyName = "enableGemCutter",
            name = "Enable",
            description = "Walks to Toci's Gem Store in Aldarin and runs the selected mode. Requires coins in inventory.",
            position = 0,
            section = gemCutterSection
    )
    default boolean enableGemCutter() {
        return false;
    }

    @ConfigItem(
            keyName = "gemCutterMode",
            name = "Mode",
            description = "Buy & Bank: fast stockpile uncut gems (briefcase required). " +
                    "Buy, Cut & Sell: buy uncut, cut with chisel, sell cut back to Toci. " +
                    "Buy, Cut & Bank: buy uncut, cut, bank via briefcase.",
            position = 1,
            section = gemCutterSection
    )
    default GemCutterMode gemCutterMode() {
        return GemCutterMode.BUY_AND_BANK;
    }

    @ConfigItem(
            keyName = "gemType",
            name = "Gem",
            description = "Which gem to buy/cut. Cut modes require a chisel and the crafting level.",
            position = 2,
            section = gemCutterSection
    )
    default GemType gemType() {
        return GemType.RUBY;
    }

    @Range(min = 1000, max = 1_000_000)
    @ConfigItem(
            keyName = "gemCutterMinCoins",
            name = "Min coins to keep",
            description = "When coins drop below this, withdraw more from the bank",
            position = 3,
            section = gemCutterSection
    )
    default int gemCutterMinCoins() {
        return 10_000;
    }

    @ConfigSection(
            name = "Wealthy Citizen Thieving",
            description = "Pickpockets Wealthy citizens, opens coin pouches at a threshold",
            position = 3,
            closedByDefault = true
    )
    String thievingSection = "thievingSection";

    @ConfigItem(
            keyName = "enableThieving",
            name = "Enable",
            description = "Pickpockets the nearest Wealthy citizen with 100% success (Larcenist relic required). " +
                    "Opens coin pouches at the configured threshold. Stand near a Wealthy citizen before enabling.",
            position = 0,
            section = thievingSection
    )
    default boolean enableThieving() {
        return false;
    }

    @Range(min = 1, max = 280)
    @ConfigItem(
            keyName = "coinPouchThreshold",
            name = "Open pouches at",
            description = "Open coin pouches when this many have accumulated (max 280 before they auto-destroy)",
            position = 1,
            section = thievingSection
    )
    default int coinPouchThreshold() {
        return 200;
    }

    @ConfigSection(
            name = "Snape Grass Telegrab",
            description = "Telegrab snape grass at a fixed location",
            position = 4,
            closedByDefault = true
    )
    String snapeGrassSection = "snapeGrassSection";

    @ConfigItem(
            keyName = "enableSnapeGrass",
            name = "Enable",
            description = "Walks to snape grass spawn (1736, 3170), casts Telekinetic Grab, waits for respawn, repeats. " +
                    "Requires 33 Magic, law runes, and air runes or air staff equipped.",
            position = 0,
            section = snapeGrassSection
    )
    default boolean enableSnapeGrass() {
        return false;
    }

    @ConfigSection(
            name = "Easy Clue Opener",
            description = "Opens scroll boxes, digs dig-clues, drops non-dig clues, opens reward caskets",
            position = 5,
            closedByDefault = true
    )
    String easyClueSection = "easyClueSection";

    @ConfigItem(
            keyName = "enableEasyClue",
            name = "Enable",
            description = "Uses the Aldarin bank easy clue method to farm reward caskets. " +
                    "Opens Scroll box (easy), checks if the clue is a dig type (ID 29853) — " +
                    "if so, digs with spade repeatedly until you get a casket or a different clue. " +
                    "Non-dig clues are dropped and the next scroll box is opened. " +
                    "Caskets stack in your inventory. Requires a spade and scroll boxes.",
            position = 0,
            section = easyClueSection
    )
    default boolean enableEasyClue() {
        return false;
    }

    @Range(min = 100, max = 3000)
    @ConfigItem(
            keyName = "clueDigDelayMin",
            name = "Dig delay min (ms)",
            description = "Minimum delay after digging before the next action",
            position = 1,
            section = easyClueSection
    )
    default int clueDigDelayMin() {
        return 400;
    }

    @Range(min = 100, max = 3000)
    @ConfigItem(
            keyName = "clueDigDelayMax",
            name = "Dig delay max (ms)",
            description = "Maximum delay after digging before the next action",
            position = 2,
            section = easyClueSection
    )
    default int clueDigDelayMax() {
        return 700;
    }

    @Range(min = 50, max = 2000)
    @ConfigItem(
            keyName = "clueActionDelay",
            name = "Action delay (ms)",
            description = "Delay between opening scroll boxes, dropping clues, etc.",
            position = 3,
            section = easyClueSection
    )
    default int clueActionDelay() {
        return 300;
    }

    @ConfigSection(
            name = "Hespori (Echo)",
            description = "Prayer switching and fight management for Hespori Echo boss",
            position = 6,
            closedByDefault = true
    )
    String hesporiSection = "hesporiSection";

    @ConfigItem(
            keyName = "enableHespori",
            name = "Enable",
            description = "Manages the Hespori Echo fight. Auto-switches between Protect from Magic and Missiles " +
                    "based on attack animation, kills flowers with correct combat style based on their overhead prayer, " +
                    "dodges vine attacks. Uses a fast 150ms loop. " +
                    "Bring both a melee weapon and a ranged/magic weapon for flower phases.",
            position = 0,
            section = hesporiSection
    )
    default boolean enableHespori() {
        return false;
    }

    @ConfigItem(
            keyName = "hesporiPrayerOnly",
            name = "Prayer/eat only",
            description = "Only handle prayer switching, eating, and dodging. " +
                    "You control attacking and flower killing manually.",
            position = 1,
            section = hesporiSection
    )
    default boolean hesporiPrayerOnly() {
        return false;
    }

    @ConfigItem(
            keyName = "hesporiMainWeapon",
            name = "Main weapon (boss)",
            description = "Weapon to fight the boss with. Echo Hespori is weak to slash (e.g. Abyssal tentacle)",
            position = 1,
            section = hesporiSection
    )
    default String hesporiMainWeapon() {
        return "Abyssal tentacle";
    }

    @ConfigItem(
            keyName = "hesporiMeleeWeapon",
            name = "Melee weapon (flowers)",
            description = "Weapon for flowers praying ranged+magic. Can be same as main weapon if main is melee.",
            position = 2,
            section = hesporiSection
    )
    default String hesporiMeleeWeapon() {
        return "Abyssal tentacle";
    }

    @ConfigItem(
            keyName = "hesporiMageWeapon",
            name = "Magic/Ranged weapon (flowers)",
            description = "Weapon for flowers praying melee (e.g. Trident of the seas, Magic shortbow)",
            position = 3,
            section = hesporiSection
    )
    default String hesporiMageWeapon() {
        return "Trident of the seas";
    }

    @ConfigSection(
            name = "Kraken Boss",
            description = "Automates the Kraken boss fight — disturb tentacles, wake boss, attack, repeat",
            position = 7,
            closedByDefault = true
    )
    String krakenSection = "krakenSection";

    @ConfigItem(
            keyName = "enableKraken",
            name = "Enable",
            description = "Automates the Kraken boss fight. Keeps Protect from Magic on, disturbs " +
                    "all 4 tentacle whirlpools, wakes the boss, attacks until dead, repeats. " +
                    "Requires a Kraken slayer task and magic combat. Start inside the boss room.",
            position = 0,
            section = krakenSection
    )
    default boolean enableKraken() {
        return false;
    }

    @Range(min = 10, max = 90)
    @ConfigItem(
            keyName = "krakenEatAt",
            name = "Eat at HP %",
            description = "Eat food when HP drops below this percentage",
            position = 1,
            section = krakenSection
    )
    default int krakenEatAt() {
        return 50;
    }

    @ConfigItem(
            keyName = "krakenDrinkPrayer",
            name = "Drink prayer pots",
            description = "Automatically drink prayer potions",
            position = 2,
            section = krakenSection
    )
    default boolean krakenDrinkPrayer() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "krakenPrayerThreshold",
            name = "Drink prayer at",
            description = "Drink prayer potion when points drop below this",
            position = 3,
            section = krakenSection
    )
    default int krakenPrayerThreshold() {
        return 20;
    }

    @ConfigSection(
            name = "Ourania Altar",
            description = "Craft runes at the Ourania (ZMI) Altar with briefcase banking and transmutation",
            position = 8,
            closedByDefault = true
    )
    String ouraniaSection = "ouraniaSection";

    @ConfigItem(
            keyName = "enableOurania",
            name = "Enable",
            description = "Crafts runes at Ourania Altar. Banks via briefcase Last-destination to Eniola. " +
                    "Transmutation runs concurrently to convert air runes to noted pure essence. " +
                    "Keep air runes, briefcase (equipped), and transmutation ledger in inventory. " +
                    "Eniola requires runes for banking (auto-pay).",
            position = 0,
            section = ouraniaSection
    )
    default boolean enableOurania() {
        return false;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
            keyName = "ouraniaEatAtPercent",
            name = "Eat at HP %",
            description = "Eat food from bank when HP drops below this percentage",
            position = 1,
            section = ouraniaSection
    )
    default int ouraniaEatAtPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "ouraniaFoodName",
            name = "Food name",
            description = "Name of food to withdraw and eat at bank (e.g. Salmon, Lobster)",
            position = 2,
            section = ouraniaSection
    )
    default String ouraniaFoodName() {
        return "Salmon";
    }

    @ConfigSection(
            name = "Transmutation",
            description = "Casts Alchemic Divergence/Convergence to upgrade or downgrade noted items through tiers",
            position = 9,
            closedByDefault = true
    )
    String transmuteSection = "transmuteSection";

    @ConfigItem(
            keyName = "enableTransmute",
            name = "Enable transmutation",
            description = "Have the starting noted items in your inventory before enabling. " +
                    "The script casts the spell on each tier until it reaches the target. " +
                    "Requires the Transmutation relic and the transmutation ledger equipped or in inventory.",
            position = 0,
            section = transmuteSection
    )
    default boolean enableTransmute() {
        return false;
    }

    @ConfigItem(
            keyName = "transmuteStartItem",
            name = "Starting item",
            description = "The item you currently have noted in your inventory. Must be in the same category as the target.",
            position = 1,
            section = transmuteSection
    )
    default TransmuteItem transmuteStartItem() {
        return TransmuteItem.IRON_ORE;
    }

    @ConfigItem(
            keyName = "transmuteTargetItem",
            name = "Target item",
            description = "The final item you want. Must be in the same category as the starting item.",
            position = 2,
            section = transmuteSection
    )
    default TransmuteItem transmuteTargetItem() {
        return TransmuteItem.RUNITE_ORE;
    }

    @ConfigItem(
            keyName = "transmuteDirection",
            name = "Direction",
            description = "Upgrade (Alchemic Divergence / High Alch) or Downgrade (Alchemic Convergence / Low Alch)",
            position = 4,
            section = transmuteSection
    )
    default TransmuteDirection transmuteDirection() {
        return TransmuteDirection.UPGRADE;
    }
}
