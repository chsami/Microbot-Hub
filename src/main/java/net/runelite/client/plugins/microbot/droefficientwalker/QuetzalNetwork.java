package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the quetzal network, checks landing and whistle access,
 * and resolves destination widgets.
 */
final class QuetzalNetwork
{
    static final int[] WHISTLE_ITEM_IDS = {33120, 29275, 29273, 29271};
    private static final Pattern WHISTLE_CHARGES_PATTERN = Pattern.compile("Your quetzal whistle has (\\d+) charges? remaining\\.");

    private final Map<WorldPoint, Integer> unlockBitsByDestination = new LinkedHashMap<>();
    private final List<TransportCatalog.TransportEdge> transports = new ArrayList<>();

    QuetzalNetwork()
    {
    }

    static QuetzalNetwork readNetwork(Reader reader) throws IOException
    {
        QuetzalNetwork result = new QuetzalNetwork();
        Map<WorldPoint, Integer> origins = new LinkedHashMap<>();
        BufferedReader buffered = new BufferedReader(reader);

        String line;
        while ((line = buffered.readLine()) != null)
        {
            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            String[] columns = line.split("\t", -1);
            if (columns.length != 7
                || columns[0].isEmpty() == columns[1].isEmpty()
                || !"Twilight's Promise".equals(columns[3]))
            {
                throw new IOException("Invalid quetzal data row");
            }

            boolean origin = !columns[0].isEmpty();
            if (origin)
            {
                if (!"Travel Renu 13350".equals(columns[2]))
                {
                    throw new IOException("Unsupported quetzal action");
                }
            }
            else if (!columns[2].isEmpty() || columns[5].isEmpty())
            {
                throw new IOException("Unsupported quetzal action");
            }

            WorldPoint point = TransportCatalog.parseWorldPoint(columns[origin ? 0 : 1]);
            if (point.getPlane() != 0
                || point.getX() <= 0
                || point.getX() >= 16384
                || point.getY() <= 0
                || point.getY() >= 16384)
            {
                throw new IOException("Invalid quetzal location");
            }

            int unlockBit = 0;
            if (!columns[6].isEmpty())
            {
                if (!columns[6].startsWith("4182&"))
                {
                    throw new IOException("Unsupported quetzal requirement");
                }

                unlockBit = Integer.parseInt(columns[6].substring(5));
                if (unlockBit <= 0 || Integer.bitCount(unlockBit) != 1)
                {
                    throw new IOException("Invalid quetzal unlock bit");
                }
            }

            Map<WorldPoint, Integer> target = origin ? origins : result.unlockBitsByDestination;
            if (target.putIfAbsent(point, unlockBit) != null)
            {
                throw new IOException("Duplicate quetzal location");
            }
        }

        if (origins.size() < 2 || !origins.equals(result.unlockBitsByDestination))
        {
            throw new IOException("Quetzal origins and destinations do not match");
        }

        for (WorldPoint origin : origins.keySet())
        {
            for (WorldPoint destination : result.unlockBitsByDestination.keySet())
            {
                if (origin.equals(destination))
                {
                    continue;
                }

                result.transports.add(TransportCatalog.createNpcTransport(
                    origin,
                    destination,
                    "Travel",
                    "Renu",
                    new int[]{13350, 13351, 13352, 13353, 13354}));
            }
        }

        return result;
    }

    List<TransportCatalog.TransportEdge> getTransports()
    {
        return Collections.unmodifiableList(this.transports);
    }

    List<TeleportProviders.TeleportProvider> createWhistleProviders(Client client, Set<Integer> excludedSlots)
    {
        List<TeleportProviders.TeleportProvider> teleports = new ArrayList<>();
        for (WorldPoint destination : this.unlockBitsByDestination.keySet())
        {
            teleports.add(new TeleportProviders.TeleportProvider(
                "Quetzal whistle " + destination,
                destination,
                WHISTLE_ITEM_IDS,
                "Signal",
                () -> canUseWhistleProvider(client, destination, excludedSlots),
                false,
                () -> isWhistleDestinationAvailableForProvider(client, destination)));
        }
        return teleports;
    }

    boolean isWhistleDestinationAvailable(Client client, WorldPoint destination)
    {
        return isWhistleDestinationAvailable(
            client.getWorldType().contains(WorldType.MEMBERS),
            client.getVarpValue(4182),
            client.getVarbitValue(19681),
            destination);
    }

    boolean isWhistleDestinationAvailable(boolean members, int unlockFlags, int whistleState, WorldPoint destination)
    {
        return members
            && unlockFlags >= 0
            && whistleState == 0
            && isLandingSiteUnlocked(destination, unlockFlags);
    }

