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
            name = "Transmutation",
            description = "Casts Alchemic Divergence/Convergence to upgrade or downgrade noted items through tiers",
            position = 2,
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
