package net.runelite.client.plugins.microbot.autofarmer.randomevents;
import java.util.*;
/** Keeps pre-existing supplies and non-food rewards out of the drop list. */
final class EventFoodTracker {
    static final Set<Integer> FOOD = Set.of(6961,6962,6963,6965,2327,1971,1973);
    private final Map<Integer,Integer> baseline = new HashMap<>(), pending = new HashMap<>();
    synchronized void begin(Map<Integer,Integer> counts) { baseline.clear(); baseline.putAll(counts); }
    synchronized void complete(Map<Integer,Integer> counts) {
        for (int id : FOOD) {
            int gained = counts.getOrDefault(id,0) - baseline.getOrDefault(id,0);
            if (gained > 0) pending.merge(id,gained,Integer::sum);
        }
    }
    synchronized int allowance(int id,int current) {
        return FOOD.contains(id) ? Math.max(0,Math.min(pending.getOrDefault(id,0), current-baseline.getOrDefault(id,0))) : 0;
    }
    synchronized void dropped(int id,int quantity) { pending.computeIfPresent(id,(k,v)->Math.max(0,v-quantity)); }
}
