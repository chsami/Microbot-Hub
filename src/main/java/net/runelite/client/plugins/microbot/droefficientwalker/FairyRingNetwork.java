package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfigNode;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Parses fairy ring destinations, evaluates access, and tracks dial selections.
 */
final class FairyRingNetwork
{
    private static final String[] DIAL_LETTERS = {"ADCB", "ILKJ", "PSRQ"};
    private static final int[] DIAL_VARBITS = {3985, 3986, 3987};
    private static final Set<String> VARBIT_4897_CODES = Set.of("AKR", "CIR", "CIS", "BLS", "DJR");
    private static final Set<String> VARBIT_9650_CODES = Set.of("CKQ", "AJP", "AIS");
    static final int[] STAFF_ITEM_IDS = {772, 9084};

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
        return Collections.unmodifiableList(this.transports);
    }

    static boolean isFairyRingTransport(TransportCatalog.TransportEdge transport)
    {
        return transport != null
            && "Configure".equals(transport.action)
            && "Fairy ring".equals(transport.target);
    }

    boolean isTransportAvailable(Client client, TransportCatalog.TransportEdge transport)
    {
        String code = this.codesByDestination.get(transport.destination);
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
        if (!isValidCode(code) || "DIQ".equals(code))
        {
            return false;
        }

        if (VARBIT_4897_CODES.contains(code) && varbitValue.applyAsInt(4897) != 1)
        {
            return false;
        }

        if (VARBIT_9650_CODES.contains(code) && varbitValue.applyAsInt(9650) != 1)
        {
            return false;
        }

        if ("AKP".equals(code) && varbitValue.applyAsInt(13841) < 14)
        {
            return false;
        }

        return !"CJQ".equals(code) || varbitValue.applyAsInt(18321) > 5;
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
        return Rs2Inventory.count("Dramen staff", true) > 0
            || Rs2Inventory.count("Lunar staff", true) > 0;
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
        this.lastDialState = -1;
        this.lastDialProgressTick = -1;
        this.lastDialClickTick = -1;
        this.dialClickAttempts = 0;
    }

    int nextDialAction(Client client, TransportCatalog.TransportEdge transport)
    {
        int tick = client.getTickCount();
        int[] dialValues = {
            client.getVarbitValue(DIAL_VARBITS[0]),
            client.getVarbitValue(DIAL_VARBITS[1]),
            client.getVarbitValue(DIAL_VARBITS[2])
        };

        int childId = getNextDialWidgetChild(this.codesByDestination.get(transport.destination), dialValues);
        if (childId < 0)
        {
            return -1;
        }

        int packedDialState = dialValues[0] * 16 + dialValues[1] * 4 + dialValues[2];
        if (this.lastDialProgressTick < 0 || packedDialState != this.lastDialState)
        {
            this.lastDialState = packedDialState;
            this.lastDialProgressTick = tick;
            this.dialClickAttempts = 0;
        }

        if (tick - this.lastDialProgressTick >= 12 || (this.dialClickAttempts >= 3 && tick - this.lastDialClickTick >= 4))
        {
            return -1;
        }

        if (this.lastDialClickTick == tick || (this.dialClickAttempts > 0 && tick - this.lastDialClickTick < 4))
        {
            return 0;
        }

        Widget widget = client.getWidget(398, childId);
        if (!isFirstWidgetActionEnabled(client, widget))
        {
            return 0;
        }

        this.lastDialClickTick = tick;
        ++this.dialClickAttempts;
        return childId;
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
        if (code == null || code.length() != 3)
        {
            return false;
        }

        for (int i = 0; i < 3; ++i)
        {
            if (DIAL_LETTERS[i].indexOf(code.charAt(i)) < 0)
            {
                return false;
            }
        }

        return true;
    }

    static int getNextDialWidgetChild(String code, int[] dialValues)
    {
        if (!isValidCode(code) || dialValues.length != 3)
        {
            return -1;
        }

        for (int value : dialValues)
        {
            if (value < 0 || value > 3)
            {
                return -1;
            }
        }

        for (int i = 0; i < 3; ++i)
        {
            int target = DIAL_LETTERS[i].indexOf(code.charAt(i));
            int current = dialValues[i];
            if (current == target)
            {
                continue;
            }

            int clockwise = (target - current + 4) & 3;
            return 19 + i * 2 + (clockwise == 3 ? 1 : 0);
        }

        return 26;
    }
}
