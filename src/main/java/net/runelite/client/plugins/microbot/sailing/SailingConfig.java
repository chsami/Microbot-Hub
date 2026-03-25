package net.runelite.client.plugins.microbot.sailing;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.sailing.AlchOrder;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRanks;

import java.awt.*;

@ConfigGroup(SailingConfig.configGroup)
public interface SailingConfig extends Config {
	String configGroup = "micro-sailing";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Salvaging Highlight",
		description = "Shipwreck highlighting settings",
		position = 1
	)
	String highlightSection = "highlight";

	@ConfigSection(
		name = "Trials",
		description = "Barracuda Trials settings",
		position = 2
	)
	String trialsSection = "trials";

	@ConfigItem(
		keyName = "Salvgaging",
		name = "Salvgaging",
		description = "Enable this option to use salvaging.",
		position = 0,
		section = generalSection
	)
	default boolean salvaging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableAlching",
		name = "Enable Alching",
		description = "Enable high alching items when inventory is full.",
		position = 1,
		section = generalSection
	)
	default boolean enableAlching()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alchItems",
		name = "Alch items",
		description = "Comma-separated list of items to high alch when salvaging.",
		position = 1,
		section = generalSection
	)
	default String alchItems()
	{
		return "gold ring, sapphire ring, emerald ring, ruby ring, diamond ring, ruby bracelet, emerald bracelet, diamond bracelet, mithril scimitar";
	}

	@ConfigItem(
		keyName = "openCaskets",
		name = "Open Caskets",
		description = "Automatically open all caskets in your inventory before alching and dropping.",
		position = 2,
		section = generalSection
	)
	default boolean openCaskets()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alchOrder",
		name = "Alch Order",
		description = "Order in which to high alch items. LIST_ORDER follows your alch list. LEFT_TO_RIGHT sweeps row by row. RIGHT_TO_LEFT sweeps rows right to left. TOP_TO_BOTTOM sweeps column by column. BOTTOM_TO_TOP sweeps columns bottom to top.",
		position = 3,
		section = generalSection
	)
	default AlchOrder alchOrder()
	{
		return AlchOrder.LIST_ORDER;
	}

	@ConfigItem(
		keyName = "dropItems",
		name = "Drop items",
		description = "Comma-separated list of items to drop when salvaging.",
		position = 4,
		section = generalSection
	)
	default String dropItems()
	{
		return "casket, oyster pearl, oyster pearls, teak logs, steel nails, mithril nails, giant seaweed, mithril cannonball, adamant cannonball, elkhorn frag, plank, oak plank, hemp seed, flax seed, mahogany repair kit, teak repair kit, rum";
	}

	@ConfigItem(
		keyName = "salvagingHighlight",
		name = "Enable Highlighting",
		description = "Enable shipwreck highlighting overlay.",
		position = 0,
		section = highlightSection
	)
	default boolean salvagingHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "salvagingHighlightActiveWrecks",
		name = "Highlight Active Wrecks",
		description = "Highlight shipwrecks you can salvage.",
		position = 1,
		section = highlightSection
	)
	default boolean salvagingHighlightActiveWrecks()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "salvagingHighlightActiveWrecksColour",
		name = "Active Wrecks Colour",
		description = "Colour for active shipwrecks.",
		position = 2,
		section = highlightSection
	)
	default Color salvagingHighlightActiveWrecksColour()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "salvagingHighlightInactiveWrecks",
		name = "Highlight Inactive Wrecks",
		description = "Highlight depleted shipwrecks (stumps).",
		position = 3,
		section = highlightSection
	)
	default boolean salvagingHighlightInactiveWrecks()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "salvagingHighlightInactiveWrecksColour",
		name = "Inactive Wrecks Colour",
		description = "Colour for inactive shipwrecks (stumps).",
		position = 4,
		section = highlightSection
	)
	default Color salvagingHighlightInactiveWrecksColour()
	{
		return Color.GRAY;
	}

	@ConfigItem(
		keyName = "salvagingHighlightHighLevelWrecks",
		name = "Highlight High Level Wrecks",
		description = "Highlight shipwrecks above your sailing level.",
		position = 5,
		section = highlightSection
	)
	default boolean salvagingHighlightHighLevelWrecks()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "salvagingHighLevelWrecksColour",
		name = "High Level Wrecks Colour",
		description = "Colour for shipwrecks above your level.",
		position = 6,
		section = highlightSection
	)
	default Color salvagingHighLevelWrecksColour()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "trials",
		name = "Enable Trials",
		description = "Enable Barracuda Trials automation.",
		position = 0,
		section = trialsSection
	)
	default boolean trials()
	{
		return false;
	}

	@ConfigItem(
		keyName = "trialsRank",
		name = "Target Rank",
		description = "The rank route to follow during trials.",
		position = 1,
		section = trialsSection
	)
	default TrialRanks trialsRank()
	{
		return TrialRanks.Swordfish;
	}

	@ConfigItem(
		keyName = "showTrialRoute",
		name = "Show Route Overlay",
		description = "Show the trial route path on screen.",
		position = 2,
		section = trialsSection
	)
	default boolean showTrialRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoNavigate",
		name = "Auto Navigate",
		description = "Automatically navigate the boat along the route.",
		position = 3,
		section = trialsSection
	)
	default boolean autoNavigate()
	{
		return false;
	}
}
