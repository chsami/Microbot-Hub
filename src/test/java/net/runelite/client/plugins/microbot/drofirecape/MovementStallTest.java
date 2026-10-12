/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.MovementAck;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MovementStallTest {
    private final Tile cover=new Tile(54,36),west=new Tile(53,36);
    private MovementAck failed() {
        MovementAck ack=new MovementAck();ack.sent(cover,west,100);
        assertEquals(MovementAck.Result.RETRY_MINIMAP,ack.observe(cover,103));
        ack.sent(cover,west,103);
        assertEquals(MovementAck.Result.RETRY_MINIMAP,ack.observe(cover,106));
        ack.sent(cover,west,106);
        assertEquals(MovementAck.Result.FAILED,ack.observe(cover,109));
        assertFalse(ack.pending());return ack;
    }
    @Test public void repeatedClicksWithoutDisplacementNeverAcknowledgeProgress() {
        MovementAck ack=failed();
        assertTrue(ack.coolingDown(cover,west,109));assertTrue(ack.coolingDown(cover,west,118));
        assertFalse(ack.coolingDown(cover,west,119));
        assertTrue(ack.failedRecently(cover,west,119));
    }
    @Test public void failedStepDoesNotBlockAnotherDestinationOrOrigin() {
        MovementAck ack=failed();
        assertFalse(ack.coolingDown(cover,cover.add(0,1),110));
        assertFalse(ack.coolingDown(cover.add(0,1),west,110));
    }
    @Test public void actualMovementProgressExtendsAcknowledgementWindow() {
        MovementAck ack=new MovementAck();Tile to=cover.add(-5,0);
        ack.sent(cover,to,100,List.of(cover,west,cover.add(-2,0),cover.add(-3,0),cover.add(-4,0),to));
        assertEquals(MovementAck.Result.PROGRESSED,ack.observe(west,103));
        assertEquals(MovementAck.Result.WAITING,ack.observe(west,105));
        assertEquals(MovementAck.Result.ARRIVED,ack.observe(to,106));assertFalse(ack.pending());
    }
    @Test public void sceneResetClearsFailureHistory() {
        MovementAck ack=failed();ack.reset();
        assertFalse(ack.failedRecently(cover,west,110));
    }
}
