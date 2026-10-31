package net.runelite.client.plugins.microbot.aiomagic.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure planner for the cheapest outfit that reaches the splash magic bonus. One piece (or none) per slot,
 * only pieces with a negative magic attack bonus are ever considered, so the total can never get worse.
 */
public final class SplashGear {
    /** Equipment slot indexes (EquipmentInventorySlot.getSlotIdx). */
    public static final int HEAD = 0, CAPE = 1, AMULET = 2, WEAPON = 3, BODY = 4, SHIELD = 5, LEGS = 7, HANDS = 9, FEET = 10;

    public static final String[] PIECES = {"full helm", "platebody", "platelegs", "kiteshield"};
    /** Metal name and the Defence level needed to wear it. */
    public static final String[] METALS = {"Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune"};
    public static final int[] METAL_DEFENCE = {1, 1, 5, 10, 20, 30, 40};

    public static final class Piece {
        public final int slot;
        public final String name;
        public final int itemId;
        public final int bonus;
        public final long cost;
        public final boolean owned;

        public Piece(int slot, String name, int itemId, int bonus, long cost, boolean owned) {
            this.slot = slot;
            this.name = name;
            this.itemId = itemId;
            this.bonus = bonus;
            this.cost = owned ? 0 : cost;
            this.owned = owned;
        }
    }

    public static final class Plan {
        public final List<Piece> pieces;
        public final int bonus;
        public final long cost;

        Plan(List<Piece> pieces, int bonus, long cost) {
            this.pieces = Collections.unmodifiableList(pieces);
            this.bonus = bonus;
            this.cost = cost;
        }
    }

    private SplashGear() {
    }

    public static String metalPieceName(String metal, String piece) {
        return metal + " " + piece;
    }

    public static boolean metalWearable(int metalIndex, int defenceLevel) {
        return defenceLevel >= METAL_DEFENCE[metalIndex];
    }

    /**
     * Cheapest set of pieces such that {@code baseBonus + sum(pieces) <= target}; ties go to fewer pieces.
     * {@code baseBonus} is what is worn outside the planned slots (e.g. a staff). Null if unreachable.
     * If {@code blockShield}, the shield slot is skipped (two-handed weapon).
     */
    public static Plan select(Collection<Piece> candidates, int baseBonus, int target, boolean blockShield) {
        Map<Integer, List<Piece>> bySlot = new TreeMap<>();
        for (Piece p : candidates) {
            if (p.bonus >= 0) continue;
            if (blockShield && p.slot == SHIELD) continue;
            bySlot.computeIfAbsent(p.slot, k -> new ArrayList<>()).add(p);
        }
        Map<Integer, Plan> states = new HashMap<>();
        states.put(0, new Plan(new ArrayList<>(), 0, 0));
        for (List<Piece> options : bySlot.values()) {
            Map<Integer, Plan> next = new HashMap<>(states);
            for (Plan cur : states.values()) {
                for (Piece p : options) {
                    List<Piece> list = new ArrayList<>(cur.pieces);
                    list.add(p);
                    Plan cand = new Plan(list, cur.bonus + p.bonus, cur.cost + p.cost);
                    Plan existing = next.get(cand.bonus);
                    if (existing == null || better(cand, existing)) next.put(cand.bonus, cand);
                }
            }
            states = next;
        }
        Plan best = null;
        for (Plan p : states.values()) {
            if (baseBonus + p.bonus > target) continue;
            if (best == null || better(p, best)) best = p;
        }
        return best;
    }

    private static boolean better(Plan a, Plan b) {
        if (a.cost != b.cost) return a.cost < b.cost;
        return a.pieces.size() < b.pieces.size();
    }
}
