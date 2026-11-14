package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PohTimingTest {
    @AfterEach void reset() { Thread.interrupted(); MaxPohSupport.configure(null,null,null); }

    @Test void clientTickGateNeverBlocksAndResetsWhenLeavingTheHouse() {
        var timing=new PohTickTiming();
        assertFalse(timing.hold(false,0));
        assertTrue(timing.hold(true,1000));
        assertTrue(timing.hold(true,2199));
        assertFalse(timing.hold(true,2500));
        timing.action(3000);
        assertTrue(timing.hold(true,3149));
        assertFalse(timing.hold(true,3350));
        assertFalse(timing.hold(false,4000));
        assertTrue(timing.hold(true,5000));
        assertTrue(timing.hold(true,6199));
    }

    @Test void requestResetRequiresANewLoadingPauseWithoutAnObservedOutsideTick() {
        var timing=new PohTickTiming();
        assertTrue(timing.hold(true,1000));
        assertFalse(timing.hold(true,2500));
        timing.reset();
        assertTrue(timing.hold(true,3000));
        assertTrue(timing.hold(true,4199));
        assertFalse(timing.hold(true,4500));
    }

    @Test void outdoorPresenceChecksNeverTouchTheHouseObjectCache() {
        Client client=mock(Client.class);WorldView view=mock(WorldView.class);Scene scene=mock(Scene.class);
        when(client.isClientThread()).thenReturn(true);when(client.getTopLevelWorldView()).thenReturn(view);when(view.getScene()).thenReturn(scene);
        MaxPohSupport.configure(client,mock(EfficientWalkerConfig.class),null);
        try(var dro=mockStatic(Microbot.class)) {
            for(int i=0;i<100;i++)assertFalse(MaxPohSupport.insideHouse());
            dro.verify(Microbot::getRs2TileObjectCache,never());
            dro.verify(Microbot::getClientThread,never());
        }
    }

    @Test void freshLoadingAndActionDelaysStayWithinTheirSeparateRanges() {
        Set<Integer> loads=new HashSet<>(),actions=new HashSet<>();
        for(int i=0;i<100;i++) {
            int load=MaxPohSupport.pauseMillis(true),action=MaxPohSupport.pauseMillis(false);
            assertTrue(load>=1200&&load<=1500);assertTrue(action>=150&&action<=350);
            loads.add(load);actions.add(action);
        }
        assertTrue(loads.size()>1);assertTrue(actions.size()>1);
    }

    @Test void legacyFurnitureDoesNotRequireMaxPohButBothRefuseClientThreadAndCancellation() {
        Client client=mock(Client.class);
        EfficientWalkerConfig config=mock(EfficientWalkerConfig.class);
        when(config.useTeleportations()).thenReturn(true);
        MaxPohSupport.configure(client,config,null);
        try(var arbiter=mockStatic(InputArbiter.class)) {
            var legacy=new MaxPohSupport.Execution(false);
            var max=new MaxPohSupport.Execution(true);
            assertTrue(legacy.active());assertFalse(max.active());
            when(config.useMaxPoh()).thenReturn(true);assertTrue(max.active());
            when(client.isClientThread()).thenReturn(true);
            assertFalse(legacy.pause(true));assertFalse(max.pause(false));
            assertFalse(MaxPohSupport.executeFacility(JewelleryBox.GRAND_EXCHANGE));
            when(client.isClientThread()).thenReturn(false);
            Thread.currentThread().interrupt();assertFalse(legacy.pause(false));assertTrue(Thread.currentThread().isInterrupted());
            Thread.interrupted();
            when(config.useTeleportations()).thenReturn(false);assertFalse(legacy.active());assertFalse(max.active());
        }
    }

    @Test void legacyNexusAndBoxKeepExactDestinationsAndGates() {
        var nexus=net.runelite.client.plugins.microbot.droefficientwalker.support.poh.NexusPortal.CATHERBY;
        var exit=MaxPohSupport.facilityExit(nexus);
        assertEquals("NEXUS",exit.family);assertEquals(nexus.getText(),exit.label);assertEquals(nexus.getDestination(),exit.point);
        exit=MaxPohSupport.facilityExit(JewelleryBox.TEARS_OF_GUTHIX);
        assertEquals("BOX",exit.family);assertEquals(JewelleryBox.TEARS_OF_GUTHIX.getDestination(),exit.point);
        assertEquals(net.runelite.api.QuestState.FINISHED,exit.gates.getQuests().get(net.runelite.api.Quest.TEARS_OF_GUTHIX));
    }
}
