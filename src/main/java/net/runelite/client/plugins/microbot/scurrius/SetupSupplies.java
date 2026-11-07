package net.runelite.client.plugins.microbot.scurrius;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an Inventory Setup requires, derived from its item list: how much food, how many prayer doses and how many
 * teleport tabs (by stack quantity). Pure, so it is tested without a client.
 */
public final class SetupSupplies {
    private static final Pattern DOSE = Pattern.compile("\\((\\d)\\)\\s*$");

    private SetupSupplies() {
    }

    /** One row of a setup (or one inventory slot): id, name and quantity. */
    public static final class Item {
        public final int id;
        public final String name;
        public final int quantity;

        public Item(int id, String name, int quantity) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.quantity = Math.max(1, quantity);
        }
    }

    /** The amounts a setup asks for. */
    public static final class Needs {
        public final int food;
        public final int prayerDoses;
        public final int tabs;

        Needs(int food, int prayerDoses, int tabs) {
            this.food = food;
            this.prayerDoses = prayerDoses;
            this.tabs = tabs;
        }
    }

    /** Food = items the client knows as food, prayer = doses of prayer potions and super restores, tabs = the tab stack quantity. */
    public static Needs derive(Collection<Item> setupItems, IntPredicate isFood, int tabId) {
        int food = 0;
        int doses = 0;
        int tabs = 0;
        for (Item item : setupItems) {
            if (isFood.test(item.id)) {
                food += item.quantity;
            } else if (isPrayerRestore(item.name)) {
                doses += item.quantity * doses(item.name);
            } else if (item.id == tabId) {
                tabs += item.quantity;
            }
        }
        return new Needs(food, doses, tabs);
    }

    public static boolean isPrayerRestore(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.contains("prayer potion") || lower.contains("super restore");
    }

    /** Doses in a potion from its name, "Prayer potion(3)" is 3; a name without a dose count is 1. */
    public static int doses(String name) {
        Matcher matcher = DOSE.matcher(name == null ? "" : name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    /** Whether the setup is met: nothing short. */
    public static boolean isMet(Needs needs, int foodHave, int dosesHave, int tabsHave) {
        return foodHave >= needs.food && dosesHave >= needs.prayerDoses && tabsHave >= needs.tabs;
    }

    /**
     * Every setup item (by id, quantities added up) that the inventory is short of, as "Name have/need", for the message
     * when the bank cannot supply it.
     */
    public static List<String> missing(Collection<Item> setupItems, IntUnaryOperator haveById) {
        Map<Integer, int[]> needById = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Item item : setupItems) {
            needById.computeIfAbsent(item.id, id -> new int[1])[0] += item.quantity;
            names.putIfAbsent(item.id, item.name.isEmpty() ? "item " + item.id : item.name);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : needById.entrySet()) {
            int have = haveById.applyAsInt(entry.getKey());
            if (have < entry.getValue()[0]) {
                out.add(names.get(entry.getKey()) + " " + have + "/" + entry.getValue()[0]);
            }
        }
        return out;
    }
}
