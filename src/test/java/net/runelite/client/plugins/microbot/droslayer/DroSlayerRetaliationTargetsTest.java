package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerRetaliationTargetsTest {
    private Object invoke(DroSlayerScript script, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = DroSlayerScript.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(script, args);
    }

    private void verifyTargets(boolean includeWorker) throws Exception {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        Player player = mock(Player.class);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        Rs2NpcModel larva = mock(Rs2NpcModel.class);
        Rs2NpcModel worker = mock(Rs2NpcModel.class);
        NPCComposition harmless = mock(NPCComposition.class);
        NPCComposition fighter = mock(NPCComposition.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        when(client.getLocalPlayer()).thenReturn(player);
        when(larva.getId()).thenReturn(1);
        when(worker.getId()).thenReturn(2);
        // -1: no health bar shown yet, as for a live NPC that has not been hit.
        when(larva.getHealthRatio()).thenReturn(-1);
        when(worker.getHealthRatio()).thenReturn(-1);
        when(larva.getName()).thenReturn("Kalphite Larva");
        when(worker.getName()).thenReturn("Kalphite Worker");
        when(larva.getInteracting()).thenReturn(player);
        when(worker.getInteracting()).thenReturn(player);
        when(client.getNpcDefinition(1)).thenReturn(harmless);
        when(client.getNpcDefinition(2)).thenReturn(fighter);
        when(harmless.getActions()).thenReturn(new String[0]);
        when(fighter.getActions()).thenReturn(new String[]{null, "Attack", "Examine"});
        List<Rs2NpcModel> scene = includeWorker ? Arrays.asList(larva, worker) : Collections.singletonList(larva);
        when(cache.query()).thenAnswer(call -> {
            Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
            List<Predicate<Rs2NpcModel>> filters = new ArrayList<>();
            when(query.where(any())).thenAnswer(filter -> {
                filters.add(filter.getArgument(0));
                return query;
            });
            when(query.firstOnClientThread()).thenAnswer(ignored -> scene.stream()
                    .filter(npc -> filters.stream().allMatch(predicate -> predicate.test(npc)))
                    .findFirst().orElse(null));
            when(query.count()).thenAnswer(ignored -> (int) scene.stream()
                    .filter(npc -> filters.stream().allMatch(predicate -> predicate.test(npc))).count());
            return query;
        });
        try (MockedStatic<Microbot> dro = mockStatic(Microbot.class);
             MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            DroSlayerScript script = new DroSlayerScript();
            assertEquals(includeWorker, invoke(script, "isBeingAttacked", new Class<?>[0]));
            assertSame(includeWorker ? worker : null, invoke(script, "findNpcAttackingUs",
                    new Class<?>[]{List.class}, Collections.singletonList("Kalphite")));
            assertSame(includeWorker ? worker : null, invoke(script, "findAnyNpcAttackingUs", new Class<?>[0]));
            // A dying NPC (zero health) still interacts during its death animation; never retarget it.
            when(worker.getHealthRatio()).thenReturn(0);
            assertNull(invoke(script, "findAnyNpcAttackingUs", new Class<?>[0]));
            when(worker.getHealthRatio()).thenReturn(-1);
            assertEquals(false, invoke(script, "attackNpc", new Class<?>[]{Rs2NpcModel.class}, larva));
            runtime.verifyNoInteractions();
            verify(larva, never()).click(anyString());
            when(worker.click("Attack")).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false),
                    any(java.util.function.BooleanSupplier.class))).thenAnswer(call ->
                    ((java.util.function.BooleanSupplier) call.getArgument(2)).getAsBoolean());
            assertEquals(true, invoke(script, "attackNpc", new Class<?>[]{Rs2NpcModel.class}, worker));
            verify(worker).click("Attack");
            // Dead or temporarily unavailable NPC definitions must also fail closed.
            when(worker.isDead()).thenReturn(true);
            assertEquals(false, invoke(script, "canAttackNpc", new Class<?>[]{Rs2NpcModel.class}, worker));
            when(worker.isDead()).thenReturn(false);
            when(client.getNpcDefinition(2)).thenReturn(null);
            assertEquals(false, invoke(script, "canAttackNpc", new Class<?>[]{Rs2NpcModel.class}, worker));
        }
    }

    @Test public void larvaCannotStarveWorkerRetaliationOrWakeMouse() throws Exception { verifyTargets(true); }
    @Test public void harmlessInteractionAloneDoesNotCountAsCombat() throws Exception { verifyTargets(false); }
}
