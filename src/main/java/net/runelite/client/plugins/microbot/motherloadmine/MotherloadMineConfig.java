package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.motherloadmine.enums.MLMMiningSpotList;

@ConfigGroup(MotherloadMineConfig.configGroup)
@ConfigInformation(
	"• This plugin will automate mining in motherload mine <br />" +
	"• If using deposit all feature, <b>ensure you lock the slots you wish to keep in inventory</b> <br />" +
	"• Start near the bank chest in motherload mine <br />"
)
public interface MotherloadMineConfig extends Config
{
	String configGroup = "micro-motherloadmine";

	String useInventorySetup = "useInventorySetup";
	String inventorySetup = "inventory-setup";
	String useDepositAll = "useDepositAll";
	String antiCrash = "antiCrash";
	String useUpstairsMine = "useUpstairsMine";
	String useUpstairsHopper = "useUpstairsHopper";
	String miningArea = "miningArea";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Features",
		description = "Feature Settings",
		position = 1
	)
	String featureSection = "features";

	@ConfigItem(
		keyName = useInventorySetup,
		name = "Enable Inventory Setup",
		description = "Enable this option to use an inventory setup with the plugin",
		position = 0,
		section = generalSection
	)
	default boolean useInventorySetup()
	{
		return false;
	}

	@ConfigItem(
		keyName = inventorySetup,
		name = "Inventory Setup",
		description = "Select the inventory setup to use with the plugin",
		position = 1,
		section = generalSection
	)
	default InventorySetup getInventorySetup()
	{
		return null;
	}

	@ConfigItem(
		keyName = useDepositAll,
		name = "Use Deposit All",
		description = "Uses deposit all button in the deposit box<br>" +
			"Note: ensure you enable locked slots enabled for the items you want to keep in your inventory",
		position = 2,
		section = generalSection
	)
	default boolean useDepositAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = antiCrash,
		name = "Anti Crash",
		description = "Avoids other players when mining in the lower level",
		position = 3,
		section = generalSection
	)
	default boolean useAntiCrash()
	{
		return false;
	}

	// Mine upstairs
	@ConfigItem(
		keyName = useUpstairsMine,
		name = "Use Mine Upstairs",
		description = "Should the plugin use the upstairs mining area",
		position = 0,
		section = featureSection
	)
	default boolean mineUpstairs()
	{
		return false;
	}

	// Upstairs hopper unlocked
	@ConfigItem(
		keyName = useUpstairsHopper,
		name = "Use Upstairs Hopper",
		description = "Should the plugin use the upstairs hopper",
		position = 1,
		section = featureSection
	)
	default boolean upstairsHopperUnlocked()
	{
		return false;
	}

	// Mining Area Selection
	@ConfigItem(
		keyName = miningArea,
		name = "Mining Area",
		description = "Choose the specific area to mine in Motherload Mine",
		position = 2,
		section = featureSection
	)
	default MLMMiningSpotList miningArea()
	{
		return MLMMiningSpotList.ANY;
	}
}
