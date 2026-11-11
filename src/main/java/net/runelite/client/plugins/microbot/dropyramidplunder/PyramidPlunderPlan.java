package net.runelite.client.plugins.microbot.dropyramidplunder;

/**
 * What to loot in each room and when to move on. Room values and the strategy come from the OSRS
 * Wiki's Pyramid Plunder page (read 2026-11-09); the per-action seconds come from the owner's
 * recording of the same day.
 *
 * <ul>
 * <li>Urns are looted only in the last two rooms the Thieving level allows, and the last room keeps
 * enough of the five minutes to be emptied.</li>
 * <li>The Grand Gold Chest is searched in every room.</li>
 * <li>Sarcophagi give Strength experience only and are slow, so they are opened from room 4, where
 * the sceptre chance is 1/950 or better (rooms 1-3: 1/4,200 to 1/1,600).</li>
 * <li>In an urn room the chest and sarcophagus come last, just before leaving, so a scarab swarm or
 * mummy is not fought through the whole urn phase.</li>
 * </ul>
 *
 * <p>Better XP mode (on by default) uses the same two urn rooms at every Thieving level (7 and 8 at
 * 91, 5 and 6 at 71): no sarcophagi, the chest in every room, and the room before the last is left
 * with about {@link #XP_LEAVE_SECONDS} seconds on the timer (no last-room reserve), so the last
 * room gets the rest and is looted until the timer.
 */
final class PyramidPlunderPlan {
    /** The run timer varbit counts game ticks up to this value. */
    static final int RUN_TICKS = 500;
    static final int FIRST_SARCOPHAGUS_ROOM = 4;
    static final int LAST_ROOM = 8;

    /** Recorded: trap pass and the walk in, a chest search, a sarcophagus, and an average door search. */
    static final double TRAP_SECONDS = 8;
    static final double CHEST_SECONDS = 5;
    static final double SARCOPHAGUS_SECONDS = 10;
    static final double DOOR_SECONDS = 10;
    /** Recorded at 96 Thieving in room 8: 13 urns in about 95 seconds, failed searches included. */
    static final double DEFAULT_SECONDS_PER_URN = 7.3;
    static final double MIN_RESERVE_SECONDS = 60;
    static final double MAX_RESERVE_SECONDS = 170;
    /**
     * Better XP mode: the room before the last starts another urn only while that urn and the chest
     * still leave this much of the run, so its doors are started with about 1:15 to 1:20 left.
     */
    static final double XP_LEAVE_SECONDS = 75;

    private static final int[] URN_XP = {60, 90, 150, 215, 300, 450, 675, 825};
    private static final int[] CHEST_XP = {40, 60, 100, 140, 200, 300, 450, 550};

    enum Step { TRAP, URN, CHEST, SARCOPHAGUS, DOOR, LEAVE }

    private PyramidPlunderPlan() { }

    /** Room 1 needs 21 Thieving and every further room ten more; boosts do not count. */
    static int maxRoom(int thievingLevel) {
        if (thievingLevel < 21) return 0;
        return Math.min(LAST_ROOM, (thievingLevel - 21) / 10 + 1);
    }

    static int urnXp(int room) { return URN_XP[clampRoom(room) - 1]; }
    static int chestXp(int room) { return CHEST_XP[clampRoom(room) - 1]; }
    /** Every room has 13 urns except the third, which has 12. */
    static int urnCount(int room) { return room == 3 ? 12 : 13; }

    static boolean urnRoom(int room, int maxRoom) {
        return room >= 1 && room <= maxRoom && room >= maxRoom - 1;
    }

    /** The first of the two urn rooms: the room before the last one the level reaches. */
    static int firstUrnRoom(int maxRoom) {
        return Math.max(1, maxRoom - 1);
    }

    static boolean opensSarcophagus(int room, boolean enabled) {
        return enabled && room >= FIRST_SARCOPHAGUS_ROOM;
    }

