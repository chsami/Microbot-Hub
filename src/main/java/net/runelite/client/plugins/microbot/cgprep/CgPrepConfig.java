package net.runelite.client.plugins.microbot.cgprep;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.PrepProfile;

@ConfigGroup(CgPrepConfig.GROUP)
public interface CgPrepConfig extends Config
{
	String GROUP = "cgprep";

	@ConfigSection(name = "Run", description = "What the bot does each run", position = 0)
	String runSection = "run";

	@ConfigSection(name = "Boss fight", description = "Food and potions in the boss fight", position = 1)
	String bossSection = "boss";

	@ConfigSection(name = "Prayers", description = "Offensive prayers for prep and the boss", position = 2)
	String prayerSection = "prayers";

	@ConfigSection(name = "Hotkeys", description = "Pause and continue keys", position = 3)
	String hotkeySection = "hotkeys";

	@ConfigItem(keyName = "meleePrayer", name = "Melee prayer",
		description = "Offensive prayer for melee. None turns it off.",
		position = 0, section = prayerSection)
	default net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Melee meleePrayer()
	{
		return net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Melee.PIETY;
	}

	@ConfigItem(keyName = "rangedPrayer", name = "Ranged prayer",
		description = "Offensive prayer for the bow. None turns it off.",
		position = 1, section = prayerSection)
	default net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Ranged rangedPrayer()
	{
		return net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Ranged.RIGOUR;
	}

	@ConfigItem(keyName = "magicPrayer", name = "Magic prayer",
		description = "Offensive prayer for the staff. None turns it off.",
		position = 2, section = prayerSection)
	default net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Magic magicPrayer()
	{
		return net.runelite.client.plugins.microbot.cgprep.fight.OffensePrayers.Magic.AUGURY;
	}

	// Items marked hidden are tuning/testing knobs: not shown in the panel, their defaults are the
	// values the bot was tested with. They can still be changed through the config file / ConfigManager.

	// ------------------------------------------------------------------ run

	@ConfigItem(keyName = "mode", name = "Gauntlet", description = "Corrupted, or the normal Gauntlet for practice.",
		position = 0, section = runSection)
	default CgMode mode()
	{
		return CgMode.CORRUPTED;
	}

	@ConfigItem(keyName = "profile", name = "Loadout",
		description = "The gear to make. T2 armour with T3 bow and staff suits most accounts.",
		position = 1, section = runSection)
	default PrepProfile profile()
	{
		return PrepProfile.T2_ARMOUR_T3_BOW_STAFF;
	}

	@Range(min = 0, max = 24)
	@ConfigItem(keyName = "fishTarget", name = "Paddlefish target", description = "Cooked paddlefish to bring to the boss.",
		position = 2, section = runSection)
	default int fishTarget()
	{
		return 14;
	}

	@Range(min = 0, max = 24)
	@ConfigItem(keyName = "fishMin", name = "Paddlefish minimum",
		description = "Enough fish: past this, more are only taken from a spot right by us or on the way",
		position = 100, hidden = true)
	default int fishMin()
	{
		return 12;
	}

	@Range(min = 0, max = 6)
	@ConfigItem(keyName = "potionTarget", name = "Egniol potions",
		description = "How many Egniol potions to brew each run (one grym leaf each).",
		position = 3, section = runSection)
	default int potionTarget()
	{
		return 3;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "shardBuffer", name = "Extra shards", description = "Safety margin on top of the computed shard need",
		position = 100, hidden = true)
	default int shardBuffer()
	{
		return 10;
	}

	// ------------------------------------------------------------------ timing

	@Range(min = 30, max = 180)
	@ConfigItem(keyName = "finishReserve", name = "Finish reserve (s)",
		description = "Stop gathering when this many seconds are left, to walk home, craft, brew and cook",
		position = 100, hidden = true)
	default int finishReserve()
	{
		return 75;
	}

	@Range(min = 0, max = 120)
	@ConfigItem(keyName = "handoverReserve", name = "Handover reserve (s)",
		description = "Extra seconds left for YOU after handover (added to the finish reserve). With 'Boss fight' ON there's no handover and at most 8 s are kept, to walk into the boss room",
		position = 100, hidden = true)
	default int handoverReserve()
	{
		return 30;
	}

	@Range(min = 0, max = 180)
	@ConfigItem(keyName = "demiBossBudget", name = "Demi-boss budget (s)",
		description = "Keep hunting demi-bosses until this much time is left beyond the finishing reserve. 20 by default (0.4.9): the prep simulator found the Dark Beast missed in ~5% of preps at 60, ~1% at 20",
		position = 100, hidden = true)
	default int demiBossBudget()
	{
		return 20;
	}

