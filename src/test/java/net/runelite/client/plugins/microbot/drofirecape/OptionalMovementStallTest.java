/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import net.runelite.client.plugins.microbot.drofirecape.*;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OptionalMovementStallTest {
    @Test public void wave35StoppedOneTileShortExpiresWithoutAnyRetryDispatch() {
        MovementAck ack=new MovementAck(true);
        Tile from=new Tile(62,60),goal=new Tile(57,54),stopped=new Tile(57,55);
        ack.sent(from,goal,92);
        assertEquals(MovementAck.Result.PROGRESSED,ack.observe(stopped,95));
        for(int tick=96;tick<104;tick++) {
            ack.observe(stopped,tick);
            assertTrue(ack.pending());
        }
        assertEquals(MovementAck.Result.FAILED,ack.observe(stopped,104));
        assertFalse(ack.pending());
        assertTrue(ack.coolingDown(stopped,goal,104));
        assertEquals(MovementAck.Result.NONE,ack.observe(stopped,1618));
    }

    @Test public void acknowledgedProgressStillAllowsLongRoutes() {
        MovementAck ack=new MovementAck(true);Tile from=new Tile(10,10),goal=new Tile(50,10);
        ack.sent(from,goal,1);
        for(int tick=2;tick<=40;tick++)
            assertEquals(MovementAck.Result.PROGRESSED,ack.observe(from.add(tick-1,0),tick));
        assertEquals(MovementAck.Result.ARRIVED,ack.observe(goal,41));
    }

    @Test public void expiredMovementCannotSuppressStationaryWatchdog() {
        MovementAck ack=new MovementAck(true);PureRepositionWatchdog watchdog=new PureRepositionWatchdog();
        Tile player=new Tile(57,55);ack.sent(player,new Tile(57,54),92);
        List<Mob> mobs=List.of(
            new Mob(1,Kind.MAGER,new Tile(61,45),5,-1,-1,99,Protection.MAGIC,true),
            new Mob(2,Kind.BABY,new Tile(57,56),1,-1,-1,-1,Protection.MELEE,true),
            new Mob(3,Kind.BABY,new Tile(57,57),1,-1,-1,-1,Protection.MELEE,true));
        Plan shot=new Plan(player,player,Protection.MAGIC,2,false,0,0,4,"Failed lure: kill immediately with protection");
        boolean recovered=false;
        for(int tick=93;tick<=131;tick++) {
            ack.observe(player,tick);
            Snapshot room=new Snapshot(tick,player,new CollisionGrid(new int[104][104]),mobs,100,true,5,Protection.NONE);
            recovered|=watchdog.stalled(room,shot,ack.pending(),-1);
        }
        assertTrue(recovered,"Unacknowledged shots must recover rather than wait until tick 1618");
    }
}
