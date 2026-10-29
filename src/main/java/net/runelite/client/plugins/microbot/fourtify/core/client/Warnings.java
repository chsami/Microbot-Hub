package net.runelite.client.plugins.microbot.fourtify.core.client;

import java.util.Arrays;
import java.util.List;

/**
 * The game's warning screens: interfaces {@code cws_warning_1} to {@code cws_warning_25},
 * which show before a dangerous area, and {@code wilderness_warningscreen}. Each warning screen has its text
 * at child 14, Close at child 12, and "Yes" at child 17, except the two that only inform, which have Close alone.
 */
final class Warnings {
    static final List<Integer> SCREENS = Arrays.asList(319, 327, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570,
            571, 572, 573, 574, 575, 576, 577, 578, 579, 580, 581);
    /** {@code cws_warning_11} (a cold area that drains stats) and {@code cws_warning_24} (Death Plateau). */
    static final List<Integer> INFORM_ONLY = Arrays.asList(327, 581);
    static final int CLOSE = 12;
    static final int TEXT = 14;
    static final int YES = 17;
    /** The Wilderness ditch warning; its message (child 10) is set by the game when it opens. */
    static final int WILDERNESS = 475;
    static final int WILDERNESS_TEXT = 10;

    private Warnings() {
    }
}
