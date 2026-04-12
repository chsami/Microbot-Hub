package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(

	name = PluginConstants.MOCROSOFT + "Agility",
	description = "Microbot agility plugin",
    authors = { "Mocrosoft" },
    version = MicroAgilityPlugin.version,
        minClientVersion = "2.1.0",
	tags = {"agility", "microbot"},
    iconUrl = "https://chsami.github.io/Microbot-Hub/MicroAgilityPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/MicroAgilityPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class MicroAgilityPlugin extends Plugin
{
	public static final String version = "1.2.6";
	@Inject
	private MicroAgilityConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MicroAgilityOverlay agilityOverlay;
	@Inject
	private AgilityScript agilityScript;


	@Provides
	MicroAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicroAgilityConfig.class);
	}
	public MicroAgilityConfig getConfig()
	{
		return config;
	}

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(agilityOverlay);
		}
        agilityScript.run();
    }

	protected void shutDown()
	{
		agilityScript.shutdown();
		overlayManager.remove(agilityOverlay);
	}

	public AgilityCourseHandler getCourseHandler()
	{
		return config.agilityCourse().getHandler();
	}

	public List<Rs2ItemModel> getInventoryFood()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> !(i.getName().toLowerCase().contains("summer pie"))).collect(Collectors.toList());
	}

	public List<Rs2ItemModel> getSummerPies()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> i.getName().toLowerCase().contains("summer pie")).collect(Collectors.toList());
	}

	public boolean hasRequiredLevel()
	{
		if (getSummerPies().isEmpty() || !getCourseHandler().canBeBoosted())
		{
			return Rs2Player.getRealSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
		}

		return Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
	}

	public AgilityScript getAgilityScript() {
		return agilityScript;
	}

}
