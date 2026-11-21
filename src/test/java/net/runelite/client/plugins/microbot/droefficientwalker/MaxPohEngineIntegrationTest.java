package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportObjectClick;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MaxPohEngineIntegrationTest {
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
    private static final WorldPoint HOUSE = new WorldPoint(10910,13717,0);
    private static final WorldPoint GE = new WorldPoint(3164,3487,0);
    @AfterEach public void reset() { MaxPohSupport.configure(null,null,null); Thread.interrupted(); }

    @Test public void microbotSettingsAreReadableWithoutThePohPanelOrAnInsideTile() {
        ConfigManager settings = mock(ConfigManager.class);
        when(settings.getConfiguration("shortestpath","jewelleryBox")).thenReturn("ORNATE");
        when(settings.getConfiguration("shortestpath","fairyRing")).thenReturn("true");
        when(settings.getConfiguration("shortestpath","Nexus Teleport")).thenReturn("CATHERBY,VARROCK_GE,bad-entry");
        assertEquals(Set.of("BOX:ORNATE","RING","NEXUS:CATHERBY","NEXUS:VARROCK_GE"),
            MaxPohSupport.configuredFacilities(settings));
    }

    @Test public void realAdmissionAndCollisionPlannerConnectHouseStartToGe() throws Exception {
        for (WorldPoint start : List.of(HOUSE,new WorldPoint(2660,3305,0))) {
            Constructor<?> loaderCtor = Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader")
                .getDeclaredConstructor(ClassLoader.class);
            loaderCtor.setAccessible(true);
            ClassLoader loader = (ClassLoader)loaderCtor.newInstance(getClass().getClassLoader());
            Class<?> edgeClass = Class.forName(SHADOW + ".TransportCatalog$TransportEdge",true,loader);
            Class<?> plannerClass = Class.forName(SHADOW + ".CollisionRoutePlanner",true,loader);
            Class<?> engineClass = Class.forName(SHADOW + ".WalkerEngine",true,loader);
            Class<?> configClass = Class.forName(SHADOW + ".WalkerEngineConfig",true,loader);
            Constructor<?> edgeCtor = edgeClass.getDeclaredConstructor(WorldPoint.class,WorldPoint.class,
                String.class,String.class,int.class,List.class,List.class,List.class,List.class,List.class,boolean.class,int[].class);
            edgeCtor.setAccessible(true);
            Object edge = edgeCtor.newInstance(start,GE,"Max POH","BOX:Grand Exchange:CAPE",-1,
                List.of(),List.of(),List.of(),List.of(),List.of(),false,new int[0]);
            Method build = WalkerTestReflection.declaredMethod(plannerClass, "create",List.class,Predicate.class); build.setAccessible(true);
            Object planner = build.invoke(null,List.of(edge),(Predicate<Object>)e->true);
            Object config = Proxy.newProxyInstance(loader,new Class<?>[]{configClass},(p,m,a)->
                m.getReturnType()==boolean.class ? true : m.getReturnType()==int.class ? 30 : null);
            Constructor<?> engineCtor = engineClass.getDeclaredConstructor(Client.class,configClass,List.class,
                plannerClass,Executor.class,Executor.class); engineCtor.setAccessible(true);
            Client client=mock(Client.class);Player player=mock(Player.class);WorldView view=mock(WorldView.class);
            CollisionData collision=mock(CollisionData.class);Scene scene=mock(Scene.class);
            when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(start);
            when(player.getWorldView()).thenReturn(view);when(client.getTopLevelWorldView()).thenReturn(view);
            when(view.isTopLevel()).thenReturn(true);when(view.getId()).thenReturn(WorldView.TOPLEVEL);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            when(view.getBaseX()).thenReturn(start.getX()-50);when(view.getBaseY()).thenReturn(start.getY()-50);
            when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(50*128+64,50*128+64));
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getEnergy()).thenReturn(10000);
            when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});when(collision.getFlags()).thenReturn(new int[104][104]);
            when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[1][104][104]);
            Object engine = engineCtor.newInstance(client,config,List.of(edge),planner,
                (Executor)Runnable::run,(Executor)Runnable::run);
            Method admit = WalkerTestReflection.declaredMethod(engineClass, "canUseTransport",edgeClass); admit.setAccessible(true);
            assertEquals(true, admit.invoke(engine,edge), "Engine must admit the house hop");
            Method plan = WalkerTestReflection.declaredMethod(plannerClass, "planRoute",WorldPoint.class,Collection.class,Set.class,Set.class,Set.class);
            plan.setAccessible(true);
            Object route = plan.invoke(planner,start,List.of(GE),Set.of(),Set.of(),Set.of(edge));
            Field path = WalkerTestReflection.declaredField(route.getClass(), "path");path.setAccessible(true);
            assertEquals(List.of(start,GE),path.get(route));
            try(var micro=mockStatic(Microbot.class);var bank=mockStatic(Rs2Bank.class);
                var inventory=mockStatic(Rs2Inventory.class);var playerApi=mockStatic(Rs2Player.class)) {
                Method preview=WalkerTestReflection.declaredMethod(engineClass, "previewRouteTo",WorldPoint.class);preview.setAccessible(true);
                Method tick=WalkerTestReflection.declaredMethod(engineClass, "tick");tick.setAccessible(true);
                assertEquals(true,preview.invoke(engine,GE));
                tick.invoke(engine);tick.invoke(engine);
                Field reason=WalkerTestReflection.declaredField(engineClass, "failureMessage");reason.setAccessible(true);
                assertEquals("PREVIEW",engineClass.getMethod("getStatus").invoke(engine).toString(),
                    engineClass.getMethod("getPlanningFailure").invoke(engine)+" "+reason.get(engine));
                Field planned=WalkerTestReflection.declaredField(engineClass, "localRouteTransports");planned.setAccessible(true);
                Field global=WalkerTestReflection.declaredField(engineClass, "globalRoute");global.setAccessible(true);
                assertEquals(List.of(start,GE),global.get(engine));
                EfficientWalkerConfig facadeConfig=mock(EfficientWalkerConfig.class);when(facadeConfig.useMaxPoh()).thenReturn(true);
                NetworkTransportSupport bridge=new NetworkTransportSupport(client,facadeConfig,loader,engineClass,engine,SHADOW,
                    mock(CatalogTeleportSupport.class));
                Field active=WalkerTestReflection.declaredField(engineClass, "activeTransport");active.setAccessible(true);
                Field bridgeActive=WalkerTestReflection.declaredField(NetworkTransportSupport.class, "activeTransportField");bridgeActive.setAccessible(true);
                bridgeActive.set(bridge,active);
                Field map=WalkerTestReflection.declaredField(NetworkTransportSupport.class, "sharedCatalogEdges");map.setAccessible(true);
                MaxPohSupport.Leg leg=new MaxPohSupport.Leg(new MaxPohSupport.Exit("BOX","Grand Exchange",GE,null),MaxPohSupport.Entry.CAPE);
                ((Map<Object,Transport>)map.get(bridge)).put(edge,leg);
                for(String[] binding:new String[][]{{"transportOriginField","origin"},{"transportDestinationField","destination"}}) {
                    Field f=WalkerTestReflection.declaredField(edgeClass, binding[1]);f.setAccessible(true);
                    Field target=WalkerTestReflection.declaredField(NetworkTransportSupport.class, binding[0]);target.setAccessible(true);target.set(bridge,f);
                }
                ClientThread thread=mock(ClientThread.class);
                when(thread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
                micro.when(Microbot::getClientThread).thenReturn(thread);
                assertTrue(bridge.activePreviewUsesMaxPoh(), "Preview must include full house time even before N/O exist");
                assertSame(leg, bridge.pendingHouseLeg(), "Equipment capture must see the house hop before its worker starts");
                // A jewellery provider and a tablet can reach the same external tile.
                // Committing the cape route must retain its input/supply identity.
                Object tab=edgeCtor.newInstance(start,GE,"Max POH","BOX:Grand Exchange:TABLET",-1,
                    List.of(),List.of(),List.of(),List.of(),List.of(),false,new int[0]);
                ((Map<Object,Transport>)map.get(bridge)).put(tab,new MaxPohSupport.Leg(leg.exit,MaxPohSupport.Entry.TABLET));
                Field providers=WalkerTestReflection.declaredField(engineClass, "teleportProviders");providers.setAccessible(true);
                providers.set(engine,List.of(new SameLandingProvider()));
                Method convert=WalkerTestReflection.declaredMethod(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2PathApi.class, "toTransportEdge",Transport.class);convert.setAccessible(true);
                var typed=(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge)convert.invoke(null,leg);
                var complete=mock(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult.class);
                when(complete.getPath()).thenReturn(List.of(start,GE));
                when(complete.getSteps()).thenReturn(List.of(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteStep.transport(start,GE,typed)));
                bridge.preferCompleteRoute(GE,new CompleteRouteDiscovery.Choice("house",complete,null,
                    net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout.empty(),56));
                Method commit=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "applyCompleteRoute",WorldPoint.class,List.class);
                commit.setAccessible(true);List<Object> selected=new ArrayList<>(List.of(edge,tab));commit.invoke(bridge,GE,selected);
                assertEquals(List.of(edge),selected);
                assertEquals(List.of(),providers.get(engine));
            }
        }
    }

    private static final class SameLandingProvider {
        private WorldPoint getDestination() { return GE; }
    }




    private void executeBox(boolean alreadyInside, boolean worn, MaxPohSupport.Entry entry,
        boolean outsideArrival, boolean exitAtPortal) throws Exception {
        executeBox(alreadyInside,worn,entry,outsideArrival,exitAtPortal,false);
    }

    private void executeBox(boolean alreadyInside, boolean worn, MaxPohSupport.Entry entry,
        boolean outsideArrival, boolean exitAtPortal, boolean recoverNullHouseTile) throws Exception {
        Client client = mock(Client.class); Player player = mock(Player.class);
        WorldView view = mock(WorldView.class); Scene scene = mock(Scene.class); Tile tile = mock(Tile.class);
        GameObject portal = mock(GameObject.class), box = mock(GameObject.class), exterior = mock(GameObject.class);
        Tile exteriorTile=mock(Tile.class);
        ObjectComposition portalDef = mock(ObjectComposition.class), boxDef = mock(ObjectComposition.class), exteriorDef=mock(ObjectComposition.class);
        AtomicBoolean inside = new AtomicBoolean(alreadyInside);
        AtomicBoolean atExterior=new AtomicBoolean();
        WorldPoint expected=exitAtPortal?HouseLocation.PRIFDDINAS.getPortalLocation():GE;
        AtomicReference<WorldPoint> location = new AtomicReference<>(recoverNullHouseTile ? null
            : alreadyInside ? HOUSE : new WorldPoint(2660,3305,0));
        AtomicBoolean onClient = new AtomicBoolean();
        AtomicInteger tickCount = new AtomicInteger(1);
        when(client.getTickCount()).thenAnswer(i -> tickCount.incrementAndGet());
        when(client.isClientThread()).thenAnswer(i->onClient.get()); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION)).thenReturn(9);
        when(client.getLocalPlayer()).thenReturn(player); when(player.getWorldLocation()).thenAnswer(i->location.get());
        when(client.getTopLevelWorldView()).thenReturn(view);when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenAnswer(i->inside.get()?new Tile[][][]{{{tile}}}
            :atExterior.get()?new Tile[][][]{{{exteriorTile}}}:new Tile[0][][]);
        when(tile.getGameObjects()).thenReturn(new GameObject[]{portal,box});
        when(exteriorTile.getGameObjects()).thenReturn(new GameObject[]{exterior});
        when(exterior.getId()).thenReturn(HouseLocation.PRIFDDINAS.getPortalId());
        when(exterior.getWorldLocation()).thenReturn(HouseLocation.PRIFDDINAS.getPortalLocation());
        when(exteriorDef.getName()).thenReturn("Portal");when(exteriorDef.getActions()).thenReturn(new String[]{"Enter","Home"});
        when(portal.getId()).thenReturn(4525);when(box.getId()).thenReturn(29156);
        when(portalDef.getName()).thenReturn("Portal");when(boxDef.getName()).thenReturn("Ornate Jewellery Box");
        when(portalDef.getActions()).thenReturn(new String[]{"Enter","Lock",null,null,"Remove"});
        when(boxDef.getActions()).thenReturn(new String[]{null,"Teleport Menu","Grand Exchange",null,"Remove"});
        ClientThread thread=mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(i->{
            onClient.set(true);
            try { return ((Supplier<?>)i.getArgument(0)).get(); } finally { onClient.set(false); }
        });
        Widget root=mock(Widget.class),row=mock(Widget.class);
        when(client.getWidget(590,0)).thenReturn(root);when(root.getDynamicChildren()).thenReturn(new Widget[]{row});
        when(row.getText()).thenReturn("<col=ffffff>Q</col>: Grand Exchange");
        EfficientWalkerConfig config=mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);when(config.useMaxPoh()).thenReturn(true);
        MaxPohSupport.configure(client,config,null);
        try(var micro=mockStatic(Microbot.class);var objects=mockStatic(SceneObjects.class);
            var inv=mockStatic(Rs2Inventory.class);var equipment=mockStatic(Rs2Equipment.class);
            var travel=mockStatic(TravelEquipment.class);var clicks=mockStatic(TransportObjectClick.class);
            var widgets=mockStatic(Rs2Widget.class);var state=mockStatic(Rs2Player.class);var bank=mockStatic(Rs2Bank.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::getClient).thenReturn(client);
            state.when(Rs2Player::getWorldLocation).thenAnswer(i->location.get());
            objects.when(()->SceneObjects.convertToObjectComposition(portal)).thenReturn(portalDef);
            objects.when(()->SceneObjects.convertToObjectComposition(box)).thenReturn(boxDef);
            objects.when(()->SceneObjects.convertToObjectComposition(exterior)).thenReturn(exteriorDef);
            travel.when(()->TravelEquipment.isWearing(9790)).thenReturn(worn);
            inv.when(()->Rs2Inventory.hasItem(9790)).thenReturn(!worn);
            inv.when(()->Rs2Inventory.interact(9790,"Tele to POH")).thenAnswer(i->{inside.set(true);location.set(HOUSE);return true;});
            equipment.when(()->Rs2Equipment.interact(9790,"Tele to POH")).thenAnswer(i->{
                inside.set(!outsideArrival);atExterior.set(outsideArrival);
                location.set(outsideArrival?HouseLocation.PRIFDDINAS.getPortalLocation():HOUSE);return true;
            });
            inv.when(()->Rs2Inventory.interact(8013,"Break")).thenAnswer(i->{inside.set(true);location.set(HOUSE);return true;});
            clicks.when(()->TransportObjectClick.click(exterior,"Home")).thenAnswer(i->{inside.set(true);atExterior.set(false);location.set(HOUSE);return true;});
            clicks.when(()->TransportObjectClick.click(portal,"Enter")).thenAnswer(i->{inside.set(false);location.set(expected);return true;});
            clicks.when(()->TransportObjectClick.click(box,"Teleport Menu")).thenReturn(true);
            widgets.when(()->Rs2Widget.clickWidget(row)).thenAnswer(i->{inside.set(false);location.set(GE);return true;});
            MaxPohSupport.Leg leg=new MaxPohSupport.Leg(new MaxPohSupport.Exit(exitAtPortal?"EXIT":"BOX",
                exitAtPortal?"PRIFDDINAS":"Grand Exchange",expected,null),entry);
            assertTrue(recoverNullHouseTile ? MaxPohSupport.leaveHouse() : MaxPohSupport.execute(leg), "Must observe external landing");
            assertEquals(expected,location.get());
            bank.verifyNoInteractions();
            if(alreadyInside) { inv.verifyNoInteractions();equipment.verifyNoInteractions(); }
            else if(entry==MaxPohSupport.Entry.TABLET)inv.verify(()->Rs2Inventory.interact(8013,"Break"));
            else if(worn)equipment.verify(()->Rs2Equipment.interact(9790,"Tele to POH"));
            else inv.verify(()->Rs2Inventory.interact(9790,"Tele to POH"));
            if(outsideArrival)clicks.verify(()->TransportObjectClick.click(exterior,"Home"));
            if(exitAtPortal)clicks.verify(()->TransportObjectClick.click(portal,"Enter"));
        }
    }

}
