package net.runelite.client.plugins.microbot.droslayer;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerWildernessTaskReaderTest
{
    @Test
    public void ordinaryAssignmentsPassThroughWithoutLookingUpBosses()
    {
        Client client = mock(Client.class);
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(13);
        AtomicInteger calls = new AtomicInteger();
        assertEquals("Black dragons", DroSlayerWildernessTaskReader.getTaskName(client, () -> {
            calls.incrementAndGet(); return "Black dragons";
        }));
        assertEquals(1, calls.get());
        verify(client, never()).getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID);
    }

    @Test
    public void bossTaskUsesSelectedBossSubtableRatherThanGenericBossesId()
    {
        Client client = mock(Client.class);
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
        when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(123);
        when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 123))
            .thenReturn(Collections.singletonList(800));
        when(client.getDBTableField(800, DBTableID.SlayerTaskSublist.COL_TASK, 0))
            .thenReturn(new Object[]{DBTableID.SlayerTask.Row.SLAYER_TASK_CALLISTO});
        when(client.getDBTableField(DBTableID.SlayerTask.Row.SLAYER_TASK_CALLISTO,
            DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Callisto"});
        String name = DroSlayerWildernessTaskReader.getTaskName(client, () -> {
            fail("Boss tasks must not use Rs2Slayer's generic boss lookup"); return null;
        });
        assertEquals("Callisto", name);
        assertNull(DroSlayerWildernessTasks.forAssignedTask(name), "Boss identity is read for safe unsupported-task handling only");
        verify(client).getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 123);
        verify(client, never()).getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, 0, 98);
    }

    @Test
    public void unknownOrIncompleteBossDataNeverFallsBackToAnotherTask()
    {
        Client client = mock(Client.class);
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
        assertNull(DroSlayerWildernessTaskReader.getTaskName(client, () -> "Suqahs"));
        when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(321);
        when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 321)).thenReturn(Collections.emptyList());
        assertNull(DroSlayerWildernessTaskReader.getTaskName(client, () -> "Suqahs"));
        when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 321)).thenReturn(Collections.singletonList(801));
        when(client.getDBTableField(801, DBTableID.SlayerTaskSublist.COL_TASK, 0)).thenReturn(new Object[]{"invalid"});
        assertNull(DroSlayerWildernessTaskReader.getTaskName(client, () -> "Suqahs"));
    }

    @Test
    public void noActiveTargetDoesNotCallNormalTaskReader()
    {
        assertNull(DroSlayerWildernessTaskReader.getTaskName(mock(Client.class), () -> {
            fail("No assignment should not invoke normal lookup"); return null;
        }));
        assertNull(DroSlayerWildernessTaskReader.getTaskName(null, () -> "Suqahs"));
    }

    @Test
    public void onlyKrystiliaActiveMasterEnablesWildernessAssignment()
    {
        assertTrue(DroSlayerWildernessTaskReader.isKrystiliaAssignment(7));
        assertFalse(DroSlayerWildernessTaskReader.isKrystiliaAssignment(0));
        assertFalse(DroSlayerWildernessTaskReader.isKrystiliaAssignment(1));
        assertFalse(DroSlayerWildernessTaskReader.isKrystiliaAssignment(10));
    }
}
