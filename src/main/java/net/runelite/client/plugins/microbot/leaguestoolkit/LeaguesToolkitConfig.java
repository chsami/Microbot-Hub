package net.runelite.client.plugins.microbot.leaguestoolkit;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("LeaguesToolkit")
@ConfigInformation("<h2>Leagues Toolkit</h2>" +
        "<h3>Version: " + LeaguesToolkitPlugin.version + "</h3>" +
        "<p><strong>Anti-AFK:</strong> Presses a random arrow key before the idle timer kicks in. " +
        "Great for long AFK sessions with auto-bank relics (e.g. Endless Harvest).</p>" +
        "<p><strong>Toci's Gem Store:</strong> Walks to Toci in Aldarin, buys uncut gems, " +
        "and either sells cut gems back or banks them via the Banker's Briefcase. Three modes:</p>" +
        "<ul>" +
        "<li><em>Buy &amp; Bank</em> — fast stockpile: buy uncut gems, briefcase to bank, walk back, repeat.</li>" +
        "<li><em>Buy, Cut &amp; Sell</em> — buy uncut, cut with chisel, sell cut gems back to Toci for profit.</li>" +
        "<li><em>Buy, Cut &amp; Bank</em> — buy uncut, cut, bank via briefcase, walk back, repeat.</li>" +
        "</ul>" +
        "<p><strong>Wealthy Citizen Thieving:</strong> Pickpockets Wealthy citizens with auto coin pouch opening. " +
        "Requires the <strong>Larcenist relic</strong> for 100% success rate (no stuns). " +
        "Configure the pouch threshold (max 280 before they auto-destroy).</p>" +
        "<p><strong>Easy Clue Opener:</strong> Farms reward caskets using the Aldarin bank easy clue method. " +
        "Opens Scroll box (easy) — if the clue is a dig type, digs with spade repeatedly until a casket " +
        "or a different clue appears. Non-dig clues are dropped and the next scroll box opens. " +
        "Caskets stack in your inventory. Configurable action speed. Requires a spade and scroll boxes.</p>" +
        "<p><strong>Snape Grass Telegrab:</strong> Walks to the snape grass spawn and casts Telekinetic Grab " +
        "on repeat. Requires 33 Magic, law runes, and air runes (or air staff equipped). " +
        "Stops when inventory is full.</p>" +
        "<p><strong>Transmutation:</strong> Casts Alchemic Divergence or Convergence on noted items " +
        "to upgrade/downgrade through tiers (e.g. Iron ore all the way to Runite ore). " +
        "Have the starting items noted in your inventory before enabling. " +
        "Requires the Transmutation relic and the transmutation ledger.</p>")
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
            name = "Toci's Gem Store",
            description = "Automated gem buying, cutting, and selling/banking at Toci in Aldarin",
            position = 1,
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
            position = 2,
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
            position = 2,
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
            position = 4,
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
            name = "Transmutation",
            description = "Casts Alchemic Divergence/Convergence to upgrade or downgrade noted items through tiers",
            position = 5,
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
