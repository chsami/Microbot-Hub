package net.runelite.client.plugins.microbot.droslayer;

import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;

/** Reads the active assignment exactly as RuneLite's native Slayer plugin does. */
public final class DroSlayerWildernessTaskReader
{
    public static final int KRYSTILIA_MASTER_ID = 7;
    private static final int BOSSES_TASK_ID = 98;

    private DroSlayerWildernessTaskReader() {}

    /** Normal tasks retain Rs2Slayer behavior; boss tasks resolve the selected boss subtable. */
    public static String getTaskName()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            getTaskName(Microbot.getClient(), Rs2Slayer::getSlayerTask)).orElse(null);
    }

    public static int getActiveMaster()
    {
        return Microbot.getVarbitValue(VarbitID.SLAYER_MASTER);
    }

    public static boolean isKrystiliaAssignment()
    {
        return isKrystiliaAssignment(getActiveMaster());
    }

    static boolean isKrystiliaAssignment(int activeMaster)
    {
        return activeMaster == KRYSTILIA_MASTER_ID;
    }

    /** Kept independent of Dro globals so DB selection can be regression tested. */
    static String getTaskName(Client client, Supplier<String> ordinaryTaskName)
    {
        if (client == null) return null;
        int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        if (taskId == 0) return null;
        if (taskId != BOSSES_TASK_ID) return ordinaryTaskName.get();

        int bossId = client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID);
        if (bossId <= 0) return null;
        List<Integer> rows = client.getDBRowsByValue(
            DBTableID.SlayerTaskSublist.ID,
            DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
            0, bossId);
        if (rows == null || rows.isEmpty()) return null;
        Object[] taskRows = client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
        if (taskRows == null || taskRows.length == 0 || !(taskRows[0] instanceof Integer)) return null;
        Object[] names = client.getDBTableField((Integer) taskRows[0], DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
        if (names == null || names.length == 0 || !(names[0] instanceof String)) return null;
        String name = (String) names[0];
        return name.trim().isEmpty() ? null : name;
    }
}
