package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfigNode;
import net.runelite.client.plugins.microbot.droslayer.walker.FairyRingSelection;
import net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Fairy-ring data, admission and dial selection.
 * Legacy text decoding is isolated below to preserve default-charset behavior;
 * all route and interaction decisions are expressed directly.
 */
final class FairyRingNetwork
{
    private static final String[] DIAL_LETTERS;
    private static final int[] DIAL_VARBITS;
    private static final Set<String> VARBIT_4897_CODES;
    private static final Set<String> VARBIT_9650_CODES;
    static final int[] STAFF_ITEM_IDS;

    static
    {
        // Preserve the mutable dial alphabets and their historical order.
        DIAL_LETTERS = new String[]{
            resolveLegacyText("ADCB", 327371828, -1662623398),
            resolveLegacyText("ILKJ", 327371828, 420326193),
            resolveLegacyText("PSRQ", 327371828, -1758333556)
        };
        DIAL_VARBITS = new int[]{3985, 3986, 3987};
        VARBIT_4897_CODES = Set.of(
            resolveLegacyText("AKR", 327371828, 220544539),
            resolveLegacyText("BLS", 327371828, -1958131466),
            resolveLegacyText("CIR", 327371828, 24941901),
            resolveLegacyText("CIS", 327371828, -1084170280),
            resolveLegacyText("DJR", 327371828, 898951607)
        );
        VARBIT_9650_CODES = Set.of(
            resolveLegacyText("AIS", 327371828, -1279774206),
            resolveLegacyText("AJP", 327371828, 699169945),
            resolveLegacyText("CKQ", 327371828, -1479308940)
        );
        STAFF_ITEM_IDS = new int[]{772, 9084};
    }

    // Haunted Woods (ALQ): hostile area; never route into or out of it.
    private static final WorldPoint BLOCKED_RING = new WorldPoint(3597, 3495, 0);

    private final Map<WorldPoint, String> codesByDestination = new LinkedHashMap<>();
    private final List<TransportCatalog.TransportEdge> transports = new ArrayList<>();

    private int lastDialState = -1;
    private int lastDialProgressTick = -1;
    private int lastDialClickTick = -1;
    private int dialClickAttempts;

    FairyRingNetwork()
    {
    }

    static FairyRingNetwork readNetwork(Reader reader) throws IOException
    {
        FairyRingNetwork result = new FairyRingNetwork();
        List<String[]> origins = new ArrayList<>();
        Map<WorldPoint, String[]> destinations = new LinkedHashMap<>();

        BufferedReader buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null)
        {
            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            String[] row = line.split("\t", -1);
            if (row.length != 8)
            {
                throw new IOException("Invalid fairy ring data row");
            }

            if (!row[0].isEmpty())
            {
                if (row[2].startsWith("Configure Fairy ring ") && !row[2].endsWith(" 29228"))
                {
                    origins.add(row);
                }
                continue;
            }

            String code = row[7].replace(" ", "");
            if (!isValidCode(code) || "DIQ".equals(code))
            {
                continue;
            }

            WorldPoint destination = TransportCatalog.parseWorldPoint(row[1]);
            result.codesByDestination.put(destination, code);
            destinations.put(destination, row);
        }

        Set<WorldPoint> seenOrigins = new HashSet<>();
        for (String[] originRow : origins)
        {
            WorldPoint origin = TransportCatalog.parseWorldPoint(originRow[0]);
            if (BLOCKED_RING.equals(origin) || !seenOrigins.add(origin))
            {
                continue;
            }

            for (Map.Entry<WorldPoint, String[]> entry : destinations.entrySet())
            {
                WorldPoint destination = entry.getKey();
                if (origin.equals(destination) || BLOCKED_RING.equals(destination))
                {
                    continue;
                }

                String[] destinationRow = entry.getValue();
                String code = result.codesByDestination.get(destination);
                boolean questAlreadyEncodedByRuntimeGate =
                    ("CJQ".equals(code) && "Troubled Tortugans".equals(destinationRow[4]))
                        || ("BJR".equals(code) && "Holy Grail".equals(destinationRow[4]));

                TransportCatalog.TransportEdge transport = TransportCatalog.createFairyRingTransport(
                    origin,
                    destination,
                    Integer.parseInt(originRow[2].substring(originRow[2].lastIndexOf(' ') + 1)),
                    destinationRow[3],
                    questAlreadyEncodedByRuntimeGate ? "" : destinationRow[4],
                    destinationRow[5],
                    "BJR".equals(code) ? "5>8" : ""
                );
                result.transports.add(transport);
            }
        }

