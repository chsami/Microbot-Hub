/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** First frames of waves 37 and 45 from the 2026-10-20 pure run stand in for the
 * predicted spawns: both waves appeared beside a tile where the gap was spent eating. */
public class SpawnTransitionReplayTest {
    private JsonArray json(String name)throws Exception {
        try(Reader r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name),name),StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonArray();
        }
    }
    private static Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private Snapshot scene(int tick)throws Exception {
        for(JsonElement e:json("pure-070.json")) {
            JsonObject f=e.getAsJsonObject();if(f.get("tick").getAsInt()!=tick)continue;
            int[][] flags=new int[104][104];
            for(JsonElement r:json(f.get("geometry").getAsString())) {
                JsonArray row=r.getAsJsonArray();
                for(int y=row.get(1).getAsInt();y<=row.get(2).getAsInt();y++)flags[row.get(0).getAsInt()][y]=row.get(3).getAsInt();
            }
            // Predicted spawns carry no attack clock or health bar.
            List<Mob> mobs=new ArrayList<>();
            for(JsonElement n:f.getAsJsonArray("mobs")) {
                JsonObject m=n.getAsJsonObject();
                mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),tile(m.getAsJsonArray("tile")),
                    m.get("size").getAsInt(),-1,-1,-1,Protection.valueOf(m.get("style").getAsString()),true));
            }
            return new Snapshot(tick,tile(f.getAsJsonArray("player")),new CollisionGrid(flags),mobs,100,
                f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.NONE).atWave(f.get("wave").getAsInt());
        }
        throw new AssertionError("Missing fixture tick "+tick);
    }

    @Test public void wave45PauseTileBesideTheTokXilSpawnIsNotSafe()throws Exception {
        Snapshot spawned=scene(6374);
        assertEquals(new Tile(57,65),spawned.player());
        assertFalse(SpawnTransition.pauseSafe(spawned));
    }
    @Test public void wave37PauseTileBesideTheKetZekSpawnIsNotSafe()throws Exception {
        Snapshot spawned=scene(4931);
        assertEquals(new Tile(33,27),spawned.player());
        assertFalse(SpawnTransition.pauseSafe(spawned));
    }
    @Test public void openGridSeparatesAdjacentAndDistantSpawns() {
        CollisionGrid open=new CollisionGrid(new int[104][104]);
        List<Mob> spawn=List.of(new Mob(1,Kind.MAGER,new Tile(22,20),5,-1,-1,-1,Protection.MAGIC,true),
            new Mob(2,Kind.RANGER,new Tile(20,26),3,-1,-1,-1,Protection.RANGE,true));
        assertFalse(SpawnTransition.pauseSafe(new Snapshot(1,new Tile(20,20),open,spawn,100,true,5,Protection.NONE)));
        assertTrue(SpawnTransition.pauseSafe(new Snapshot(1,new Tile(90,90),open,spawn,100,true,5,Protection.NONE)));
    }
    @Test public void oneCoverableSpawnStyleStillAllowsThePause() {
        CollisionGrid open=new CollisionGrid(new int[104][104]);
        List<Mob> spawn=List.of(new Mob(1,Kind.RANGER,new Tile(30,20),3,-1,-1,-1,Protection.RANGE,true));
        assertTrue(SpawnTransition.pauseSafe(new Snapshot(1,new Tile(20,20),open,spawn,100,true,5,Protection.NONE)));
    }
    @Test public void unknownPredictionKeepsExistingBehaviour() {
        assertTrue(SpawnTransition.pauseSafe(null));
    }
}
