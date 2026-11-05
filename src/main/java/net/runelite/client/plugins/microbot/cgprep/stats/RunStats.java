package net.runelite.client.plugins.microbot.cgprep.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Totals for this session (reset every time the plugin is switched on, 1.2.0): runs, deaths
 * during prep, deaths in the boss fight, and the value of the reward chest loot at GE and
 * high-alch prices.
 *
 * The Gauntlet's Reward Chest has no loot screen: opening it puts the loot in the inventory. So
 * whatever is added to the inventory within a few ticks of opening it (the "Open" click on the
 * Reward Chest, or the reward-available varbit going to 0) is counted as loot, except in the first
 * ticks after leaving the Gauntlet, when it hands back the items it held during the run.
 */
public final class RunStats
{
	static final String GROUP = "cgprep";
	static final String K_RUNS = "statsRuns";
	static final String K_SETUP_DEATHS = "statsSetupDeaths";
	static final String K_BOSS_DEATHS = "statsBossDeaths";
	static final String K_GE = "statsLootGe";
	static final String K_HA = "statsLootHa";
	static final String DEATH_MESSAGE = "Oh dear, you are dead!";
	/** Ticks after the chest is opened in which new inventory items count as loot. */
	static final int LOOT_WINDOW_TICKS = 10;

	private final ConfigManager config;
	private final ItemManager items;
	private final BiConsumer<String, String> log;

	private volatile long runs;
	private volatile long setupDeaths;
	private volatile long bossDeaths;
	private volatile long lootGe;
	private volatile long lootHa;

	// client thread only
	private Map<Integer, Integer> lastInv;
	private Map<Integer, Integer> lastAdded = new HashMap<>();
	private int lastAddedTick = -100;
	private boolean lastAddedCounted = true;
	private int windowUntil = -1;
	/** Tick we last left the Gauntlet: the held items come back then and are not loot. */
	private int leftTick = -100;
	private boolean diedThisRun;

	public RunStats(ConfigManager config, ItemManager items, BiConsumer<String, String> log)
	{
		this.config = config;
		this.items = items;
		this.log = log;
		// Totals are per session (user, 1.2.0): a new RunStats is made every time the plugin is
		// switched on, starting from 0. Totals stored by earlier versions are cleared.
		for (String k : new String[]{K_RUNS, K_SETUP_DEATHS, K_BOSS_DEATHS, K_GE, K_HA})
		{
			if (config.getConfiguration(GROUP, k) != null)
			{
				config.unsetConfiguration(GROUP, k);
			}
		}
	}

	// ------------------------------------------------------------------ events

	/** A run ended (left the Gauntlet). Script thread. */
	public void runEnded()
	{
		runs++;
		diedThisRun = false;
	}

	/** Game message. Client thread; inGauntlet / bossStarted read from the client's varbits. */
	public void onChat(String message, boolean inGauntlet, boolean bossStarted)
	{
		if (message == null || !message.contains(DEATH_MESSAGE) || !inGauntlet || diedThisRun)
		{
			return;
		}
		diedThisRun = true;
		if (bossStarted)
		{
			bossDeaths++;
			log.accept("STATS", "died in the boss fight (boss deaths " + bossDeaths + ")");
		}
		else
		{
			setupDeaths++;
			log.accept("STATS", "died during prep (setup deaths " + setupDeaths + ")");
		}
	}

	/** Left the Gauntlet (PLAYER_IN_GAUNTLET -> 0): the items it held come back now. Client thread. */
	public void onLeftGauntlet(int tick)
	{
		leftTick = tick;
		windowUntil = -1;
	}

	/** The Reward Chest was opened (its "Open" click, or the reward varbit cleared). Client thread. */
	public void onChestOpened(Client client, int tick)
	{
		windowUntil = tick + LOOT_WINDOW_TICKS;
		// The items may have arrived in the same tick, just before this event.
		if (!lastAddedCounted && lastAddedTick >= tick - 1 && lastAddedTick > leftTick + 3)
		{
			count(client, lastAdded);
			lastAddedCounted = true;
		}
	}

	/** Inventory changed. Client thread. */
	public void onInventory(Client client, Item[] inv, int tick)
	{
		Map<Integer, Integer> now = new HashMap<>();
		for (Item it : inv)
		{
			if (it.getId() > 0 && it.getQuantity() > 0)
			{
				now.merge(it.getId(), it.getQuantity(), Integer::sum);
			}
		}
		if (lastInv != null)
		{
			Map<Integer, Integer> added = new HashMap<>();
			for (Map.Entry<Integer, Integer> e : now.entrySet())
			{
				int d = e.getValue() - lastInv.getOrDefault(e.getKey(), 0);
				if (d > 0)
				{
					added.put(e.getKey(), d);
				}
			}
			if (!added.isEmpty() && tick <= leftTick + 3)
			{
				added.clear(); // our own items handed back by the Gauntlet
			}
			if (!added.isEmpty())
			{
				if (tick <= windowUntil)
				{
					count(client, added);
					lastAddedCounted = true;
				}
				else
				{
					lastAdded = added;
					lastAddedTick = tick;
					lastAddedCounted = false;
				}
			}
		}
		lastInv = now;
	}

	private void count(Client client, Map<Integer, Integer> added)
	{
		long ge = 0;
		long ha = 0;
		StringBuilder what = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : added.entrySet())
		{
			int id = items.canonicalize(e.getKey()); // noted -> unnoted price
			long qty = e.getValue();
			if (id == ItemID.COINS)
			{
				ge += qty;
				ha += qty;
			}
			else
			{
				ge += items.getItemPrice(id) * qty;
				ItemComposition c = client.getItemDefinition(id);
				ha += c == null ? 0 : (long) c.getHaPrice() * qty;
			}
			ItemComposition c = client.getItemDefinition(e.getKey());
			what.append(qty).append(" x ").append(c == null ? String.valueOf(e.getKey()) : c.getName()).append(", ");
		}
		lootGe += ge;
		lootHa += ha;
		log.accept("LOOT", "GE " + ge + " | HA " + ha + " | " + what.toString().replaceAll(", $", "")
			+ " | totals GE " + lootGe + " HA " + lootHa);
	}

	// ------------------------------------------------------------------ reads (any thread)

	public long runs()
	{
		return runs;
	}

	public long setupDeaths()
	{
		return setupDeaths;
	}

	public long bossDeaths()
	{
		return bossDeaths;
	}

	public String gp()
	{
		return "GE: " + QuantityFormatter.quantityToStackSize(lootGe) + " | HA: " + QuantityFormatter.quantityToStackSize(lootHa);
	}
}
