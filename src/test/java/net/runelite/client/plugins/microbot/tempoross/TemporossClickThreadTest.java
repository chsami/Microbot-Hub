package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporossClickThreadTest
{
    private static final WorldPoint TETHER_LOCATION = new WorldPoint(11140, 6483, 0);

    private ExecutorService clientExecutor;
    private Thread clientThread;
    private ClientThread clientThreadMock;
    private final List<Thread> lookupThreads = new ArrayList<>();
    private final List<Thread> clickThreads = new ArrayList<>();
    private final List<Object[]> restore = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        clientExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
        clientThread = clientExecutor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
        clientThreadMock = mock(ClientThread.class);
        when(clientThreadMock.invoke(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return clientExecutor.submit(supplier::get).get(5, TimeUnit.SECONDS);
        });
        swapMicrobotField("clientThread", clientThreadMock);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        for (int i = restore.size() - 1; i >= 0; i--)
        {
            Object[] entry = restore.get(i);
            Field field = Microbot.class.getDeclaredField((String) entry[0]);
            field.setAccessible(true);
            field.set(null, entry[1]);
        }
        clientExecutor.shutdownNow();
    }

    @Test
    void tetherClickRunsOnScriptThreadAfterClientThreadLookup() throws Exception
    {
        Rs2TileObjectModel tether = mock(Rs2TileObjectModel.class);
        when(tether.click("Tether")).thenAnswer(invocation -> {
            clickThreads.add(Thread.currentThread());
            return true;
        });
        stubObjectLookup(tether);

        boolean clicked = invokeClickObject(new TemporossObjectSnapshot(41010, null, TETHER_LOCATION), "Tether");

        assertTrue(clicked);
        assertEquals(List.of(clientThread), lookupThreads);
        assertEquals(1, clickThreads.size());
        assertSame(Thread.currentThread(), clickThreads.get(0));
    }

    @Test
    void missingObjectIsNotClicked() throws Exception
    {
        stubObjectLookup(null);

        assertFalse(invokeClickObject(new TemporossObjectSnapshot(41010, null, TETHER_LOCATION), "Tether"));
        assertEquals(List.of(clientThread), lookupThreads);
    }

    @Test
    void npcClickRunsOnScriptThreadAfterClientThreadLookup() throws Exception
    {
        Rs2NpcModel fire = mock(Rs2NpcModel.class);
        when(fire.getNpc()).thenReturn(mock(NPC.class));
        when(fire.click("Douse")).thenAnswer(invocation -> {
            clickThreads.add(Thread.currentThread());
            return true;
        });
        stubNpcLookup(fire);

        boolean clicked = invokeClickNpc(new TemporossNpcSnapshot(8643, 7, null, TETHER_LOCATION, "Fire"), "Douse");

        assertTrue(clicked);
        assertEquals(List.of(clientThread), lookupThreads);
        assertEquals(1, clickThreads.size());
        assertSame(Thread.currentThread(), clickThreads.get(0));
    }

    @Test
    void npcWithoutActorIsNotClicked() throws Exception
    {
        Rs2NpcModel gone = mock(Rs2NpcModel.class);
        stubNpcLookup(gone);

        assertFalse(invokeClickNpc(new TemporossNpcSnapshot(8643, 7, null, TETHER_LOCATION, "Fire"), "Douse"));
        verify(gone, never()).click(any(String.class));
    }

    private void stubObjectLookup(Rs2TileObjectModel result) throws Exception
    {
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.where(any())).thenReturn(query);
        when(query.nearest()).thenAnswer(invocation -> {
            lookupThreads.add(Thread.currentThread());
            return result;
        });
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        swapMicrobotField("rs2TileObjectCache", cache);
    }

    private void stubNpcLookup(Rs2NpcModel result) throws Exception
    {
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.where(any())).thenReturn(query);
        when(query.nearest()).thenAnswer(invocation -> {
            lookupThreads.add(Thread.currentThread());
            return result;
        });
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);
        swapMicrobotField("rs2NpcCache", cache);
    }

    private static boolean invokeClickObject(TemporossObjectSnapshot snapshot, String action) throws Exception
    {
        Method method = TemporossScript.class.getDeclaredMethod("clickObject", TemporossObjectSnapshot.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, snapshot, action);
    }

    private static boolean invokeClickNpc(TemporossNpcSnapshot snapshot, String action) throws Exception
    {
        Method method = TemporossScript.class.getDeclaredMethod("clickNpc", TemporossNpcSnapshot.class, String.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, snapshot, action, false);
    }

    private void swapMicrobotField(String name, Object value) throws Exception
    {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        restore.add(new Object[] {name, field.get(null)});
        field.set(null, value);
    }
}