    static boolean opensSarcophagus(int room, boolean enabled, boolean xpMode) {
        // Better XP mode never opens a sarcophagus (Strength experience only, and slow).
        return !xpMode && opensSarcophagus(room, enabled);
    }

    static double secondsLeft(int timerTicks) {
        return Math.max(0, RUN_TICKS - timerTicks) * 0.6;
    }

    /** Seconds the last room needs to be emptied, from the measured pace of earlier runs. */
    static double lastRoomReserveSeconds(int lastRoom, double secondsPerUrn, boolean sarcophagi) {
        return lastRoomReserveSeconds(lastRoom, secondsPerUrn, sarcophagi, false);
    }

    static double lastRoomReserveSeconds(int lastRoom, double secondsPerUrn, boolean sarcophagi, boolean xpMode) {
        double perUrn = secondsPerUrn > 0 ? secondsPerUrn : DEFAULT_SECONDS_PER_URN;
        double reserve = TRAP_SECONDS + urnCount(lastRoom) * perUrn + CHEST_SECONDS
                + (opensSarcophagus(lastRoom, sarcophagi, xpMode) ? SARCOPHAGUS_SECONDS : 0);
        return Math.max(MIN_RESERVE_SECONDS, Math.min(MAX_RESERVE_SECONDS, reserve));
    }

    /**
     * The next thing to do in a room.
     *
     * @param chestClosed       the chest has not been searched (and is wanted)
     * @param sarcophagusClosed the sarcophagus has not been opened (and is wanted)
     */
    static Step next(int room, int maxRoom, boolean trapActive, double secondsLeft, double lastRoomReserve,
            double secondsPerUrn, int urnsLeft, boolean chestClosed, boolean sarcophagusClosed) {
        return next(room, maxRoom, false, trapActive, secondsLeft, lastRoomReserve, secondsPerUrn, urnsLeft,
                chestClosed, sarcophagusClosed);
    }

    /** @param xpMode Better XP mode: the room before the last is left by the timer, not the reserve */
    static Step next(int room, int maxRoom, boolean xpMode, boolean trapActive, double secondsLeft,
            double lastRoomReserve, double secondsPerUrn, int urnsLeft, boolean chestClosed,
            boolean sarcophagusClosed) {
        if (trapActive) return Step.TRAP;
        boolean last = room >= maxRoom;
        double perUrn = secondsPerUrn > 0 ? secondsPerUrn : DEFAULT_SECONDS_PER_URN;
        double containers = (chestClosed ? CHEST_SECONDS : 0) + (sarcophagusClosed ? SARCOPHAGUS_SECONDS : 0);
        if (urnRoom(room, maxRoom) && urnsLeft > 0) {
            if (last) {
                // Keep the containers for the end, but never lose them to the timer.
                if (secondsLeft > containers + perUrn) return Step.URN;
            } else if (xpMode) {
                // One more urn only while it and the chest still leave the last room its share.
                if (secondsLeft - containers - perUrn > XP_LEAVE_SECONDS) return Step.URN;
            } else if (secondsLeft - containers - DOOR_SECONDS - perUrn > lastRoomReserve) {
                return Step.URN;
            }
        }
        if (chestClosed) return Step.CHEST;
        if (sarcophagusClosed) return Step.SARCOPHAGUS;
        if (!last) return Step.DOOR;
        return urnsLeft > 0 && urnRoom(room, maxRoom) ? Step.URN : Step.LEAVE;
    }

    /** Smoothed seconds per looted urn; a single odd room moves the estimate by a third. */
    static double learnSecondsPerUrn(double current, double measuredSeconds, int urnsLooted) {
        if (urnsLooted < 4 || measuredSeconds <= 0) return current;
        double measured = Math.max(4.0, Math.min(14.0, measuredSeconds / urnsLooted));
        double base = current > 0 ? current : DEFAULT_SECONDS_PER_URN;
        return base + (measured - base) / 3.0;
    }

    private static int clampRoom(int room) { return Math.max(1, Math.min(LAST_ROOM, room)); }
}
