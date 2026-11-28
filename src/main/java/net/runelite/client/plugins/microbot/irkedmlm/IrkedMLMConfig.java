package net.runelite.client.plugins.microbot.irkedmlm;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.irkedmlm.enums.AfkParkSide;
import net.runelite.client.plugins.microbot.irkedmlm.enums.DepositMethod;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMSackSize;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MouseActivity;

@ConfigGroup(IrkedMLMConfig.configGroup)
@ConfigInformation(
		"Mines pay-dirt at the Motherlode Mine, fills the hopper, empties the sack and banks the ore at the deposit box.<br/><br/>" +
		"<b>You need:</b> a pickaxe and 30 Mining. The upper level needs 57 Mining and its 100-nugget unlock.<br/>" +
		"<b>Recommended:</b> a hammer or Imcando hammer for wheel repairs, and a gem bag.<br/>" +
		"<b>Start:</b> in the mine, with a mining area picked under Setup."
)
public interface IrkedMLMConfig extends Config
{
	String configGroup = "micro-irkedmlm";

	String depositMethod = "depositMethod";
	String antiCrash = "antiCrash";
	String dropGems = "dropGems";
	String useUpstairsHopper = "useUpstairsHopper";
	String miningArea = "miningArea";
	String sackSize = "sackSize";
	String logDetail = "logDetail";
	String stopAfterMinutes = "stopAfterMinutes";
	String stopAtLevel = "stopAtLevel";
	String logoutWhenFinished = "logoutWhenFinished";
	String showMiningAreas = "showMiningAreas";
	String useGemBag = "useGemBag";
	String enableHumanLikeBehavior = "enableHumanLikeBehavior";
	String afkParkSide = "afkParkSide";
	String mouseActivity = "mouseActivity";
	String useImcandoHammer = "useImcandoHammer";
	String waitForOthersToRepair = "waitForOthersToRepair";
	String hopWhenFrozen = "hopWhenFrozen";
	String frozenHopGraceSeconds = "frozenHopGraceSeconds";
	String repairStruts = "repairStruts";
	String overlayMode = "overlayMode";
	String useInventorySetup = "useInventorySetup";
	String inventorySetup = "inventorySetup";

	@ConfigSection(name = "Setup", description = "Where to mine and how the ore is banked.", position = 0)
	String setupSection = "setup";

	@ConfigSection(name = "Wheel repair", description = "What to do when both water wheels break.", position = 1)
	String repairSection = "repair";

	@ConfigSection(name = "Humanisation", description = "Pauses, mouse habits and pacing that make a run look played; turn off for speed.",
			position = 2, closedByDefault = true)
	String humanSection = "humanization";

	@ConfigSection(name = "Overlay", description = "The in-game session panel.", position = 3)
	String overlaySection = "overlay";

	// Show Mining Areas is developer tooling and stays hidden from the config panel.

	@ConfigItem(
			keyName = miningArea,
			name = "Mining area",
			description = "Which part of the mine to work; Any picks a lower area at random and changes now and then; the upper areas need 57 Mining and the upper-level unlock.",
			position = 0,
			section = setupSection
	)
	default MLMMiningSpot miningArea()
	{
		return MLMMiningSpot.WEST_LOWER;
	}

	@ConfigItem(
			keyName = useUpstairsHopper,
			name = "Use upper hopper",
			description = "Deposit upstairs when mining an upper area; needs the extra upper-hopper unlock, and falls back to the lower hopper if it is missing.",
			position = 1,
			section = setupSection
	)
	default boolean upstairsHopperUnlocked()
	{
		return false;
	}

	@ConfigItem(
			keyName = depositMethod,
			name = "Deposit method",
			description = "Deposit items banks each ore and gem type by hand; Deposit all uses the box button, but switches to item by item when you carry a pickaxe, hammer or bag it would bank.",
			position = 2,
			section = setupSection
	)
	default DepositMethod depositMethod()
	{
		return DepositMethod.ITEMS;
	}

	@ConfigItem(
			keyName = useGemBag,
			name = "Use gem bag",
			description = "Withdraws your gem bag if needed, locks it in slot 1 and opens it so mined gems go straight in; it is emptied and banked on sack trips.",
			position = 3,
			section = setupSection
	)
	default boolean useGemBag()
	{
		return false;
	}

	@ConfigItem(
			keyName = dropGems,
			name = "Drop gems",
			description = "Drops uncut gems as they are mined instead of banking them; ignored while Use gem bag is on.",
			position = 4,
			section = setupSection
	)
	default boolean dropGems()
	{
		return false;
	}

	@ConfigItem(
			keyName = antiCrash,
			name = "Avoid other players",
			description = "On the lower level, skips a vein another player is standing at; has no effect upstairs.",
			position = 5,
			section = setupSection
	)
	default boolean useAntiCrash()
	{
		return false;
	}

	@ConfigItem(
			keyName = sackSize,
			name = "Sack size before login",
			description = "Only used for the panel until the game reports the real sack size, which always wins.",
			position = 6,
			section = setupSection
	)
	default MLMSackSize sackSize()
	{
		return MLMSackSize.STANDARD;
	}

	@ConfigItem(
			keyName = useInventorySetup,
			name = "Use inventory setup",
			description = "Loads the inventory setup below at the bank chest before mining, instead of fetching a pickaxe itself.",
			position = 7,
			section = setupSection
	)
	default boolean useInventorySetup()
	{
		return false;
	}

