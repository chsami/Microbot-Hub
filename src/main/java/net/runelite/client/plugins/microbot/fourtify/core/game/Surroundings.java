package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.runelite.api.Quest;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a plugin needs in its {@link GameState} beyond the player's own state: objects and NPCs by exact name,
 * ground items by a part of their name or by item ID, all within {@code radius} tiles of the player; the bank counts of some
 * item IDs; some varbits and varps (settings, progress of the older quests); and the state of some quests. Reading only these keeps the per-tick
 * snapshot small.
 */
@Value
@Builder(toBuilder = true)
public class Surroundings {
    @Singular
    Set<String> objectNames;
    @Singular
    Set<String> groundItemNameParts;
    @Singular
    Set<Integer> groundItemIds;
    @Singular
    Set<String> npcNames;
    @Singular
    Set<Integer> bankItemIds;
    @Singular
    Set<Integer> varbits;
    @Singular
    Set<Integer> varps;
    /** Quests whose progress the snapshot holds (read through the client's quest list, on the client thread). */
    @Singular
    Set<Quest> quests;
    int radius;

    /** Asks for nothing. */
    public static Surroundings none() {
        return builder().build();
    }

    public boolean wantsGroundItem(String name) {
        String lower = name.toLowerCase();
        for (String part : groundItemNameParts) {
            if (lower.contains(part.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** A ground item is wanted when its ID is asked for, or (when it has one) its name holds a name part. */
    public boolean wantsGroundItem(int id, String name) {
        return groundItemIds.contains(id) || (name != null && wantsGroundItem(name));
    }

    /** Everything these surroundings or the other ones ask for, as far out as the farther of the two. */
    public Surroundings plus(Surroundings other) {
        return new Surroundings(union(objectNames, other.objectNames), union(groundItemNameParts, other.groundItemNameParts),
                union(groundItemIds, other.groundItemIds),
                union(npcNames, other.npcNames), union(bankItemIds, other.bankItemIds), union(varbits, other.varbits),
                union(varps, other.varps), union(quests, other.quests), Math.max(radius, other.radius));
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }
}
