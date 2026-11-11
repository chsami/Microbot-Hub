package net.runelite.client.plugins.microbot.droslayer.walker;

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

    FairyRingNetwork()
    {
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