	@ConfigItem(keyName = "vialsSmash", name = "Empty vials vanish",
		description = "Learned automatically the first time a potion is drunk empty: on (vial smashing) every potion needs its own vial",
		position = 100, hidden = true)
	default boolean vialsSmash()
	{
		return true; // seen on the user's account in the 0.3.18 and 0.3.19 runs; re-learned in game
	}

	@ConfigItem(keyName = "autoRetaliateOff", name = "Turn auto-retaliate off",
		description = "Auto-retaliate makes the character stop and fight anything that hits it while it's idle (0.3.26: stood still for a spider on the way home). Turned off once, outside or at the bowl",
		position = 100, hidden = true)
	default boolean autoRetaliateOff()
	{
		return true;
	}

	@ConfigItem(keyName = "lazyFlick", name = "Lazy-flick attack prayers",
		description = "Offensive prayer (Piety / Mystic Vigour / Rigour) on the tick before each of our attacks and off on the attack tick",
		position = 100, hidden = true)
	default boolean lazyFlick()
	{
		return true;
	}

	@ConfigItem(keyName = "weaveAttacks", name = "Weave attacks while gathering",
		description = "A weak monster hitting us at a node: attack it right after a resource lands, then click the node again (kills it, saves prayer, farms shards)",
		position = 100, hidden = true)
	default boolean weaveAttacks()
	{
		return true;
	}