	@ConfigItem(
			keyName = inventorySetup,
			name = "Inventory setup",
			description = "The Inventory Setups loadout to use; needs Use inventory setup on.",
			position = 8,
			section = setupSection
	)
	default InventorySetup inventorySetup()
	{
		return null;
	}

	@Range(min = 0, max = 1440)
	@Units(Units.MINUTES)
	@ConfigItem(
			keyName = stopAfterMinutes,
			name = "Stop after",
			description = "Stops after this much play time; time on a break does not count. 0 runs until you stop it.",
			position = 9,
			section = setupSection
	)
	default int stopAfterMinutes()
	{
		return 0;
	}

	@Range(min = 0, max = 99)
	@ConfigItem(
			keyName = stopAtLevel,
			name = "Stop at Mining level",
			description = "Stops when your Mining reaches this level. 0 never stops for a level.",
			position = 10,
			section = setupSection
	)
	default int stopAtLevel()
	{
		return 0;
	}

	@ConfigItem(
			keyName = logoutWhenFinished,
			name = "Log out when finished",
			description = "Logs out once a stop goal is reached, or when the run stops on a problem it cannot fix by itself.",
			position = 11,
			section = setupSection
	)
	default boolean logoutWhenFinished()
	{
		return true;
	}

	@ConfigItem(
			keyName = logDetail,
			name = "Log detail",
			description = "How much goes to client.log: Quiet writes problems only, Normal adds what the run is doing, Detailed adds every decision.",
			position = 12,
			section = setupSection
	)
	default LogDetail logDetail()
	{
		return LogDetail.NORMAL;
	}

	enum LogDetail
	{
		QUIET("Quiet"),
		NORMAL("Normal"),
		DETAILED("Detailed");

		private final String name;

		LogDetail(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigItem(
			keyName = repairStruts,
			name = "Repair struts",
			description = "Fixes a broken wheel on a deposit trip when both wheels have stopped; when off, the run waits for someone else to fix it.",
			position = 0,
			section = repairSection
	)
	default boolean repairStruts()
	{
		return true;
	}

	@ConfigItem(
			keyName = useImcandoHammer,
			name = "Use Imcando hammer",
			description = "Repairs with your own Imcando hammer instead of fetching one from the crate; needs Repair struts on.",
			position = 1,
			section = repairSection
	)
	default boolean useImcandoHammer()
	{
		return false;
	}

	@ConfigItem(
			keyName = waitForOthersToRepair,
			name = "Let others repair",
			description = "Waits instead of repairing while another player stands at the wheel, and takes the job back if they turn out to be idle.",
			position = 2,
			section = repairSection
	)
	default boolean waitForOthersToRepair()
	{
		return true;
	}

	@ConfigItem(
			keyName = hopWhenFrozen,
			name = "Hop when frozen",
			description = "While waiting on a frozen wheel, hops to the busiest world once the wait passes Frozen wait.",
			position = 3,
			section = repairSection
	)
	default boolean hopWhenFrozen()
	{
		return false;
	}

	@Range(min = 10, max = 900)
	@Units(Units.SECONDS)
	@ConfigItem(
			keyName = frozenHopGraceSeconds,
			name = "Frozen wait",
			description = "How long to wait for a repair before hopping; needs Hop when frozen on.",
			position = 4,
			section = repairSection
	)
	default int frozenHopGraceSeconds()
	{
		return 120;
	}

	@ConfigItem(
			keyName = enableHumanLikeBehavior,
			name = "Human-like behaviour",
			description = "Randomised waits, varied mouse habits and a delayed special attack; off runs at full speed with randomised click points.",
			position = 0,
			section = humanSection
	)
	default boolean enableHumanLikeBehavior()
	{
		return true;
	}

	@ConfigItem(
			keyName = afkParkSide,
			name = "AFK park side",
			description = "Which screen edge the mouse leaves by while mining is left to tick over; needs Human-like behaviour on.",
			position = 1,
			section = humanSection
	)
	default AfkParkSide afkParkSide()
	{
		return AfkParkSide.RANDOM;
	}

	@ConfigItem(
			keyName = mouseActivity,
			name = "Mouse activity",
			description = "How busy the cursor is during a mining bout, from AFK to Active; needs Human-like behaviour on.",
			position = 2,
			section = humanSection
	)
	default MouseActivity mouseActivity()
	{
		return MouseActivity.AFK;
	}

	@ConfigItem(
			keyName = overlayMode,
			name = "Info panel",
			description = "The session panel: Full, Compact (one line) or Off; right-click the panel to minimise or expand it.",
			position = 0,
			section = overlaySection
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.FULL;
	}

	enum OverlayMode
	{
		FULL("Full"),
		COMPACT("Compact"),
		OFF("Off");

		private final String name;

		OverlayMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigItem(
			keyName = showMiningAreas,
			name = "Show Mining Areas",
			description = "Draw the configured spot on the game map: green = selectable zone, blue = tiles the bot believes it can walk to right now, red = rockfall-blocked (static), orange = rockfalls learned this session.",
			position = 1,
			hidden = true
	)
	default boolean showMiningAreas()
	{
		return false;
	}

}
