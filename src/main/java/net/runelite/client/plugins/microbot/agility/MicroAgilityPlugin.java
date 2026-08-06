package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.api.gameval.ItemID;
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
	public static final String version = "1.3.1";
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

	@Override
	protected void shutDown()
	{
		if (overlayManager != null)
		{
			overlayManager.remove(agilityOverlay);
		}
		agilityScript.shutdown();
	}

	public AgilityCourseHandler getCourseHandler()
	{
		return config.agilityCourse().getHandler();
	}

	public void notifyUser(String message)
	{
		try
		{
			if (Microbot.getClient() != null)
			{
				Microbot.getClientThread().invoke(() ->
					Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", message, "Micro Agility", false));
			}
		}
		catch (Exception ex)
		{
			log.debug("Unable to add agility message to chat", ex);
		}
		Microbot.showMessage(message);
	}

	public List<Rs2ItemModel> getInventoryFood()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> !isSummerPie(i)).collect(Collectors.toList());
	}

	public List<Rs2ItemModel> getSummerPies()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(this::isSummerPie).collect(Collectors.toList());
	}

	public boolean hasRequiredLevel()
	{
		return hasRequiredLevel(getCourseHandler());
	}

	public boolean hasRequiredLevel(AgilityCourseHandler courseHandler)
	{
		int requiredLevel = courseHandler.getRequiredLevel();
		if (Rs2Player.getRealSkillLevel(Skill.AGILITY) >= requiredLevel)
		{
			return true;
		}

		if (!config.useSummerPies() || getSummerPies().isEmpty() || !courseHandler.canBeBoosted())
		{
			return false;
		}

		return Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= requiredLevel;
	}

	public boolean hasRealRequiredLevel(AgilityCourseHandler courseHandler)
	{
		return Rs2Player.getRealSkillLevel(Skill.AGILITY) >= courseHandler.getRequiredLevel();
	}

	public boolean canSummerPieMeetRequirement(AgilityCourseHandler courseHandler)
	{
		return courseHandler.canBeBoosted()
			&& Rs2Player.getRealSkillLevel(Skill.AGILITY) + 5 >= courseHandler.getRequiredLevel();
	}

	private boolean isSummerPie(Rs2ItemModel item)
	{
		return item != null && (item.getId() == ItemID.SUMMER_PIE || item.getId() == ItemID.HALF_SUMMER_PIE);
	}

	public AgilityScript getAgilityScript() {
		return agilityScript;
	}

}
