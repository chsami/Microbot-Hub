package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public final class CharterFare {
    private static final WorldPoint MOS = new WorldPoint(3671,2931,0);
    private CharterFare() { }
    public static boolean cabinFever() { return Rs2Player.getQuestState(Quest.CABIN_FEVER) == QuestState.FINISHED; }
    public static boolean charos() { return TravelEquipment.isWearing(6465); }
    public static int quote(WorldPoint from, WorldPoint to, int base, boolean cabin, boolean charos) {
        boolean mos = MOS.equals(from) || MOS.equals(to);
        int fare = cabin && !mos ? base / 2 : base;
        return charos ? fare / 2 : fare;
    }
    public static int amount(Transport row) {
        return row.getType() == TransportType.CHARTER_SHIP
            ? quote(row.getOrigin(), row.getDestination(), row.getCurrencyAmount(), cabinFever(), charos())
            : row.getCurrencyAmount();
    }
}
