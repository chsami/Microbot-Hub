package net.runelite.client.plugins.microbot.droslayer.walker.support.seasonal;

import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilNotNull;

/** Executes the League Map of Alacrity's two-stage region/destination picker. */
@Slf4j
public final class Rs2MapOfAlacrityTransport
{
	static final int ITEM_ID = 33233;
	static final int WIDGET_GROUP = 187;
	static final int LIST_CHILD = 3;
	private static final String PREFIX = "map of alacrity:";
	private static final String LOCKED_MARKUP = "<str>";
	private static final Pattern HOTKEY_PATTERN =
		Pattern.compile("^\\s*(?:\\[([0-9A-Za-z])\\]|([0-9A-Za-z])\\s*[:.])");
	private static final Pattern MARKUP_PATTERN = Pattern.compile("<[^>]+>");
	private static final Pattern PUNCT_PATTERN = Pattern.compile("[^a-zA-Z0-9 ]");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final Set<Integer> unavailableDestinations = ConcurrentHashMap.newKeySet();
	private static final Set<String> lockedRegions = ConcurrentHashMap.newKeySet();

	private Rs2MapOfAlacrityTransport()
	{
	}

	public static boolean matches(Transport transport)
	{
		return transport != null
			&& transport.getType() == TransportType.SEASONAL_TRANSPORT
			&& transport.getOrigin() == null
			&& transport.getDestination() != null
			&& transport.getDisplayInfo() != null
			&& transport.getDisplayInfo().trim().toLowerCase(Locale.ROOT).startsWith(PREFIX)
			&& parseDestination(transport.getDisplayInfo()) != null;
	}

	public static boolean isAvailable(Transport transport)
	{
		if (!matches(transport))
		{
			return false;
		}
		Destination destination = parseDestination(transport.getDisplayInfo());
		return destination != null
			&& !unavailableDestinations.contains(WorldPointUtil.packWorldPoint(transport.getDestination()))
			&& !lockedRegions.contains(normalize(destination.region));
	}

	public static boolean isStagedRoute(Transport transport)
	{
		return matches(transport) && !transport.isConsumable() && transport.getCurrencyAmount() == 0
			&& transport.getItemIdRequirements().equals(Set.of(Set.of(ITEM_ID)));
	}

	static void markUnavailable(Transport transport, boolean regionLocked)
	{
		Destination destination = parseDestination(transport.getDisplayInfo());
		boolean changed = unavailableDestinations.add(WorldPointUtil.packWorldPoint(transport.getDestination()));
		if (regionLocked && destination != null)
		{
			changed |= lockedRegions.add(normalize(destination.region));
		}
		if (changed) Rs2LeaguesTransport.invalidateContext();
	}

	public static boolean tryUse(Transport transport)
	{
		if (!isAvailable(transport))
		{
			return false;
		}
		int packedDestination = WorldPointUtil.packWorldPoint(transport.getDestination());
		Destination destination = parseDestination(transport.getDisplayInfo());
		if (destination == null)
		{
			return false;
		}

		Rs2ItemModel map = Rs2Inventory.get(ITEM_ID);
		if (map == null)
		{
			return false;
		}
		String action = map.getAction("Read");
		if (action == null)
		{
			action = map.getActionFromList(Arrays.asList("Read", "Open", "Teleport", "Invoke"));
		}
		if (action == null || !Rs2Inventory.interact(map, action)
			|| !sleepUntil(() -> Rs2Widget.isWidgetVisible(WIDGET_GROUP, LIST_CHILD), 3000))
		{
			return false;
		}

		Widget regionRoot = Rs2Widget.getWidget(WIDGET_GROUP, LIST_CHILD);
		Widget regionRow = findWidget(regionRoot, destination.region);
		String regionText = widgetText(regionRow);
		if (regionRow == null || isLocked(regionText))
		{
			if (isLocked(regionText))
			{
				lockedRegions.add(normalize(destination.region));
			}
			unavailableDestinations.add(packedDestination);
			Rs2LeaguesTransport.invalidateContext();
			return false;
		}
		if (!select(regionRoot, regionRow, regionText))
		{
			return false;
		}

		Widget destinationRow = sleepUntilNotNull(() -> {
			Widget root = Rs2Widget.getWidget(WIDGET_GROUP, LIST_CHILD);
			return findWidget(root, destination.name);
		}, 3000);
		String destinationText = widgetText(destinationRow);
		if (destinationRow == null || isLocked(destinationText))
		{
			unavailableDestinations.add(packedDestination);
			Rs2LeaguesTransport.invalidateContext();
			return false;
		}
		Widget destinationRoot = Rs2Widget.getWidget(WIDGET_GROUP, LIST_CHILD);
		boolean issued = select(destinationRoot, destinationRow, destinationText);
		if (issued)
		{
			Rs2LeaguesTransport.recordTransportAttempt(transport);
		}
		return issued;
	}

