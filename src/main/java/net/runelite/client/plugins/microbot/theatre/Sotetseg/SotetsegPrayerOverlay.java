package net.runelite.client.plugins.microbot.theatre.Sotetseg;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.theatre.TheatreConfig;
import net.runelite.client.plugins.microbot.theatre.prayer.TheatrePrayerOverlay;
import net.runelite.client.plugins.microbot.theatre.prayer.TheatreUpcomingAttack;

import javax.inject.Inject;
import java.util.Queue;

public class SotetsegPrayerOverlay extends TheatrePrayerOverlay
{
	private final Sotetseg plugin;

	@Inject
	protected SotetsegPrayerOverlay(Client client, TheatreConfig config, Sotetseg plugin)
	{
		super(client, config);
		this.plugin = plugin;
	}

	@Override
	protected Queue<TheatreUpcomingAttack> getAttackQueue()
	{
		return plugin.getUpcomingAttackQueue();
	}

	@Override
	protected long getLastTick()
	{
		return plugin.getLastTick();
	}

	@Override
	protected boolean isEnabled()
	{
		return getConfig().sotetsegPrayerHelper();
	}
}
