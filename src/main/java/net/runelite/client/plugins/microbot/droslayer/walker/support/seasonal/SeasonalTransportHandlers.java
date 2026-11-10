package net.runelite.client.plugins.microbot.droslayer.walker.support.seasonal;

import net.runelite.client.plugins.microbot.util.leaguetransport.*;

import net.runelite.client.plugins.microbot.shortestpath.Transport;

import java.util.List;

/**
 * Built-in {@link SeasonalTransportHandler} instances and default ordering.
 */
public final class SeasonalTransportHandlers
{
    private static final java.util.regex.Pattern AREA_PREFIX = java.util.regex.Pattern.compile("(?i)leagues\\s*area:\\s*");
	private SeasonalTransportHandlers()
	{
	}

	public static final SeasonalTransportHandler LEAGUES_AREA = new SeasonalTransportHandler()
	{
		@Override
		public boolean matches(Transport transport)
		{
			return Rs2LeaguesTransport.matchesLeaguesAreaTransportPrefix(transport);
		}

		@Override
		public boolean tryUse(Transport transport)
		{
			return Rs2LeaguesTransport.tryHandleLeaguesAreaTransport(transport);
		}
	};

	public static final SeasonalTransportHandler MAP_OF_ALACRITY = new SeasonalTransportHandler()
	{
		@Override
		public boolean matches(Transport transport)
		{
			return Rs2MapOfAlacrityTransport.matches(transport);
		}

		@Override
		public boolean tryUse(Transport transport)
		{
			return Rs2MapOfAlacrityTransport.tryUse(transport);
		}
	};

	public static final SeasonalTransportHandler CLUE_COMPASS = new SeasonalTransportHandler()
	{
		@Override
		public boolean matches(Transport transport)
		{
			return Rs2ClueCompassTransport.matches(transport);
		}

		@Override
		public boolean tryUse(Transport transport)
		{
			return Rs2ClueCompassTransport.tryUse(transport);
		}
	};

	/** Cheap pathfinder gate: every admitted seasonal edge must have a corresponding executor. */
	public static boolean canHandle(Transport transport)
	{
		return (transport != null && transport.getDisplayInfo() != null && AREA_PREFIX.matcher(net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer.normalizeAsciiColons(transport.getDisplayInfo())).lookingAt())
			|| Rs2MapOfAlacrityTransport.matches(transport)
			|| Rs2ClueCompassTransport.matches(transport);
	}

	/** Runtime availability layered over the static executor-shape check. */
	public static boolean isAvailable(Transport transport)
	{
		return canHandle(transport)
			&& (!Rs2MapOfAlacrityTransport.matches(transport)
				|| Rs2MapOfAlacrityTransport.isAvailable(transport));
	}

	public static List<SeasonalTransportHandler> defaultHandlerList()
	{
		return List.of(LEAGUES_AREA, MAP_OF_ALACRITY, CLUE_COMPASS);
	}
}
