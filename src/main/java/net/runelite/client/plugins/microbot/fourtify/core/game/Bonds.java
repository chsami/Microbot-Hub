package net.runelite.client.plugins.microbot.fourtify.core.game;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Old School bonds, tradeable, noted and untradeable. The account rule: they are never dropped,
 * deposited, withdrawn or used (a bond's item action redeems it); {@code ClientActions} refuses, and tasks leave them.
 */
public final class Bonds {
    public static final Set<Integer> IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ItemID.OSRS_BOND, ItemID.BOUGHT_OSRS_BOND, ItemID.OSRS_BOND_UNTRADEABLE)));

    private Bonds() {
    }
}