    static int findWhistleInventorySlot(Client client, Set<Integer> excludedSlots)
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        return inventory == null ? -1 : findWhistleInventorySlot(slot -> readInventoryItemId(inventory, slot), excludedSlots);
    }

    static int findWhistleInventorySlot(IntUnaryOperator itemAtSlot, Set<Integer> excludedSlots)
    {
        for (int itemId : WHISTLE_ITEM_IDS)
        {
            for (int slot = 0; slot < 28; slot++)
            {
                if (!excludedSlots.contains(slot) && itemAtSlot.applyAsInt(slot) == itemId)
                {
                    return slot;
                }
            }
        }
        return -1;
    }

    static boolean isWhistleRechargeMessage(String message)
    {
        return "Soar Leader Pitri|There you go. Some whistle charges for you!".equals(message)
            || "Soar Leader Pitri|Looks like the birds are all full for now. Make them work a bit before feeding them again!".equals(message);
    }

    static int parseWhistleCharges(String message)
    {
        Matcher matcher = WHISTLE_CHARGES_PATTERN.matcher(message);
        if (!matcher.matches())
        {
            return -1;
        }

        try
        {
            return Integer.parseInt(matcher.group(1));
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    static boolean isQuetzalTransport(TransportCatalog.TransportEdge transport)
    {
        return transport != null
            && transport.hasAlternativeIds()
            && "Travel".equals(transport.action)
            && "Renu".equals(transport.target);
    }

    boolean isTransportAvailable(Client client, TransportCatalog.TransportEdge transport)
    {
        return isTransportAvailable(
            client.getWorldType().contains(WorldType.MEMBERS),
            client.getVarbitValue(9649),
            client.getVarpValue(4182),
            transport);
    }

    boolean isTransportAvailable(boolean members, int questProgress, int unlockFlags, TransportCatalog.TransportEdge transport)
    {
        return members
            && questProgress >= 38
            && unlockFlags >= 0
            && isQuetzalTransport(transport)
            && isLandingSiteUnlocked(transport.origin, unlockFlags)
            && isLandingSiteUnlocked(transport.destination, unlockFlags);
    }

    private boolean isLandingSiteUnlocked(WorldPoint destination, int unlockFlags)
    {
        Integer requiredBit = this.unlockBitsByDestination.get(destination);
        return requiredBit != null && (unlockFlags & requiredBit) == requiredBit;
    }

    static boolean isMapOpen(Client client)
    {
        return isMapOpen(client, 874);
    }

    static boolean isMapOpen(Client client, int groupId)
    {
        Widget widget = client.getWidget(groupId, 12);
        return widget != null && !widget.isHidden();
    }

    static boolean isFadeOverlayVisible(Client client)
    {
        Widget widget = client.getWidget(174, 0);
        return widget != null && !widget.isHidden();
    }

    Widget findDestinationWidget(Client client, TransportCatalog.TransportEdge transport)
    {
        return isTransportAvailable(client, transport)
            ? findDestinationWidget(client, transport.destination, 874)
            : null;
    }

    Widget findDestinationWidget(Client client, WorldPoint destination, int groupId)
    {
        if (!isLandingSiteUnlocked(destination, client.getVarpValue(4182)) || !isMapOpen(client, groupId))
        {
            return null;
        }

        String action = findDestinationAction(client, destination);
        Widget[] children = client.getWidget(groupId, 12).getDynamicChildren();
        if (action == null || children == null)
        {
            return null;
        }

        Widget match = null;
        for (Widget child : children)
        {
            if (FairyRingNetwork.isFirstWidgetActionEnabled(client, child)
                && child.getIndex() >= 0
                && action.equals(child.getActions()[0]))
            {
                if (match != null)
                {
                    return null;
                }
                match = child;
            }
        }
        return match;
    }

    private static String findDestinationAction(Client client, WorldPoint destination)
    {
        List<Integer> rows = client.getDBTableRows(64);
        if (rows == null)
        {
            return null;
        }

        String result = null;
        for (Integer row : rows)
        {
            Object[] location = client.getDBTableField(row, 2, 0);
            if (location == null
                || location.length != 1
                || !(location[0] instanceof Integer)
                || ((Integer) location[0]).intValue() < 0
                || !destination.equals(WorldPoint.fromCoord(((Integer) location[0]).intValue())))
            {
                continue;
            }

            Object[] name = client.getDBTableField(row, 1, 0);
            if (result != null
                || name == null
                || name.length != 1
                || !(name[0] instanceof String))
            {
                return null;
            }

            result = (String) name[0];
        }
        return result;
    }

    private static int readInventoryItemId(ItemContainer inventory, int slot)
    {
        Item item = inventory.getItem(slot);
        return item == null ? -1 : item.getId();
    }

    private boolean isWhistleDestinationAvailableForProvider(Client client, WorldPoint destination)
    {
        return isWhistleDestinationAvailable(client, destination);
    }

    private boolean canUseWhistleProvider(Client client, WorldPoint destination, Set<Integer> excludedSlots)
    {
        return isWhistleDestinationAvailable(client, destination) && findWhistleInventorySlot(client, excludedSlots) >= 0;
    }
}
