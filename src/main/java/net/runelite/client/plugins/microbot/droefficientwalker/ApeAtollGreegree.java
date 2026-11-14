package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/** Reviewed AIO protection: owned supplies before departure, observed equipment after landing. */
final class ApeAtollGreegree {
    static final List<Integer> IDS = List.of(19525, 4024, 4025, 4026, 4027, 4028, 4029, 4030, 4031);
    static final int TABLET = ItemID.TELETAB_APE;
    static final WorldPoint TABLET_LANDING = new WorldPoint(2771, 9102, 0);
    private int startedTick = -1;
    private volatile String failure;

    static boolean surface(WorldPoint point) {
        return point != null && point.getPlane() >= 0 && point.getPlane() <= 1
            && point.getX() >= 2688 && point.getX() < 2816 && point.getY() >= 2688 && point.getY() < 2816;
    }

    static boolean onIsland(WorldPoint point) {
        return surface(point) || point != null && point.getPlane() == 0 && point.getX() >= 2688
            && point.getX() < 2816 && point.getY() >= 9088 && point.getY() < 9216;
    }

    static boolean required(WorldPoint point, QuestState mm2) { return onIsland(point) && mm2 != QuestState.FINISHED; }

    static int first(IntPredicate available) { return IDS.stream().filter(available::test).findFirst().orElse(0); }

    boolean tick(WorldPoint player, QuestState mm2, int worn, int carried, int tick, IntPredicate hold) {
        if (!required(player, mm2) || IDS.contains(worn)) { reset(); return false; }
        if (failure != null) return true;
        if (startedTick >= 0) {
            // Inventory can remove the item one tick before equipment reports it. Keep waiting
            // for the original Hold; neither absence nor a successful click proves completion.
            if (tick - startedTick >= 16) fail("Monkey greegree was not equipped; Ape Atoll movement stopped.");
            return true;
        }
        if (!IDS.contains(carried)) {
            fail("Ape Atoll travel before MM2 requires an owned monkey greegree.");
            return true;
        }
        if (hold.test(carried)) startedTick = tick;
        else fail("Could not request Hold for the monkey greegree; Ape Atoll movement stopped.");
        return true;
    }

    String failure() { return failure; }
    void pause(int tick) { if (startedTick >= 0) startedTick = tick; }
    boolean pending() { return startedTick >= 0 && failure == null; }
    void fail(String reason) { failure = reason; }
    void reset() { startedTick = -1; failure = null; }
}
