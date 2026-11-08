package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DismissNpcEventTest {

    private static final String OLD_MAN = "Mysterious Old Man";

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Npc> rs2Npc;
    private MockedStatic<Global> global;
    private Rs2NpcQueryable query;
    private net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel legacyModel;
    private DismissNpcEvent event;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        rs2Npc = mockStatic(Rs2Npc.class);
        global = mockStatic(Global.class);

        query = mock(Rs2NpcQueryable.class);
        when(query.where(any())).thenReturn(query);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);

        legacyModel = mock(net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel.class);
        event = new DismissNpcEvent(mock(EventDismissConfig.class));
    }

    @AfterEach
    void tearDown() {
        global.close();
        rs2Npc.close();
        microbot.close();
    }

    @Test
    void skipsDismissWhenNpcLeavesDuringDelay() {
        Rs2NpcModel stale = npcNamed(OLD_MAN);
        rs2Npc.when(Rs2Npc::getRandomEventNPC).thenReturn(legacyModel).thenReturn(null);
        when(query.nearest()).thenReturn(stale);

        assertTrue(event.execute());

        verify(stale, never()).click(anyString());
    }

    @Test
    void skipsDismissWhenNpcIsReplacedDuringDelay() {
        Rs2NpcModel stale = npcNamed(OLD_MAN);
        Rs2NpcModel other = npcNamed(null);
        rs2Npc.when(Rs2Npc::getRandomEventNPC).thenReturn(legacyModel);
        when(query.nearest()).thenReturn(stale, other);

        assertTrue(event.execute());

        verify(stale, never()).click(anyString());
        verify(other, never()).click(anyString());
    }

    @Test
    void dismissesTheNpcFetchedAfterTheDelay() {
        Rs2NpcModel stale = npcNamed(OLD_MAN);
        Rs2NpcModel fresh = npcNamed(OLD_MAN);
        rs2Npc.when(Rs2Npc::getRandomEventNPC).thenReturn(legacyModel);
        when(query.nearest()).thenReturn(stale, fresh);

        event.execute();

        verify(fresh).click("Dismiss");
        verify(stale, never()).click(anyString());
    }

    private static Rs2NpcModel npcNamed(String name) {
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getName()).thenReturn(name);
        return npc;
    }
}
