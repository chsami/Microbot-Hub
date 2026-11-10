package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

/**
 * Quetzal network, whistle admission, and destination-widget lookup.
 * The legacy regex decoder remains isolated to preserve default-charset behavior.
 */
final class QuetzalNetwork
{
    static final int[] WHISTLE_ITEM_IDS;
    private static final Pattern WHISTLE_CHARGES_PATTERN;
    private static final byte[] LEGACY_XOR_KEY = {
        114, 28, 115, 49, 94, 39, 20, 123, 30, 15, 16, 105, 63, 100, 62, 109,
        97, 7, 27, 36, 16, 95, 47, 77, 88, 125, 53, 10, 88, 45, 11, 27,
        4, 50, 26, 108, 30, 11, 10, 54, 123, 50, 51, 105, 89, 95, 14, 88,
        94, 14, 64, 43, 46, 83
    };

    static
    {
        WHISTLE_ITEM_IDS = new int[]{33120, 29275, 29273, 29271};
        WHISTLE_CHARGES_PATTERN = Pattern.compile(
            resolveLegacyText("Your quetzal whistle has (\\d+) charges? remaining\\.", 1030360235, -1662623454));
    }

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
        return Collections.unmodifiableList(transports);
    }

    List<TeleportProviders.TeleportProvider> createWhistleProviders(Client client, Set<Integer> excludedSlots)
    {
        List<TeleportProviders.TeleportProvider> teleports = new ArrayList<>();
        for (WorldPoint destination : unlockBitsByDestination.keySet())
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
        Integer requiredBit = unlockBitsByDestination.get(destination);
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

    /** Recreate the original BOM-bearing outer payload only for unusual default charsets. */
    private static String resolveLegacyText(String text, int outerSeed, int textSeed)
    {
        byte[] seedDigits = Integer.toString(outerSeed).getBytes();
        byte[] originalDigits = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
        if (Arrays.equals(seedDigits, originalDigits))
        {
            return text.intern();
        }
        byte[] bytes = encodeLegacyPayload(text, textSeed).getBytes(StandardCharsets.UTF_16);
        for (int index = 0; index < bytes.length; index++)
        {
            bytes[index] = (byte) (bytes[index] ^ originalDigits[index % originalDigits.length]);
            bytes[index] = (byte) (bytes[index] ^ LEGACY_XOR_KEY[index % LEGACY_XOR_KEY.length]);
        }
        return decodeText(decodeLegacyBytes(bytes, outerSeed), textSeed);
    }

    /** Inverse of decodeText, using the same overflowing int LCG and UTF-16 code units. */
    private static String encodeLegacyPayload(String text, int seed)
    {
        byte[] bytes = new byte[text.length() * 2];
        for (int index = 0; index < text.length(); index++)
        {
            seed = seed * 1664525 + 1013904223;
            int value = text.charAt(index) ^ seed >>> 16;
            bytes[index * 2] = (byte) (value >>> 8);
            bytes[index * 2 + 1] = (byte) value;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Preserve the old in-place byte mutation and default-charset seed bytes. */
    private static String decodeLegacyBytes(byte[] bytes, int seed)
    {
        byte[] seedBytes = Integer.toString(seed).getBytes();
        for (int index = 0; index < bytes.length; index++)
        {
            bytes[index] = (byte) (bytes[index] ^ seedBytes[index % seedBytes.length]);
            bytes[index] = (byte) (bytes[index] ^ LEGACY_XOR_KEY[index % LEGACY_XOR_KEY.length]);
        }
        return new String(bytes, StandardCharsets.UTF_16);
    }

    /** Preserve the original signed-int LCG, UTF-16 units, and String interning. */
    private static String decodeText(String encoded, int seed)
    {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        char[] result = new char[bytes.length >>> 1];
        for (int index = 0; index < result.length; index++)
        {
            seed = seed * 1664525 + 1013904223;
            int value = (bytes[index << 1] & 255) << 8 | bytes[(index << 1) + 1] & 255;
            result[index] = (char) (value ^ seed >>> 16);
        }
        return new String(result).intern();
    }
}
