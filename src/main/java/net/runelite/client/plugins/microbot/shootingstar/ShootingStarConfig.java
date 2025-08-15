package net.runelite.client.plugins.microbot.shootingstar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarProvider;

@ConfigGroup(ShootingStarConfig.configGroup)
@ConfigInformation(
	"• This plugin will assist in finding & traveling to shooting stars. <br />" +
		"• Configure inventory setup name or the plugin will look for best owned pickaxe <br />" +
		"• Start the plugin it will configure the inventory, if needed, then travel based on configuration settings <br />"
)
public interface ShootingStarConfig extends Config
{

	String configGroup = "shooting-star";
	String displayAsMinutes = "displayAsMinutes";
	String hideWildernessLocations = "hideWildernessLocations";
	String useInventorySetups = "useInventorySetups";
	String InventorySetup = "inventorySetup";
	String useNearestHighTierStar = "useNearestHighTierStar";
	String useBreakAtBank = "useBreakAtBank";
	String hideOverlay = "hideOverlay";
	String hideDevOverlay = "hideDevOverlay";
	String blacklistedLocations = "blacklistedLocations";
	String providerName = "providerName";
	String shutDownOnDeath = "shutDownOnDeath";

	@ConfigSection(
		name = "General Settings",
		description = "Configure general plugin configuration & preferences",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = useInventorySetups,
		name = "Use Inventory Setups",
		description = "Should use Inventory Setups",
		position = 0,
		section = generalSection
	)
	default boolean useInventorySetup()
	{
		return false;
	}

	@ConfigItem(
		keyName = "inventorySetupName",
		name = "Setup Name",
		description = "Name of mInventory Setup for mining",
		position = 1,
		section = generalSection
	)
	default InventorySetup inventorySetup()
	{
		return null;
	}

	@ConfigItem(
		keyName = useNearestHighTierStar,
		name = "Use Nearest High Tier Star",
		description = "Toggles automatic mode - when enabled script will automatically check all stars within an acceptable tier range & find the closest star that has the highest tier",
		position = 2,
		section = generalSection
	)
	default boolean useNearestHighTierStar()
	{
		return false;
	}

	@ConfigItem(
		keyName = useBreakAtBank,
		name = "Use Break at Bank",
		description = "Toggles breaks at the bank - when enabled script will force the player to a bank before triggering the Break Handler",
		position = 3,
		section = generalSection
	)
	default boolean useBreakAtBank()
	{
		return false;
	}

	@ConfigItem(
		keyName = shutDownOnDeath,
		name = "Shutdown on Death",
		description = "Toggles shutdown on death - when enabled script will shutdown if the player dies",
		position = 4,
		section = generalSection
	)
	default boolean shutdownOnDeath()
	{
		return false;
	}

	@ConfigSection(
		name = "Panel Settings",
		description = "Configure view settings within the panel",
		position = 1
	)
	String panelSection = "panel";

	@ConfigItem(
		keyName = providerName,
		name = "Provider",
		description = "Select the provider for shooting star data",
		position = 0,
		section = panelSection
	)
	default ShootingStarProvider getProvider() {
		return ShootingStarProvider.ZERO_SEVEN;
	}

	@ConfigItem(
		keyName = hideWildernessLocations,
		name = "Hide Wilderness Locations",
		description = "Hide Wilderness locations inside of the panel",
		position = 1,
		section = panelSection
	)
	default boolean isHideWildernessLocations()
	{
		return true;
	}

	@ConfigItem(
		keyName = displayAsMinutes,
		name = "Display as Minutes",
		description = "Shows time left as minutes",
		position = 2,
		section = panelSection
	)
	default boolean isDisplayAsMinutes()
	{
		return false;
	}

	@ConfigSection(
		name = "Overlay Settings",
		description = "Configure overlay settings",
		position = 2
	)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = hideOverlay,
		name = "Hide Overlay",
		description = "Hide overlay",
		position = 0,
		section = overlaySection
	)
	default boolean isHideOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = hideDevOverlay,
		name = "Hide Dev Overlay",
		description = "Hide developer overlay",
		position = 1,
		section = overlaySection
	)
	default boolean isHideDevOverlay()
	{
		return true;
	}
}
