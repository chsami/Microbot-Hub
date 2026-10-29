package net.runelite.client.plugins.microbot.FourtifyFishing.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The kinds of fishing spot, by what they catch: the spot NPC's name and the ids of its NPCs. Several kinds share a
 * name and even actions ("Fishing spot" with "Net" is a shrimp spot, a big net spot, a boat spot and a frog spawn
 * spot), so a click picks an NPC by its id, from {@code NpcData}, which is generated and must not be edited by hand.
 * Each constant of it, named {@code KIND_npcId}, holds the columns {@code kind, npcId}: the constant of this enum and
 * the id of one NPC of that kind. The kinds
 * are the net and bait spots (shrimps, sardines), the lure and bait spots (trout, pike), the cage and harpoon spots
 * (lobsters, tuna, swordfish), the big net and harpoon spots (sharks), Piscatoris (monkfish), the Barbarian Outpost
 * (leaping fish), karambwanji, anglerfish and infernal eels.
 */
@Getter
@RequiredArgsConstructor
public enum SpotKind {
    NET_BAIT("Fishing spot"),
    LURE_BAIT("Rod Fishing spot"),
    CAGE_HARPOON("Fishing spot"),
    BIG_NET_HARPOON("Fishing spot"),
    PISCATORIS("Fishing spot"),
    BARBARIAN("Fishing spot"),
    KARAMBWANJI("Fishing spot"),
    ANGLER("Rod Fishing spot"),
    INFERNAL_EEL("Rod Fishing spot");

    private final String npcName;

    /** The ids of this kind's spot NPCs. */
    public Set<Integer> npcIds() {
        return NpcIds.BY_KIND.getOrDefault(this, Collections.emptySet());
    }

    /** Loaded on first use: an enum's constants cannot read a static field of their own class while they are made. */
    private static final class NpcIds {
        static final Map<SpotKind, Set<Integer>> BY_KIND = load();

        private static Map<SpotKind, Set<Integer>> load() {
            Map<SpotKind, Set<Integer>> ids = new EnumMap<>(SpotKind.class);
            for (NpcData npc : NpcData.values()) {
                ids.computeIfAbsent(npc.getKind(), k -> new HashSet<>()).add(npc.getNpcId());
            }
            ids.replaceAll((k, v) -> Collections.unmodifiableSet(v));
            return ids;
        }
    }
}
