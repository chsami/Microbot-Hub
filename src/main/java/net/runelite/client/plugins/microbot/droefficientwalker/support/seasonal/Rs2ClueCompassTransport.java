package net.runelite.client.plugins.microbot.droefficientwalker.support.seasonal;

import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;

import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Executes League Clue compass rows exposed as inventory actions or submenu actions. */
public final class Rs2ClueCompassTransport
{
	static final int ITEM_ID = 30363;
	private static final String PREFIX = "clue compass:";

	private Rs2ClueCompassTransport()
	{
	}

	public static boolean matches(Transport transport)
	{
		return transport != null
			&& transport.getType() == TransportType.SEASONAL_TRANSPORT
			&& transport.getOrigin() == null
			&& transport.getDestination() != null
			&& destination(transport.getDisplayInfo()) != null;
	}

	public static boolean tryUse(Transport transport)
	{
		if (!matches(transport))
		{
			return false;
		}
		Rs2ItemModel compass = Rs2Inventory.get(ITEM_ID);
		if (compass == null)
		{
			return false;
		}
		String destination = destination(transport.getDisplayInfo());
		String action = compass.getAction(destination);
		Map.Entry<String, Integer> submenu = compass.getIndexOfSubAction(destination);
		if (action == null && submenu != null && submenu.getKey() != null)
		{
			action = destination;
		}
		boolean issued = action != null && Rs2Inventory.interact(compass, action);
		if (issued)
		{
			Rs2LeaguesTransport.recordTransportAttempt(transport);
		}
		return issued;
	}

	public static boolean isStagedRoute(Transport transport)
	{
		return matches(transport) && !transport.isConsumable() && transport.getCurrencyAmount() == 0
			&& Set.of(Set.of(ITEM_ID)).equals(transport.getItemIdRequirements());
	}

	static String destination(String displayInfo)
	{
		if (displayInfo == null)
		{
			return null;
		}
		String normalized = displayInfo.trim().toLowerCase(Locale.ROOT);
		if (!normalized.startsWith(PREFIX))
		{
			return null;
		}
		String destination = displayInfo.substring(displayInfo.indexOf(':') + 1).trim()
			.toLowerCase(Locale.ROOT);
		return destination.isEmpty() ? null : destination;
	}
}