        return result;
    }

    List<TransportCatalog.TransportEdge> getTransports()
    {
        return Collections.unmodifiableList(transports);
    }

    static boolean isFairyRingTransport(TransportCatalog.TransportEdge transport)
    {
        return transport != null
            && "Configure".equals(transport.action)
            && "Fairy ring".equals(transport.target);
    }

    boolean isTransportAvailable(Client client, TransportCatalog.TransportEdge transport)
    {
        String code = codesByDestination.get(transport.destination);
        return code != null
            && hasFairyRingAccess(
                client.getWorldType().contains(WorldType.MEMBERS),
                client.getVarbitValue(2329),
                client.getVarbitValue(4498),
                hasEquippedStaff(),
                hasInventoryStaff())
            && isDestinationUnlocked(code, client::getVarbitValue);
    }

    static boolean hasFairyRingAccess(boolean members, int fairyRingProgress, int lumbridgeElite,
                             boolean staffWorn, boolean staffInInventory)
    {
        return members
            && fairyRingProgress == 2
            && (lumbridgeElite == 1 || staffWorn || staffInInventory);
    }

    static boolean isDestinationUnlocked(String code, IntUnaryOperator varbitValue)
    {
        if (!isValidCode(code))
        {
            return false;
        }
        if (resolveLegacyText("DIQ", 1115511665, 856392653).equals(code))
        {
            return false;
        }
        if (VARBIT_4897_CODES.contains(code))
        {
            return varbitValue.applyAsInt(4897) == 1;
        }
        if (VARBIT_9650_CODES.contains(code))
        {
            return varbitValue.applyAsInt(9650) == 1;
        }
        if (resolveLegacyText("AKP", 721503894, -1226815066).equals(code))
        {
            return varbitValue.applyAsInt(13841) >= 14;
        }
        if (resolveLegacyText("CJQ", 879289428, 953947931).equals(code))
        {
            return varbitValue.applyAsInt(18321) >= 6;
        }
        return true;
    }

    static boolean hasEquippedStaff()
    {
        return TravelEquipment.isWearing("Dramen staff", true)
            || TravelEquipment.isWearing("Lunar staff", true);
    }

    static int findBankedStaffItem(int emptySlots, boolean members, int fairyRingProgress, int lumbridgeElite,
                        boolean staffWorn, IntUnaryOperator inventoryCount,
                        IntUnaryOperator bankCount, Set<Integer> reservedItems)
    {
        if (emptySlots <= 0
            || lumbridgeElite == 1
            || staffWorn
            || !hasFairyRingAccess(members, fairyRingProgress, lumbridgeElite, false, true))
        {
            return -1;
        }

        for (int itemId : STAFF_ITEM_IDS)
        {
            if (inventoryCount.applyAsInt(itemId) > 0)
            {
                return -1;
            }
        }

        for (int itemId : STAFF_ITEM_IDS)
        {
            if (bankCount.applyAsInt(itemId) > 0 && !reservedItems.contains(itemId))
            {
                return itemId;
            }
        }

        return -1;
    }

    private static boolean hasInventoryStaff()
    {
        String dramenStaff = resolveLegacyText("Dramen staff", 1511747437, 284192685);
        if (Rs2Inventory.count(dramenStaff, true) > 0)
        {
            return true;
        }
        String lunarStaff = resolveLegacyText("Lunar staff", 1704118175, -1793350202);
        return Rs2Inventory.count(lunarStaff, true) > 0;
    }

    static String getRequiredStaffName(Client client)
    {
        if (client.getVarbitValue(4498) == 1 || hasEquippedStaff())
        {
            return null;
        }

        return Rs2Inventory.count("Dramen staff", true) > 0
            ? "Dramen staff"
            : "Lunar staff";
    }

    void resetDialTracking()
    {
        FairyRingSelection.reset(this);
        lastDialState = -1;
        lastDialProgressTick = -1;
        lastDialClickTick = -1;
        dialClickAttempts = 0;
    }

    int nextDialAction(Client client, TransportCatalog.TransportEdge transport)
    {
        return FairyRingSelection.next(this, client, transport);
    }

    static boolean isFirstWidgetActionEnabled(Client client, Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }

        if (widget.getActions() == null
            || widget.getActions().length == 0
            || widget.getActions()[0] == null)
        {
            return false;
        }

        WidgetConfigNode config = client.getWidgetConfig(widget);
        if (config == null)
        {
            return (widget.getClickMask() & 2) != 0;
        }

        return (config.getOpMask() & 1) != 0;
    }

    static boolean isValidCode(String code)
    {
        return code != null && code.length() == 3
            && DIAL_LETTERS[0].indexOf(code.charAt(0)) >= 0
            && DIAL_LETTERS[1].indexOf(code.charAt(1)) >= 0
            && DIAL_LETTERS[2].indexOf(code.charAt(2)) >= 0;
    }

    static int getNextDialWidgetChild(String code, int[] dialValues)
    {
        if (!isValidCode(code) || dialValues.length != 3)
        {
            return -1;
        }
        for (int dial = 0; dial < 3; dial++)
        {
            // Preserve both original array reads and validate every dial before selecting one.
            if (dialValues[dial] < 0 || dialValues[dial] > 3)
            {
                return -1;
            }
        }
        for (int dial = 0; dial < 3; dial++)
        {
            int distance = (DIAL_LETTERS[dial].indexOf(code.charAt(dial)) - dialValues[dial] + 4) % 4;
            if (distance != 0)
            {
                return 19 + dial * 2 + (distance == 3 ? 1 : 0);
            }
        }
        return 26;
    }

    /**
     * Keep ordinary values visible. For an unusual default charset, reconstruct
     * the exact old table slice before applying the historical seed bytes.
     */
    private static String resolveLegacyText(String text, int outerSeed, int textSeed)
    {
        byte[] seedDigits = Integer.toString(outerSeed).getBytes();
        byte[] originalDigits = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
        if (Arrays.equals(seedDigits, originalDigits))
        {
            return text.intern();
        }
        byte[] bytes = encodeLegacyPayload(text, textSeed).getBytes(StandardCharsets.UTF_16BE);
        for (int index = 0; index < bytes.length; index++)
        {
            bytes[index] = (byte) (bytes[index] ^ originalDigits[index % originalDigits.length]);
            bytes[index] = (byte) (bytes[index] ^ seedDigits[index % seedDigits.length]);
        }
        return decodeText(new String(bytes, StandardCharsets.UTF_16BE), textSeed);
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
