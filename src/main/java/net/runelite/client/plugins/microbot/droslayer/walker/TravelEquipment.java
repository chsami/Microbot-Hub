package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

/** Authoritative worn-item eligibility for all walker transport providers. */
public final class TravelEquipment
{
    private TravelEquipment() { }
    private static final ThreadLocal<Snapshot> planning = new ThreadLocal<>();

    /** One client-thread read per planning pass, never one round trip per candidate item. */
    static Scope planningScope()
    {
        Snapshot previous = planning.get();
        if (previous == null) {
            Snapshot snapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                java.util.Map<Integer, Integer> quantities = wornItems();
                java.util.Map<Integer, String> names = new java.util.HashMap<>();
                for (Integer id : quantities.keySet()) {
                    ItemComposition definition = Microbot.getClient().getItemDefinition(id);
                    if (definition != null && definition.getName() != null) names.put(id, definition.getName());
                }
                return new Snapshot(quantities, java.util.Map.copyOf(names));
            }).orElse(new Snapshot(java.util.Map.of(), java.util.Map.of()));
            planning.set(snapshot);
        }
        return () -> { if (previous == null) planning.remove(); else planning.set(previous); };
    }
    interface Scope extends AutoCloseable { @Override void close(); }
    private static final class Snapshot {
        final java.util.Map<Integer, Integer> quantities;
        final java.util.Map<Integer, String> names;
        Snapshot(java.util.Map<Integer, Integer> quantities, java.util.Map<Integer, String> names) {
            this.quantities = quantities; this.names = names;
        }
    }
    public static boolean isWearing(int... ids)
    {
        return query(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
    }
    public static boolean isWearing(String name, boolean exact) { return isWearing(new String[]{name}, exact); }
    public static boolean isWearing(String... names) { return isWearing(names, false); }
    public static boolean isWearing(String[] names, boolean exact)
    {
        return query(item -> {
            Snapshot snapshot = planning.get();
            ItemComposition definition = snapshot == null ? Microbot.getClient().getItemDefinition(item.getId()) : null;
            String actual = snapshot != null ? snapshot.names.get(item.getId()) : definition == null ? null : definition.getName();
            if (actual == null) return false;
            for (String name : names) if (name != null && (exact ? actual.equalsIgnoreCase(name)
                : actual.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))) return true;
            return false;
        });
    }
    public static java.util.Map<Integer, Integer> wornItems()
    {
        Snapshot snapshot = planning.get();
        if (snapshot != null) return snapshot.quantities;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            java.util.Map<Integer, Integer> result = new java.util.LinkedHashMap<>();
            ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
            if (container != null && container.getItems() != null)
                for (Item item : container.getItems()) if (item != null && item.getId() > 0 && item.getQuantity() > 0)
                    result.merge(item.getId(), item.getQuantity(), Integer::sum);
            return java.util.Map.copyOf(result);
        }).orElse(java.util.Map.of());
    }
    public static int carriedFingerprint()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int hash = 1;
            for (InventoryID source : new InventoryID[]{InventoryID.INVENTORY, InventoryID.EQUIPMENT}) {
                hash = 31 * hash + source.getId();
                ItemContainer container = Microbot.getClient().getItemContainer(source);
                if (container == null || container.getItems() == null) { hash *= 31; continue; }
                for (Item item : container.getItems()) {
                    hash = 31 * hash + (item == null ? -1 : item.getId());
                    hash = 31 * hash + (item == null ? 0 : item.getQuantity());
                }
            }
            return hash;
        }).orElse(0);
    }

    private static boolean query(Predicate<Item> match)
    {
        Snapshot snapshot = planning.get();
        if (snapshot != null) {
            for (java.util.Map.Entry<Integer, Integer> item : snapshot.quantities.entrySet())
                if (match.test(new Item(item.getKey(), item.getValue()))) return true;
            return false;
        }
        if (Microbot.getClient() == null || Microbot.getClientThread() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer worn = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
            if (worn == null || worn.getItems() == null) return false;
            for (Item item : worn.getItems())
                if (item != null && item.getId() > 0 && item.getQuantity() > 0 && match.test(item)) return true;
            return false;
        }).orElse(false);
    }
}
