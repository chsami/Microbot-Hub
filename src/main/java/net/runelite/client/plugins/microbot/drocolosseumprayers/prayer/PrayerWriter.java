package net.runelite.client.plugins.microbot.drocolosseumprayers.prayer;

import java.util.function.LongSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Sends the ledger's work as prayer-widget menu actions on the client thread. GameTick gives the ledger its server
 * evidence; every ClientTick plans and sends at most what the ledger allows.
 */
public final class PrayerWriter
{
	private final Client client;
	private final LongSupplier nanos;
	private final TickPrayerLedger ledger = new TickPrayerLedger();
	private Guard wanted = Guard.NONE;
	private boolean allowPair;
	private volatile String status = "Waiting for a tick";
	private long pairs;
	private long switches;

	public PrayerWriter(Client client)
	{
		this(client, System::nanoTime);
	}

	PrayerWriter(Client client, LongSupplier nanos)
	{
		this.client = client;
		this.nanos = nanos;
	}

	public void reset()
	{
		ledger.reset();
		wanted = Guard.NONE;
		allowPair = false;
		status = "Waiting for a tick";
	}

	public void gameTick(int tick)
	{
		ledger.beginTick(tick, nanos.getAsLong(), observed());
	}

	/** The overhead wanted from the next server tick, and whether a held overhead may be kept up with pairs. */
	public void want(Guard guard, boolean pairs)
	{
		wanted = guard;
		allowPair = pairs;
	}

	public Guard wanted()
	{
		return wanted;
	}

	public void clientTick(boolean allowed)
	{
		if (!allowed)
		{
			ledger.failed();
			status = "Paused";
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}
		if (client.getBoostedSkillLevel(Skill.PRAYER) <= 0)
		{
			status = "Out of prayer points";
			return;
		}
		Guard observed = observed();
		long now = nanos.getAsLong();
		for (TickPrayerLedger.Command command : ledger.plan(now, observed, wanted, allowPair))
		{
			if (!send(command))
			{
				ledger.failed();
				status = "Could not use " + command.guard().prayer().getName();
				return;
			}
			ledger.sent(command);
			if (command.type() == TickPrayerLedger.Type.PAIR)
			{
				pairs++;
			}
			else
			{
				switches++;
			}
		}
		status = !ledger.synchronised() ? "Syncing to the tick" :
			wanted == Guard.NONE ? "Nothing due: overhead off" : "1-tick " + wanted.label();
	}

	public String status()
	{
		return status;
	}

	public long pairs()
	{
		return pairs;
	}

	public long switches()
	{
		return switches;
	}

	public int misses()
	{
		return ledger.misses();
	}

	/** The overhead the varbits show. */
	public Guard observed()
	{
		for (Guard guard : Guard.values())
		{
			if (guard != Guard.NONE && client.getVarbitValue(guard.prayer().getVarbit()) == 1)
			{
				return guard;
			}
		}
		return Guard.NONE;
	}

	private boolean send(TickPrayerLedger.Command command)
	{
		Rs2PrayerEnum prayer = command.guard().prayer();
		if (client.getWidget(prayer.getIndex()) == null)
		{
			return false;
		}
		boolean active = client.getVarbitValue(prayer.getVarbit()) == 1;
		switch (command.type())
		{
			case ON:
				if (active)
				{
					return true;
				}
				if (client.getRealSkillLevel(Skill.PRAYER) < prayer.getLevel())
				{
					return false;
				}
				toggle(prayer, true);
				return true;
			case OFF:
				if (active)
				{
					toggle(prayer, false);
				}
				return true;
			case PAIR:
				if (!active)
				{
					return false;
				}
				// Both halves go out in this one invocation: once the OFF is sent the ON must follow, with no wait
				// or varbit check between them.
				toggle(prayer, false);
				toggle(prayer, true);
				return true;
			default:
				return false;
		}
	}

	private void toggle(Rs2PrayerEnum prayer, boolean on)
	{
		client.menuAction(-1, prayer.getIndex(), MenuAction.CC_OP, 1, -1, on ? "Activate" : "Deactivate", prayer.getName());
	}
}
