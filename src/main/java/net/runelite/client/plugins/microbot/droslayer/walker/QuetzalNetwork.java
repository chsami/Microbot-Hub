package net.runelite.client.plugins.microbot.droslayer.walker;

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

    private final List<TransportCatalog.TransportEdge> transports = new ArrayList<>();

    QuetzalNetwork()
    {
    }

    static boolean isWhistleRechargeMessage(String message)
    {
        return "Soar Leader Pitri|There you go. Some whistle charges for you!".equals(message)
            || "Soar Leader Pitri|Looks like the birds are all full for now. Make them work a bit before feeding them again!".equals(message);
    }

}
