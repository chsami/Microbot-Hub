/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Attack-range entry from the supplied 0.3.17 run, including the 39-hit return. */
public class MageTimingRegressionTest {
    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private Snapshot recorded(int tick)throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=resource("mage-late-collision.csv")) {
            r.readLine();String line;while((line=r.readLine())!=null) {
                String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);
            }
        }
        try(Reader r=resource("mage-late-frames.json")) {
            for(JsonElement e:new JsonParser().parse(r).getAsJsonArray()) {
                JsonObject f=e.getAsJsonObject();if(f.get("tick").getAsInt()!=tick)continue;
                List<Mob> mobs=new ArrayList<>();
                for(JsonElement value:f.getAsJsonArray("mobs")) {
                    JsonObject m=value.getAsJsonObject();mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),
                        tile(m.getAsJsonArray("tile")),m.get("size").getAsInt(),m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),
                        m.get("attackTick").getAsInt(),Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
                }
                return new Snapshot(tick,tile(f.getAsJsonArray("player")),new CollisionGrid(flags),mobs,
                    100,f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.NONE);
            }
        }
        throw new AssertionError("Missing recorded tick "+tick);
    }
    @Test public void recordedOpeningRunArmsMagicBeforeWalkingIntoItsAttackLane()throws Exception {
        Snapshot s=recorded(73);
        assertEquals(Protection.NONE,new TickProtection().choose(s,false,Protection.NONE,Protection.NONE).protection);
        assertEquals(Protection.MAGIC,MinimapMovement.routeProtection(s,new Tile(45,64),Protection.NONE));
    }
    @Test public void recordedMeleeGapReturnRequiresMagicBeforeTheMinimapClick()throws Exception {
        Snapshot s=recorded(230);
        assertEquals(new Tile(55,54),s.player());
        assertEquals(Protection.MAGIC,MinimapMovement.routeProtection(s,new Tile(62,60),Protection.MELEE));
    }
    @Test public void approachingMageGetsProtectionBeforeItsFirstLaunchEvenWithDelayedTick()throws Exception {
        Snapshot f=recorded(197);
        Mob mage=f.mobs().stream().filter(m->m.kind()==Kind.MAGER).findFirst().get().at(new Tile(34,56));
        Snapshot s=new Snapshot(194,f.player(),f.grid(),List.of(mage),100,false,7,Protection.NONE);
        assertEquals(0,CombatPlanner.threats(s.grid(),mage,s.player(),Protection.NONE));
        Mob oneStep=CombatPlanner.advance(s.grid(),s.mobs(),s.player(),Protection.NONE).get(0);
        assertEquals(0,CombatPlanner.threats(s.grid(),oneStep,s.player(),Protection.NONE));
        assertEquals(Protection.MAGIC,new TickProtection().choose(s,false,Protection.RANGE,Protection.NONE).protection);
    }
}
