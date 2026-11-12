package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.QuestState;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MaxPohSupportTest {
    private final WorldPoint landing = new WorldPoint(3410,3324,0);
    private MaxPohSupport.Exit ring() { return new MaxPohSupport.Exit("RING","BIP",landing,null); }
    @AfterEach public void reset() { MaxPohSupport.configure(null,null,null); }

    @Test public void defaultOffPublishesNothing() {
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);
        assertFalse(config.useMaxPoh());
        MaxPohSupport.configure(mock(Client.class),config,null);
        MaxPohSupport.refresh();
        assertTrue(MaxPohSupport.routes().isEmpty());
        assertEquals(0,MaxPohSupport.fingerprint());
    }

    @Test public void recorderFurnitureLearnsFacilitiesWithoutCoordinates() {
        Set<String> result = new HashSet<>();
        MaxPohSupport.detect(29229,"Spiritual Fairy Tree",result);
        MaxPohSupport.detect(29156,"Ornate Jewellery Box",result);
        MaxPohSupport.detect(33410,"Portal Nexus",result);
        MaxPohSupport.detect(33417,"Digsite Pendant",result);
        MaxPohSupport.detect(31554,"Obelisk",result);
        assertEquals(Set.of("TREE","RING","BOX:ORNATE","NEXUS","DIGSITE","OBELISK"),result);
        assertEquals(result,MaxPohSupport.decode(String.join(",",result)));
    }



    @Test public void bankLoadoutWithdrawsHouseEntryAndEquipsStaff() throws Exception {
        try (var micro = mockStatic(Microbot.class)) {
            MaxPohSupport.Leg leg = new MaxPohSupport.Leg(ring(),MaxPohSupport.Entry.TABLET);
            Method convert = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "toTransportEdge",Transport.class);
            convert.setAccessible(true);
            Rs2TransportEdge edge = (Rs2TransportEdge)convert.invoke(null,leg);
            Method supplies = WalkerTestReflection.declaredMethod(Rs2WalkerBankingPlanner.class, "getMissingTransportEdgeLoadout",
                List.class,java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,
                java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,java.util.function.IntPredicate.class);
            supplies.setAccessible(true);
            java.util.function.IntUnaryOperator bank = id -> id == 8013 || id == 772 ? 1 : 0;
            java.util.function.IntUnaryOperator empty = id -> 0;
            Rs2TransportLoadout loadout = (Rs2TransportLoadout)supplies.invoke(null,List.of(edge),bank,empty,empty,bank,
                (java.util.function.IntPredicate)id -> false);
            assertTrue(loadout.isSatisfiable());
            assertEquals(Map.of(8013,1,772,1),loadout.getWithdrawals());
            assertEquals(List.of(772),loadout.getEquipmentItemIds());
            assertTrue(edge.isTeleport());
            assertEquals(28,edge.getDuration());
        }
    }

    @Test public void wornStaffNeedsNoDuplicateWithdrawal() throws Exception {
        try (var micro = mockStatic(Microbot.class)) {
            MaxPohSupport.Leg leg = new MaxPohSupport.Leg(ring(),MaxPohSupport.Entry.CAPE);
            Method convert = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "toTransportEdge",Transport.class);
            convert.setAccessible(true);
            Rs2TransportEdge edge = (Rs2TransportEdge)convert.invoke(null,leg);
            Method supplies = WalkerTestReflection.declaredMethod(Rs2WalkerBankingPlanner.class, "getMissingTransportEdgeLoadout",
                List.class,java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,
                java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,java.util.function.IntPredicate.class);
            supplies.setAccessible(true);
            java.util.function.IntUnaryOperator worn = id -> id == 9790 || id == 9084 ? 1 : 0;
            java.util.function.IntUnaryOperator empty = id -> 0;
            Rs2TransportLoadout loadout = (Rs2TransportLoadout)supplies.invoke(null,List.of(edge),empty,empty,worn,empty,
                (java.util.function.IntPredicate)id -> id == 9790 || id == 9084);
            assertTrue(loadout.isSatisfiable());
            assertTrue(loadout.getWithdrawals().isEmpty());
        }
    }

    @Test public void externalLandingCannotBeConfirmedWhileStillInHouse() {
        assertFalse(MaxPohSupport.landed(landing,landing,true));
        assertFalse(MaxPohSupport.landed(landing,landing.dx(7),false));
        assertFalse(MaxPohSupport.landed(landing,new WorldPoint(3410,3324,1),false));
        assertTrue(MaxPohSupport.landed(landing,landing.dx(2),false));
    }

    @Test public void houseLegIsNotOfferedBesideItsOwnLanding() {
        WorldPoint civitas = new WorldPoint(1681,3133,0);
        MaxPohSupport.Leg leg = new MaxPohSupport.Leg(
            new MaxPohSupport.Exit("NEXUS","Civitas illa Fortis",civitas,null),MaxPohSupport.Entry.TABLET);
        assertTrue(MaxPohSupport.landsNear(leg,new WorldPoint(1680,3133,0)));
        assertTrue(MaxPohSupport.landsNear(leg,civitas.dx(MaxPohSupport.NEAR_LANDING_TILES)));
        assertFalse(MaxPohSupport.landsNear(leg,civitas.dx(MaxPohSupport.NEAR_LANDING_TILES + 1)));
        assertFalse(MaxPohSupport.landsNear(leg,new WorldPoint(1680,3133,1)));
        assertFalse(MaxPohSupport.landsNear(leg,new WorldPoint(1365,3319,0)));
    }

    @Test public void houseEntryIncludesMoreTimeThanAlreadyBeingInside() {
        MaxPohSupport.Exit exit = new MaxPohSupport.Exit("NEXUS","Catherby",new WorldPoint(2804,3434,0),null);
        try (var player = mockStatic(Rs2Player.class)) {
            WorldPoint liveHouse = new WorldPoint(10910,13717,0);
            player.when(Rs2Player::getWorldLocation).thenReturn(liveHouse);
            MaxPohSupport.Leg portal = new MaxPohSupport.Leg(exit,MaxPohSupport.Entry.CAPE);
            MaxPohSupport.Leg inside = new MaxPohSupport.Leg(exit,MaxPohSupport.Entry.INSIDE);
            assertNull(portal.getOrigin());
            assertNull(inside.getOrigin(), "Do not store instance or template coordinates");
            assertEquals(TransportType.TELEPORTATION_ITEM,portal.getType());
            assertTrue(RouteTimeCost.transport(portal) > RouteTimeCost.transport(inside));
        }
    }

    @Test public void nexusShortcutIsReadFromDestinationRow() {
        assertEquals("G",MaxPohSupport.shortcut("<col=ffffff>G</col>: Catherby"));
        assertEquals("G",MaxPohSupport.shortcut("<col=ff981f>G</col>: Catherby"));
        assertNull(MaxPohSupport.shortcut("Catherby"));
    }

    @Test public void nexusRowMustEqualTheLabelNotMerelyContainIt() {
        assertTrue(MaxPohSupport.nexusRowMatches("<col=ffffff>F</col>: Troll Stronghold","Troll Stronghold"));
        assertFalse(MaxPohSupport.nexusRowMatches("<col=ffffff>D</col>: West Ardougne","Ardougne"));
        assertFalse(MaxPohSupport.nexusRowMatches("<col=ffffff>4</col>: Camelot","Troll Stronghold"));
        assertTrue(MaxPohSupport.nexusRowMatches("<col=ffffff>G</col> Catherby", "Catherby"), "Shortcuts may be whitespace separated in the newer client");
        assertTrue(MaxPohSupport.nexusRowMatches("<col=ffffff>G</col>: Catherby", "Catherby"));
        assertTrue(MaxPohSupport.nexusRowMatches("Troll Stronghold","Troll Stronghold"), "An exact label without a shortcut remains clickable");
        assertTrue(MaxPohSupport.nexusRowMatches("<col=ff981f>G</col>: Catherby","Catherby"));
        assertFalse(MaxPohSupport.nexusRowMatches("Catherby Teleport","Catherby"));
        assertFalse(MaxPohSupport.nexusRowMatches("<str><col=ffffff>F</col>: Troll Stronghold</str>","Troll Stronghold"));
    }

    @Test public void nexusRowIsOnlyReturnedFromTheOpenNexusWithOneExactMatch() {
        Client client = mock(Client.class);
        MaxPohSupport.configure(client,mock(EfficientWalkerConfig.class),null);
        Widget root = mock(Widget.class), camelot = mock(Widget.class), west = mock(Widget.class), ardougne = mock(Widget.class);
        when(camelot.getText()).thenReturn("<col=ffffff>4</col>: Camelot");
        when(west.getText()).thenReturn("<col=ffffff>D</col>: West Ardougne");
        when(ardougne.getText()).thenReturn("<col=ffffff>5</col>: Ardougne");
        when(root.getDynamicChildren()).thenReturn(new Widget[]{camelot,west,ardougne});
        assertNull(MaxPohSupport.nexusRow("Ardougne"), "Closed Nexus has no rows");
        when(client.getWidget(17,0)).thenReturn(root);
        assertSame(ardougne,MaxPohSupport.nexusRow("Ardougne"));
        assertNull(MaxPohSupport.nexusRow("Troll Stronghold"), "A destination missing from the Nexus never falls back to another row");
        when(root.getDynamicChildren()).thenReturn(new Widget[]{ardougne,ardougne});
        assertSame(ardougne,MaxPohSupport.nexusRow("Ardougne"));
    }

    @Test public void jewelleryBoxSelectionMustBeExactNotSubstring() {
        Client client = mock(Client.class);
        MaxPohSupport.configure(client,mock(EfficientWalkerConfig.class),null);
        Widget root = mock(Widget.class), wrong = mock(Widget.class), right = mock(Widget.class);
        when(client.getWidget(590,0)).thenReturn(root);
        when(root.getDynamicChildren()).thenReturn(new Widget[]{wrong,right});
        when(wrong.getText()).thenReturn("<col=ffffff>1</col>: Grand Exchange rooftop");
        when(right.getText()).thenReturn("<col=ffffff>2</col> Grand Exchange");
        assertSame(right, MaxPohSupport.exitWidget(590,"Grand Exchange"));
    }

    @Test public void disabledDestinationIsSkippedInNestedMenu() {
        Client client = mock(Client.class);
        MaxPohSupport.configure(client,mock(EfficientWalkerConfig.class),null);
        Widget root = mock(Widget.class), disabled = mock(Widget.class), allowed = mock(Widget.class);
        when(client.getWidget(187,0)).thenReturn(root);
        when(root.getDynamicChildren()).thenReturn(new Widget[]{disabled,allowed});
        when(disabled.getText()).thenReturn("<str>Catherby</str>");
        when(allowed.getText()).thenReturn("<col=ffffff>G</col>: Catherby");
        assertSame(allowed,MaxPohSupport.exitWidget(187,"Catherby"));
    }



    @Test public void realPlannerTreatsHouseAsOneExternalTeleportLeg() throws Exception {
        try (var micro = mockStatic(Microbot.class)) {
            MaxPohSupport.Leg leg = new MaxPohSupport.Leg(new MaxPohSupport.Exit("NEXUS","Catherby",
                new WorldPoint(2804,3434,0),null),MaxPohSupport.Entry.TABLET);
            var planner = new net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathfinderConfig(
                net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.SplitFlagMap.fromResources(),
                new HashMap<>(),List.of(),null,null);
            Field teleports = WalkerTestReflection.declaredField(planner.getClass(), "usableTeleports");
            teleports.setAccessible(true); ((Set<Transport>)teleports.get(planner)).add(leg);
            Field cutoff = WalkerTestReflection.declaredField(planner.getClass(), "calculationCutoffMillis");
            cutoff.setAccessible(true); cutoff.setLong(planner,2000);
            Method plan = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,planner.getClass());
            plan.setAccessible(true);
            WorldPoint start = new WorldPoint(3210,3424,0);
            Rs2RouteResult result = (Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(start,leg.getDestination()),planner);
            assertTrue(result.isTargetReached(0), result.getTerminationReason()+" "+result.getPath());
            assertEquals(1,result.getTransportSteps().size());
            assertEquals(start,result.getTransportSteps().get(0).getFrom());
            assertEquals(leg.getDestination(),result.getTransportSteps().get(0).getTo());
            assertEquals("Max POH TABLET -> NEXUS: Catherby",result.getTransportSteps().get(0).getTransport().get().getDisplayInfo());
        }
    }

    @Test public void outsideSnapshotOffersOnlyConfiguredNexusDestinations() throws Exception {
        Client client = mock(Client.class);
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);
        when(config.useMaxPoh()).thenReturn(true);
        when(client.isClientThread()).thenReturn(true);
        MaxPohSupport.configure(client,config,null);
        Field known = WalkerTestReflection.declaredField(MaxPohSupport.class, "known"); known.setAccessible(true);
        known.set(null,Set.of("NEXUS","NEXUS:CATHERBY"));
        Field network = WalkerTestReflection.declaredField(MaxPohSupport.class, "networkDestinations"); network.setAccessible(true);
        network.set(null,List.of());
        try (var micro = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class); var magic = mockStatic(Rs2Magic.class)) {
            MaxPohSupport.refresh();
            assertEquals(2,MaxPohSupport.routes().size());
            assertTrue(MaxPohSupport.routes().stream().allMatch(t -> t.getDisplayInfo().endsWith("NEXUS: Catherby")));
        }
    }

    @Test public void disabledWildernessExcludesPohObelisk() throws Exception {
        Client client = mock(Client.class);
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);
        when(config.useMaxPoh()).thenReturn(true);
        when(client.isClientThread()).thenReturn(true);
        MaxPohSupport.configure(client,config,null);
        Field known = WalkerTestReflection.declaredField(MaxPohSupport.class, "known"); known.setAccessible(true);
        known.set(null,Set.of("OBELISK"));
        Field network = WalkerTestReflection.declaredField(MaxPohSupport.class, "networkDestinations"); network.setAccessible(true);
        network.set(null,List.of(new Transport(null,new WorldPoint(3156,3620,0),"1: Level 13 Wilderness",
            TransportType.WILDERNESS_OBELISK,true,8)));
        when(client.getBoostedSkillLevel(any())).thenReturn(99);
        try (var micro = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class); var magic = mockStatic(Rs2Magic.class)) {
            MaxPohSupport.refresh(); assertTrue(MaxPohSupport.routes().isEmpty());
            when(config.allowWildernessTravel()).thenReturn(true);
            when(config.useWildernessObelisks()).thenReturn(true);
            MaxPohSupport.refresh(); assertEquals(2,MaxPohSupport.routes().size());
            assertFalse(config.useWildernessTeleports()); // Obelisks have their own existing toggle.
        }
    }

    @Test public void existingRestorationTracksTheWholeCompositeRingLeg() {
        FairyRingEquipmentRestore restore = new FairyRingEquipmentRestore();
        WorldPoint origin = new WorldPoint(3210,3424,0);
        List<Integer> restored = new ArrayList<>();
        assertFalse(restore.tick(origin,landing,origin,4151,1187,1,(shield,id) -> restored.add(id)));
        assertFalse(restore.tick(origin,landing,new WorldPoint(10910,13717,0),772,1187,2,
            (shield,id) -> restored.add(id)));
        assertTrue(restore.tick(origin,landing,landing,772,1187,3,(shield,id) -> restored.add(id)));
        assertEquals(List.of(4151),restored);
        assertFalse(restore.tick(origin,landing,landing,4151,1187,4,(shield,id) -> restored.add(id)));
    }

    @Test public void outsideArrivalCanEnterOnlyTheCurrentAccountHousePortal() {
        HouseLocation house = HouseLocation.RIMMINGTON;
        assertTrue(MaxPohSupport.currentEntrance(house.getPortalId(),house.getPortalLocation(),house));
        assertFalse(MaxPohSupport.currentEntrance(HouseLocation.TAVERLY.getPortalId(),HouseLocation.TAVERLY.getPortalLocation(),house));
        assertFalse(MaxPohSupport.currentEntrance(house.getPortalId(),house.getPortalLocation().dx(50),house));
        assertFalse(MaxPohSupport.currentEntrance(house.getPortalId(),house.getPortalLocation(),null));
    }


    @Test public void bankedCapeIsAnEntrySupplyWithoutEquippingIt() throws Exception {
        MaxPohSupport.Leg leg=new MaxPohSupport.Leg(new MaxPohSupport.Exit("BOX","Grand Exchange",landing,null),MaxPohSupport.Entry.CAPE);
        Method convert=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "toTransportEdge",Transport.class);convert.setAccessible(true);
        Rs2TransportEdge edge=(Rs2TransportEdge)convert.invoke(null,leg);
        Method supplies=WalkerTestReflection.declaredMethod(Rs2WalkerBankingPlanner.class, "getMissingTransportEdgeLoadout",
            List.class,java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,
            java.util.function.IntUnaryOperator.class,java.util.function.IntUnaryOperator.class,java.util.function.IntPredicate.class);
        supplies.setAccessible(true);
        java.util.function.IntUnaryOperator bank=id->id==9790?1:0,empty=id->0;
        Rs2TransportLoadout loadout=(Rs2TransportLoadout)supplies.invoke(null,List.of(edge),bank,empty,empty,bank,
            (java.util.function.IntPredicate)id->false);
        assertTrue(loadout.isSatisfiable());assertEquals(Map.of(9790,1),loadout.getWithdrawals());
        assertTrue(loadout.getEquipmentItemIds().isEmpty());
    }
}