	@ConfigItem(keyName = "decantMethod", name = "Decant potions (2 vials)",
		description = "Brew two potions, decant into a 4-dose + 2-dose, sip the 2-dose during prep, brew the last potion in its vial: boss gets 4 + 3 doses",
		position = 100, hidden = true)
	default boolean decantMethod()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "eatBelowPercent", name = "Eat below HP %",
		description = "During prep (fighting, gathering, walking): eat a cooked paddlefish when HP drops below this % of max",
		position = 100, hidden = true)
	default int eatBelowPercent()
	{
		return 70;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "bailBelowHp", name = "Give up fight below HP", description = "With no food left",
		position = 100, hidden = true)
	default int bailBelowHp()
	{
		return 25;
	}

	// ------------------------------------------------------------------ handover

	@ConfigItem(keyName = "autoLoop", name = "Loot chest + go again",
		description = "After each run, loot the chest and enter the next run. In Boss Only mode the prep timer then starts right away: you have to prep it yourself.",
		position = 4, section = runSection)
	default boolean autoLoop()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyRunEnd", name = "Notify on win / death",
		description = "A desktop notification when a run ends in a kill or a death.",
		position = 5, section = runSection)
	default boolean notifyRunEnd()
	{
		return true;
	}

	@Range(min = 1, max = 27)
	@ConfigItem(keyName = "bankBelowFreeSlots", name = "Bank loot below free slots",
		description = "Loot stays in the inventory between runs; bank everything at the lobby deposit box when fewer than this many slots are free before opening the chest",
		position = 100, hidden = true)
	default int bankBelowFreeSlots()
	{
		return 5;
	}

	@ConfigItem(keyName = "popup", name = "Popup on handover", description = "In-client popup with the summary",
		position = 100, hidden = true)
	default boolean popup()
	{
		return true;
	}

	@ConfigItem(keyName = "notify", name = "Desktop notification", description = "Flash/notify via RuneLite's notifier",
		position = 100, hidden = true)
	default boolean notifyOnHandover()
	{
		return true;
	}

	@ConfigItem(keyName = "continueKey", name = "Continue hotkey",
		description = "Start the next run (when looping is off).",
		position = 1, section = hotkeySection)
	default Keybind continueKey()
	{
		return new Keybind(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(keyName = "pauseKey", name = "Pause hotkey", description = "Pause or resume the bot.",
		position = 0, section = hotkeySection)
	default Keybind pauseKey()
	{
		return new Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	// ------------------------------------------------------------------ antiban

	@ConfigItem(keyName = "useAntiban", name = "Use Microbot antiban",
		description = "Natural mouse, reaction gaps and short action cooldowns from the shared Antiban panel."
			+ " Cooldowns only happen in the start room, out of combat, with time to spare. Applies on plugin start.",
		position = 100, hidden = true)
	default boolean useAntiban()
	{
		return true;
	}

	@ConfigItem(keyName = "varyOrder", name = "Vary task order",
		description = "Each run picks a different (equally good) order for crafting and cooking in the start room",
		position = 100, hidden = true)
	default boolean varyOrder()
	{
		return true;
	}

	// ------------------------------------------------------------------ boss fight

	/** What the bot does (1.4.0; replaces the "Boss fight" ON / DRY_RUN / OFF setting). */
	enum AutoMode
	{
		PREP_AND_BOSS("Prep & Boss"), PREP_ONLY("Prep Only"), BOSS_ONLY("Boss Only");

		private final String label;

		AutoMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(keyName = "autoMode", name = "Auto mode",
		description = "What the bot does: prep and fight, prep only, or fight only.",
		position = -1, section = runSection)
	default AutoMode autoMode()
	{
		return AutoMode.PREP_AND_BOSS;
	}

	enum FightInput
	{
		NATURAL, INSTANT
	}

	@ConfigItem(keyName = "bossInput", name = "Fight mouse",
		description = "NATURAL: the cursor travels to every target along natural-mouse curves (speed by distance, like a hand), prayers via the prayer tab's F-key; prep prayers are clicked the same way. INSTANT: clicks without moving the cursor (fastest, easy to spot)",
		position = 100, hidden = true)
	default FightInput bossInput()
	{
		return FightInput.NATURAL;
	}

	@ConfigItem(keyName = "bossOffense", name = "Offensive prayer",
		description = "ALWAYS: on all fight, switched with the weapon (fewest clicks; best with the natural mouse). FLICK: on only for the tick of each swing (saves prayer, 2 clicks per swing). OFF: never.",
		position = 100, hidden = true)
	default net.runelite.client.plugins.microbot.cgprep.boss.BrainConfig.Offense bossOffense()
	{
		return net.runelite.client.plugins.microbot.cgprep.boss.BrainConfig.Offense.ALWAYS;
	}

	@ConfigItem(keyName = "bossEatBelow", name = "Eat at or below (HP)",
		description = "Eat a paddlefish at or below this HP.",
		position = 1, section = bossSection)
	default int bossEatBelow()
	{
		return 40;
	}

	@ConfigItem(keyName = "bossDrinkBelow", name = "Drink at or below (prayer)",
		description = "Drink a potion at or below this many prayer points.",
		position = 2, section = bossSection)
	default int bossDrinkBelow()
	{
		return 20;
	}

	@ConfigItem(keyName = "bossClicksPerTick", name = "Clicks per tick",
		description = "INSTANT fight mouse only: most clicks sent in one game tick (prayer, move/attack, weapon, food, potion, offensive prayer). The natural mouse sends what fits in the tick",
		position = 100, hidden = true)
	default int bossClicksPerTick()
	{
		return 4;
	}

	@ConfigItem(keyName = "bossOverlay", name = "Show fight overlay",
		description = "Mark the tile the bot wants next (cyan) and the floor (red = hurting, yellow = warning)",
		position = 100, hidden = true)
	default boolean bossOverlay()
	{
		return true;
	}

	// ------------------------------------------------------------------ dev

	@ConfigItem(keyName = "dryRun", name = "Dry run (plan only)",
		description = "Observe and plan, show decisions in the overlay, but send NO game actions",
		position = 100, hidden = true)
	default boolean dryRun()
	{
		return false;
	}

	@ConfigItem(keyName = "skipDemiBosses", name = "Skip demi-bosses", description = "Prep without T3 components",
		position = 100, hidden = true)
	default boolean skipDemiBosses()
	{
		return false;
	}

	@ConfigItem(keyName = "autoLobby", name = "Auto lobby (deposit + enter)",
		description = "Enter the Gauntlet whenever the bot is started outside it, from the lobby (banks first only when the bag is nearly full). 'Loot chest + go again' already covers starting in the lobby",
		position = 100, hidden = true)
	default boolean autoLobby()
	{
		return false;
	}

	@ConfigItem(keyName = "verbose", name = "Verbose client log", description = "Also print planner decisions to the client log",
		position = 100, hidden = true)
	default boolean verbose()
	{
		return false;
	}

	@ConfigItem(keyName = "decisionLog", name = "Decision log",
		description = "Write a cg-prep-*.log file in .runelite/cg-prep each session, for bug reports. Old files are not deleted.",
		position = 4, section = hotkeySection)
	default boolean decisionLog()
	{
		return true;
	}

	@ConfigItem(keyName = "logFolder", name = "Log folder",
		description = "Where the decision logs go. Empty = .runelite/cg-prep",
		position = 100, hidden = true)
	default String logFolder()
	{
		return "";
	}
}
