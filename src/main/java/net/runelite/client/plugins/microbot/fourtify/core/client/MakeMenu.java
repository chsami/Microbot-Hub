package net.runelite.client.plugins.microbot.fourtify.core.client;

/**
 * The make menu, interface 270 ({@code skillmulti}): up to 18 product slots, children 15 to 32,
 * filled in order, each drawing its product as a model of that item; and the quantity buttons, "All" at child 12.
 * The "All" button has its "All" action only while it is not the chosen quantity.
 */
final class MakeMenu {
    static final int GROUP = 270;
    static final int ROOT = 0;
    static final int ALL = 12;
    static final int FIRST_SLOT = 15;
    static final int LAST_SLOT = 32;

    private MakeMenu() {
    }
}
