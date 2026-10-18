package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TowerOfLifeCCSummonQueryTest {

    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private final Player localPlayer = mock(Player.class);

    @Test
    void summonQueryEvaluatesInteractingOnClientThread() {
        Rs2NpcModel idle = npc(null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2NpcQueryable query = stubQuery(microbot, List.of(idle));

            assertSame(idle, new TowerOfLifeCCScript().findSummonedCreature());
            verify(query, never()).nearest();
        }
    }

    @Test
    void summonQueryAcceptsCreatureTargetingLocalPlayer() {
        Rs2NpcModel busy = npc(mock(Actor.class));
        Rs2NpcModel mine = npc(localPlayer);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(busy, mine));

            assertSame(mine, new TowerOfLifeCCScript().findSummonedCreature());
        }
    }

    @Test
    void summonQueryRejectsCreatureTargetingSomeoneElse() {
        Rs2NpcModel busy = npc(mock(Actor.class));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubQuery(microbot, List.of(busy));

            assertNull(new TowerOfLifeCCScript().findSummonedCreature());
        }
    }

    private Rs2NpcModel npc(Actor interacting) {
        NPC npc = mock(NPC.class);
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