	static Destination parseDestination(String displayInfo)
	{
		if (displayInfo == null)
		{
			return null;
		}
		int colon = displayInfo.indexOf(':');
		String value = colon >= 0 ? displayInfo.substring(colon + 1).trim() : "";
		int separator = value.indexOf(" - ");
		if (separator <= 0 || separator + 3 >= value.length())
		{
			return null;
		}
		String region = value.substring(0, separator).trim();
		String name = value.substring(separator + 3).trim();
		return region.isEmpty() || name.isEmpty() ? null : new Destination(region, name);
	}

	static String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		String withoutMarkup = MARKUP_PATTERN.matcher(value).replaceAll(" ");
		String withoutPunctuation = PUNCT_PATTERN.matcher(withoutMarkup).replaceAll(" ");
		return WHITESPACE_PATTERN.matcher(withoutPunctuation.toLowerCase(Locale.ROOT))
			.replaceAll(" ").trim();
	}

	static boolean normalizedTextContainsAllTokens(String widgetText, String requestedText)
	{
		String haystack = normalize(widgetText);
		String needle = normalize(requestedText);
		if (haystack.isEmpty() || needle.isEmpty())
		{
			return false;
		}
		for (String token : needle.split(" "))
		{
			if (!haystack.contains(token))
			{
				return false;
			}
		}
		return true;
	}

	static Character extractHotkey(String rawText)
	{
		Matcher matcher = HOTKEY_PATTERN.matcher(
			MARKUP_PATTERN.matcher(rawText == null ? "" : rawText).replaceAll("").trim());
		if (!matcher.find())
		{
			return null;
		}
		String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		char key = value.charAt(0);
		return Character.isLetter(key) ? Character.toUpperCase(key) : key;
	}

	static Character indexToHotkey(int index)
	{
		if (index < 0)
		{
			return null;
		}
		if (index < 9)
		{
			return (char) ('1' + index);
		}
		int letter = index - 9;
		return letter < 26 ? (char) ('A' + letter) : null;
	}

	private static Widget findWidget(Widget root, String text)
	{
		if (root == null)
		{
			return null;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			for (Widget widget : collectChildren(root))
			{
				if (normalizedTextContainsAllTokens(widget.getText(), text))
				{
					return widget;
				}
			}
			return null;
		}).orElse(null);
	}

	private static boolean select(Widget root, Widget row, String text)
	{
		Character hotkey = extractHotkey(text);
		if (hotkey == null)
		{
			hotkey = hotkeyByIndex(root, row);
		}
		if (hotkey != null)
		{
			Rs2Keyboard.keyPress(hotkey);
			return true;
		}
		return Rs2Widget.clickWidget(row);
	}

	private static Character hotkeyByIndex(Widget root, Widget selected)
	{
		if (root == null || selected == null)
		{
			return null;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			int index = 0;
			for (Widget sibling : collectChildren(root))
			{
				String text = sibling.getText();
				if (text == null || text.isEmpty() || isLocked(text))
				{
					continue;
				}
				if (sibling == selected)
				{
					return indexToHotkey(index);
				}
				index++;
			}
			return null;
		}).orElse(null);
	}

	private static List<Widget> collectChildren(Widget root)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			List<Widget> result = new ArrayList<>();
			addChildren(result, root.getDynamicChildren());
			addChildren(result, root.getNestedChildren());
			addChildren(result, root.getStaticChildren());
			return result;
		}).orElseGet(ArrayList::new);
	}

	private static void addChildren(List<Widget> result, Widget[] children)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null)
				{
					result.add(child);
				}
			}
		}
	}

	private static String widgetText(Widget widget)
	{
		return widget == null ? "" : Microbot.getClientThread()
			.runOnClientThreadOptional(widget::getText).orElse("");
	}

	private static boolean isLocked(String text)
	{
		return text != null && text.toLowerCase(Locale.ROOT).contains(LOCKED_MARKUP);
	}

	static final class Destination
	{
		final String region;
		final String name;

		Destination(String region, String name)
		{
			this.region = region;
			this.name = name;
		}
	}
}
