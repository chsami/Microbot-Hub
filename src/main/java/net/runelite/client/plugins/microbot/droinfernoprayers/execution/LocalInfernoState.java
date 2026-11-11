package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.NpcSpawnDiagnostics;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.State;

public final class LocalInfernoState extends State
{
    private static final int UNLOADED = 0x1000000;
    private final BiFunction<Integer, Integer, int[]> scene;

    public LocalInfernoState(Client client, NpcSpawnDiagnostics diagnostics)
    {
        this(client, diagnostics, null);
    }

    LocalInfernoState(Client client, NpcSpawnDiagnostics diagnostics,
        BiFunction<Integer, Integer, int[]> scene)
    {
        super(client, diagnostics);
        this.scene = scene == null ? tiles::scene : scene;
    }

    @Override
    public int collisionFlags(int tx, int ty)
    {
        WorldView world = client.getTopLevelWorldView();
        if (world == null) return UNLOADED;
        CollisionData[] maps = world.getCollisionMaps();
        int plane = world.getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
            return UNLOADED;
        int[][] flags = maps[plane].getFlags();
        int[] tile = scene.apply(tx, ty);
        if (flags == null || tile == null || tile.length != 2 || tile[0] < 0
            || tile[0] >= flags.length || flags[tile[0]] == null || tile[1] < 0
            || tile[1] >= flags[tile[0]].length)
            return UNLOADED;
        return flags[tile[0]][tile[1]];
    }
}
