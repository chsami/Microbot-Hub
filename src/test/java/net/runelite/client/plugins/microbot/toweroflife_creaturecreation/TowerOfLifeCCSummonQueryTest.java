package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TowerOfLifeCCSummonQueryTest {

    private static final int HOMUNCULUS_ID = NpcID.TOL_HOMONCULUS;

    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private final Player localPlayer = mock(Player.class);

    @Test
    void summonQueryEvaluatesInteractingOnClientThread() {
        Rs2NpcModel idle = npc(NpcID.TOL_UNICOW, null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2NpcQueryable query = stubQuery(microbot, List.of(idle));

            assertSame(idle, new TowerOfLifeCCScript().findSummonedCreature(ToLCreature.UNICOW));
            verify(query, never()).nearest();
        }
    }

    @Test
    void summonQueryAcceptsCreatureTargetingLocalPlayer() {
        Rs2NpcModel busy = npc(NpcID.TOL_UNICOW, mock(Actor.class));
        Rs2NpcModel mine = npc(NpcID.TOL_UNICOW, localPlayer);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(busy, mine));

            assertSame(mine, new TowerOfLifeCCScript().findSummonedCreature(ToLCreature.UNICOW));
        }
    }

    @Test
    void summonQueryRejectsCreatureTargetingSomeoneElse() {
        Rs2NpcModel busy = npc(NpcID.TOL_UNICOW, mock(Actor.class));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(busy));

            assertNull(new TowerOfLifeCCScript().findSummonedCreature(ToLCreature.UNICOW));
        }
    }

    @Test
    void summonQueryIgnoresHomunculusAndOtherCreatures() {
        Rs2NpcModel homunculus = npc(HOMUNCULUS_ID, null);
        Rs2NpcModel spidine = npc(NpcID.TOL_SPIDINE, null);
        Rs2NpcModel unicow = npc(NpcID.TOL_UNICOW, null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(homunculus, spidine, unicow));

            assertSame(unicow, new TowerOfLifeCCScript().findSummonedCreature(ToLCreature.UNICOW));
        }
    }

    @Test
    void summonQueryFindsNothingWhenOnlyHomunculusIsNearby() {
        Rs2NpcModel homunculus = npc(HOMUNCULUS_ID, null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(homunculus));

            assertNull(new TowerOfLifeCCScript().findSummonedCreature(ToLCreature.SPIDINE));
        }
    }

    @Test
    void summonedCreatureIsPresentWhileItsNpcIsInTheScene() {
        Rs2NpcModel unicow = npc(NpcID.TOL_UNICOW, null);
        NPC unicowNpc = unicow.getNpc();
        Rs2NpcModel refreshed = mock(Rs2NpcModel.class);
        when(refreshed.getNpc()).thenReturn(unicowNpc);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(refreshed));
            TowerOfLifeCCScript script = new TowerOfLifeCCScript();
            script.summonedCreature = unicow;

            assertTrue(script.isSummonedCreaturePresent());
        }
    }

    @Test
    void despawnedSummonedCreatureIsNotPresent() {
        Rs2NpcModel despawned = npc(NpcID.TOL_UNICOW, null);
        Rs2NpcModel other = npc(NpcID.TOL_UNICOW, null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(other));
            TowerOfLifeCCScript script = new TowerOfLifeCCScript();
            script.summonedCreature = despawned;

            assertFalse(script.isSummonedCreaturePresent());
        }
    }

    @Test
    void noSummonedCreatureIsNotPresent() {
        assertFalse(new TowerOfLifeCCScript().isSummonedCreaturePresent());
    }

    private Rs2NpcModel npc(int id, Actor interacting) {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return id;
        });
        when(npc.getInteracting()).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return interacting;
        });
        Rs2NpcModel model = mock(Rs2NpcModel.class);
        when(model.getNpc()).thenReturn(npc);
        return model;
    }

    @SuppressWarnings("unchecked")
    private Rs2NpcQueryable stubQuery(MockedStatic<Microbot> microbot, List<Rs2NpcModel> npcs) {
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        microbot.when(Microbot::getClient).thenReturn(client);

        AtomicReference<Predicate<Rs2NpcModel>> filter = new AtomicReference<>(npc -> true);
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.where(any())).thenAnswer(invocation -> {
            filter.set(filter.get().and(invocation.getArgument(0)));
            return query;
        });
        when(query.first()).thenAnswer(invocation -> first(npcs, filter.get()));
        when(query.nearest()).thenAnswer(invocation -> first(npcs, filter.get()));
        when(query.nearestOnClientThread()).thenAnswer(invocation -> {
            onClientThread.set(true);
            try {
                return first(npcs, filter.get());
            } finally {
                onClientThread.set(false);
            }
        });

        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
        return query;
    }

    private static Rs2NpcModel first(List<Rs2NpcModel> npcs, Predicate<Rs2NpcModel> filter) {
        return npcs.stream().filter(filter).findFirst().orElse(null);
    }
}
