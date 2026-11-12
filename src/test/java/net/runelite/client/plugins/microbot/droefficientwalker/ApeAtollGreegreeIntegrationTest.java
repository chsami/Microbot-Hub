package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApeAtollGreegreeIntegrationTest {
    static final WorldPoint TARGET = ApeAtollGreegreeTest.ISLAND, HOME = new WorldPoint(3164,3487,0);

    @Test void acceptedBankCloseWaitsForTheInterfaceAndUsesOnlyOneFallback() throws Exception {
        try(Fixture f=new Fixture()) {
            var close=f.mockOwned(net.runelite.api.widgets.Widget.class);var child=f.mockOwned(net.runelite.api.widgets.Widget.class);
            when(f.client.getWidget(786434)).thenReturn(close);when(close.getDynamicChildren()).thenReturn(new net.runelite.api.widgets.Widget[]{child});
            f.bankOpen.set(true);f.carried.put(4026,1);f.carried.put(ApeAtollGreegree.TABLET,1);
            f.prepareBank();f.bankTick();assertEquals("CLOSING_BANK",f.state());
            for(int tick=2;tick<=8;tick++){f.clock.set(tick);f.bankTick();}
            assertEquals(1,f.engine.closeClicks.get());f.bank.verify(Rs2Bank::closeBank,never());
            for(int tick=9;tick<=20;tick++){f.clock.set(tick);f.bankTick();}
            assertEquals(1,f.engine.closeClicks.get());f.bank.verify(Rs2Bank::closeBank,times(1));
            f.bankOpen.set(false);f.bankTick();assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET),f.inputs);
            f.walker.cancel();f.bankOpen.set(true);f.prepareBank();f.bankTick();
            assertEquals(2,f.engine.closeClicks.get(),"A new bank visit must still be able to close normally");
        }
    }

    @Test void emptyInventoryContainerDoesNotRestartTheOwnedBankPreparation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(4026,1); f.bankCounts.put(ApeAtollGreegree.TABLET,10);
            when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
            f.prepareBank(); f.bankTick();
            assertEquals("WITHDRAWING",f.state());
            assertEquals(List.of(4026),f.withdrawals);
            assertEquals(TARGET,f.walker.getLogicalDestination());
        }
    }

    @Test void repeatedPublicRequestsKeepOneBankVisitForTheRecordedGorillaGreegree() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.config.useItemsFromBank()).thenReturn(true);
            f.bankOpen.set(true); f.bankCounts.put(4026,1); f.bankCounts.put(ApeAtollGreegree.TABLET,10);
            assertTrue(f.walker.walkTo(TARGET));
            f.bankTick();
            for(int tick=2;tick<8;tick++) {
                f.clock.set(tick);assertTrue(f.walker.walkTo(TARGET));f.bankTick();
            }
            assertEquals(List.of(4026),f.withdrawals);
            assertEquals("WITHDRAWING",f.state());
            f.carried.put(4026,1);f.bankTick();
            assertEquals(List.of(4026,ApeAtollGreegree.TABLET),f.withdrawals);
            f.carried.put(ApeAtollGreegree.TABLET,1);f.bankTick();
            assertEquals("CLOSING_BANK",f.state());
            f.bankOpen.set(false);f.bankTick();
            assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET),f.inputs);
            f.location.set(new WorldPoint(2771,9101,0));f.carried.remove(ApeAtollGreegree.TABLET);
            invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET,"Hold:4026"),f.inputs);
        }
    }

    @Test void fullyDepositedInventoryDoesNotReuseThePreviousFullBagForWithdrawalCapacity() throws Exception {
        try (Fixture f = new Fixture()) {
            f.bankOpen.set(true);f.bankCounts.put(4026,1);f.bankCounts.put(ApeAtollGreegree.TABLET,10);
            when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
            List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> oldSnapshot=new ArrayList<>();
            for(int slot=0;slot<28;slot++){
                var item=f.mockOwned(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
                when(item.getId()).thenReturn(20000+slot);when(item.getQuantity()).thenReturn(1);oldSnapshot.add(item);
            }
            f.inventory.when(Rs2Inventory::all).thenReturn(oldSnapshot);
            f.prepareBank();f.bankTick();
            assertEquals("WITHDRAWING",f.state());assertEquals(List.of(4026),f.withdrawals);
        }
    }

    @Test void bankWithdrawsOneOwnedGreegreeThenOneTabletBeforeAnyTeleport() throws Exception {
        for (int id : ApeAtollGreegree.IDS) try (Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(id,1); f.bankCounts.put(ApeAtollGreegree.TABLET,10);
            f.prepareBank();
            assertEquals(List.of(id,ApeAtollGreegree.TABLET),new ArrayList<>(((Map<?,?>)get(f.bankCoordinator,"pendingWithdrawals")).keySet()));
            f.bankTick();
            for (int tick=2;tick<=5;tick++) {f.clock.set(tick); f.bankTick();}
            assertEquals(List.of(id),f.withdrawals); assertTrue(f.inputs.isEmpty());
            f.carried.put(id,1); f.bankTick(); assertEquals(List.of(id,ApeAtollGreegree.TABLET),f.withdrawals);
            f.carried.put(ApeAtollGreegree.TABLET,1); f.bankTick(); assertEquals("CLOSING_BANK",f.state());
            assertTrue(f.inputs.isEmpty());
            f.bankOpen.set(false); f.bankTick(); assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET),f.inputs);
            assertEquals(TARGET,f.walker.getLogicalDestination()); assertEquals(EfficientWalker.Status.WALKING,f.walker.getStatus());
            for(int tick=6;tick<=9;tick++) {f.clock.set(tick); invoke(f.walker,"tickApeAtollTabletTravel");}
            assertEquals(1,f.inputs.size()); assertEquals(0,f.engine.walks.get());
            f.carried.remove(ApeAtollGreegree.TABLET); f.location.set(ApeAtollGreegree.TABLET_LANDING);
            invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET,"Hold:"+id),f.inputs);
            f.carried.remove(id); f.clock.set(10); invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(EfficientWalker.Status.WALKING,f.walker.getStatus()); assertEquals(0,f.engine.walks.get());
            f.worn.put(id,1); invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(1,f.engine.walks.get()); assertEquals(TARGET,f.engine.destination);
            assertEquals(2,f.inputs.size());
        }
    }

    @Test void carriedOrWornSuppliesSkipBankAndNoSecondTabletIsSent() throws Exception {
        for(boolean worn : List.of(false,true)) try(Fixture f = new Fixture()) {
            (worn ? f.worn : f.carried).put(4031,1); f.carried.put(ApeAtollGreegree.TABLET,5);
            assertEquals(true,invoke(f.walker,"beginApeAtollTabletTravel",new Class<?>[]{WorldPoint.class},TARGET));
            for(int i=2;i<40;i++) {f.clock.set(i); invoke(f.walker,"tickApeAtollTabletTravel");}
            assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET),f.inputs); assertTrue(f.withdrawals.isEmpty());
            f.clock.set(41); invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus()); assertEquals(1,f.inputs.size());
        }
    }

    @Test void missingEitherBankItemStopsBeforeUnsafeDeparture() throws Exception {
        for(int missing : List.of(4031,ApeAtollGreegree.TABLET)) try(Fixture f = new Fixture()) {
            f.bankOpen.set(true); f.bankCounts.put(missing==4031?ApeAtollGreegree.TABLET:4031,1); f.prepareBank();
            assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus()); assertEquals("IDLE",f.state());
            assertTrue(f.inputs.isEmpty()); assertTrue(f.withdrawals.isEmpty());
        }
    }

    @Test void missingBankSuppliesStopWhenBankUseIsDisabled() throws Exception {
        try(Fixture f = new Fixture()) {
            when(f.config.useItemsFromBank()).thenReturn(false);
            assertFalse(f.walker.walkTo(TARGET)); assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus());
            assertTrue(f.inputs.isEmpty()); assertEquals(0,f.engine.walks.get());
        }
    }

    @Test void departingTheIslandAndPostMm2CannotBeInterceptedByTheHoldGate() throws Exception {
        try(Fixture f = new Fixture()) {
            f.location.set(TARGET); f.engine.status=EfficientWalker.Status.ARRIVED; f.engine.destination=HOME;
            assertEquals(false,invoke(f.walker,"holdGreegreeBeforeSurfaceMovement"));
            assertEquals(EfficientWalker.Status.ARRIVED,f.walker.getStatus()); assertTrue(f.inputs.isEmpty());
            f.engine.destination=TARGET; f.mm2.set(QuestState.FINISHED);
            assertEquals(false,invoke(f.walker,"holdGreegreeBeforeSurfaceMovement"));
            assertEquals(EfficientWalker.Status.ARRIVED,f.walker.getStatus()); assertTrue(f.inputs.isEmpty());
        }
    }

    @Test void humanPausePreservesHoldBudgetAndCancellationClearsTheTrip() throws Exception {
        try(Fixture f = new Fixture()) {
            f.location.set(TARGET); f.engine.destination=TARGET; f.engine.status=EfficientWalker.Status.WALKING; f.carried.put(4031,1);
            invoke(f.walker,"holdGreegreeBeforeSurfaceMovement");
            Microbot.pauseAllScripts.set(true); f.clock.set(1000); invoke(f.walker,"holdGreegreeBeforeSurfaceMovement");
            Microbot.pauseAllScripts.set(false); f.clock.set(1001); invoke(f.walker,"holdGreegreeBeforeSurfaceMovement");
            assertEquals(List.of("Hold:4031"),f.inputs); assertEquals(EfficientWalker.PlanningFailure.NONE,f.walker.getPlanningFailure());
            f.walker.cancel(); assertFalse(((ApeAtollGreegree)get(f.walker,"apeAtollGreegree")).pending());
            assertEquals(false,invoke(f.walker,"tickApeAtollTabletTravel"));
        }
    }

    @Test void loadingAndWorldChangesCannotPretendToBeArrival() throws Exception {
        try(Fixture f = new Fixture()) {
            f.carried.put(4031,1); f.carried.put(ApeAtollGreegree.TABLET,1);
            invoke(f.walker,"beginApeAtollTabletTravel",new Class<?>[]{WorldPoint.class},TARGET);
            f.location.set(null); invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(1,f.inputs.size()); assertEquals(0,f.engine.walks.get());
            when(f.client.getWorld()).thenReturn(302); invoke(f.walker,"tickApeAtollTabletTravel");
            assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus()); assertEquals(1,f.inputs.size());
        }
    }

    @Test void disabledTeleportSettingAndOffIslandJourneysKeepTheirExistingBehavior() throws Exception {
        try(Fixture f = new Fixture()) {
            when(f.config.useTeleportations()).thenReturn(false); f.carried.put(4031,1);
            assertFalse((Boolean)invoke(f.walker,"missingApeAtollSupplies",new Class<?>[]{WorldPoint.class},TARGET));
            assertFalse((Boolean)invoke(f.walker,"useApeAtollTabletFlow",new Class<?>[]{WorldPoint.class},TARGET));
            assertFalse((Boolean)invoke(f.walker,"useApeAtollTabletFlow",new Class<?>[]{WorldPoint.class},HOME));
            assertTrue(f.inputs.isEmpty());
        }
    }

    @Test void repeatedWorldMapCommitsReuseTheSameTabletTrip() throws Exception {
        try(Fixture f = new Fixture()) {
            f.carried.put(4031,1); f.carried.put(ApeAtollGreegree.TABLET,1);
            assertTrue(f.walker.commitPreviewOrStartRoute(TARGET)); assertTrue(f.walker.commitPreviewOrStartRoute(TARGET));
            assertEquals(List.of("Break:"+ApeAtollGreegree.TABLET),f.inputs);
            assertEquals(TARGET,f.walker.getLogicalDestination()); assertEquals(0,f.engine.walks.get());
        }
    }

    @Test void existingGreegreeIsKeptAndOnlyTheMissingTabletIsWithdrawn() throws Exception {
        for(boolean equipped : List.of(false,true)) try(Fixture f = new Fixture()) {
            (equipped?f.worn:f.carried).put(4031,1);f.bankCounts.put(ApeAtollGreegree.TABLET,10);f.bankOpen.set(true);
            f.prepareBank(); f.bankTick(); assertEquals(List.of(ApeAtollGreegree.TABLET),f.withdrawals);
        }
    }

    @Test void delayedWithdrawalsDoNotExpireWhileMouseWorkIsStillRunning() throws Exception {
        try(Fixture f = new Fixture()) {
            f.bankOpen.set(true);f.bankCounts.put(4031,1);f.bankCounts.put(ApeAtollGreegree.TABLET,1);
            AtomicReference<FutureTask<Object>> queued=new AtomicReference<>();
            ExecutorService worker=(ExecutorService)get(f.bankCoordinator,"bankExecutor");
            when(worker.submit(any(Callable.class))).thenAnswer(i->{FutureTask<Object> action=new FutureTask<>((Callable<Object>)i.getArgument(0));queued.set(action);return action;});
            f.prepareBank();f.bankTick();f.clock.set(12);f.bankTick();
            assertEquals("WITHDRAWING",f.state());assertTrue(f.withdrawals.isEmpty());
            queued.get().run();f.clock.set(13);f.bankTick();f.clock.set(20);f.bankTick();
            assertEquals(List.of(4031),f.withdrawals);assertEquals("WITHDRAWING",f.state());
            f.carried.put(4031,1);f.bankTick();f.clock.set(31);f.bankTick();
            assertEquals("WITHDRAWING",f.state());queued.get().run();f.clock.set(32);f.bankTick();
            f.clock.set(39);f.bankTick();assertEquals(List.of(4031,ApeAtollGreegree.TABLET),f.withdrawals);
            f.carried.put(ApeAtollGreegree.TABLET,1);f.bankTick();assertEquals("CLOSING_BANK",f.state());
        }
    }

    @Test void aFailedApeAtollTripCannotPreventTheNextBankRequest() throws Exception {
        try(Fixture f = new Fixture()) {
            when(f.config.useItemsFromBank()).thenReturn(false);
            assertFalse(f.walker.walkTo(TARGET));assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus());
            assertTrue(f.walker.walkToNearestBank());assertEquals(EfficientWalker.Status.WALKING,f.walker.getStatus());
            assertNull(((ApeAtollGreegree)get(f.walker,"apeAtollGreegree")).failure());assertTrue(f.inputs.isEmpty());
        }
    }

    @Test void completedTravelReturnsEquipmentControlToTheCallingScript() throws Exception {
        try(Fixture f = new Fixture()) {
            f.location.set(TARGET);f.engine.destination=TARGET;f.engine.status=EfficientWalker.Status.ARRIVED;f.worn.put(4031,1);
            assertEquals(EfficientWalker.Status.ARRIVED,f.walker.getStatus());
            f.worn.clear();f.carried.put(4031,1); // The activity may equip its axe after arriving.
            assertEquals(EfficientWalker.Status.ARRIVED,f.walker.getStatus());
            assertEquals(false,invoke(f.walker,"holdGreegreeBeforeSurfaceMovement"));assertTrue(f.inputs.isEmpty());
        }
    }

    @Test void realPlannerConnectsTheTabletDungeonLandingToTheSurfaceDestination() throws Exception {
        try(Fixture f = new Fixture()) {
            ClassLoader loader=(ClassLoader)get(f.walker,"engineLoader");
            String shadow="net.runelite.client.plugins.microbot.droefficientwalker.engine";
            Method load=WalkerTestReflection.declaredMethod(Class.forName(shadow + ".TransportCatalog",true,loader), "loadAllTransports");load.setAccessible(true);
            List<Object> edges=new ArrayList<>((List<?>)load.invoke(null));
            Object bridge=get(f.walker,"networkTransportSupport");
            Method catalogue=WalkerTestReflection.declaredMethod(bridge.getClass(), "loadSharedCatalog");catalogue.setAccessible(true);
            Method convert=WalkerTestReflection.declaredMethod(bridge.getClass(), "buildSharedCatalogTransport",net.runelite.client.plugins.microbot.shortestpath.Transport.class,WorldPoint.class);convert.setAccessible(true);
            for(var row:(List<net.runelite.client.plugins.microbot.shortestpath.Transport>)catalogue.invoke(bridge)) {
                if(row.getOrigin()!=null){Object edge=convert.invoke(bridge,row,ApeAtollGreegree.TABLET_LANDING);if(edge!=null)edges.add(edge);}
            }
            Class<?> plannerClass=Class.forName(shadow + ".CollisionRoutePlanner",true,loader);
            Method build=WalkerTestReflection.declaredMethod(plannerClass, "create",List.class,Predicate.class);build.setAccessible(true);
            Object planner=build.invoke(null,edges,(Predicate<Object>)e->true);
            Method plan=WalkerTestReflection.declaredMethod(plannerClass, "planRoute",WorldPoint.class,Collection.class,Set.class,Set.class,Set.class);plan.setAccessible(true);
            Object result=plan.invoke(planner,ApeAtollGreegree.TABLET_LANDING,List.of(TARGET),Set.of(),Set.of(),new HashSet<>(edges));
            List<WorldPoint> path=(List<WorldPoint>)get(result,"path");
            assertFalse(path.isEmpty(),"Existing walker must connect the actual tablet landing to Ape Atoll");
            assertEquals(TARGET,path.get(path.size()-1));assertTrue(path.stream().anyMatch(ApeAtollGreegree::surface));
        }
    }

    static Object get(Object object,String name) throws Exception {Field f=WalkerTestReflection.declaredField(object.getClass(), name); f.setAccessible(true); return f.get(object);}
    static void set(Object object,String name,Object value) throws Exception {Field f=WalkerTestReflection.declaredField(object.getClass(), name); f.setAccessible(true); f.set(object,value);}
    static void state(Object object,String value) throws Exception {Field f=WalkerTestReflection.declaredField(object.getClass(), "state");f.setAccessible(true);f.set(object,Enum.valueOf((Class)f.getType(),value));}
    static Object invoke(Object object,String name) throws Exception {return invoke(object,name,new Class<?>[0]);}
    static Object invoke(Object object,String name,Class<?>[] types,Object... values) throws Exception {Method m=WalkerTestReflection.declaredMethod(object.getClass(), name,types);m.setAccessible(true);return m.invoke(object,values);}

    public static class Engine {
        WorldPoint destination; EfficientWalker.Status status=EfficientWalker.Status.IDLE; final AtomicInteger walks=new AtomicInteger(),closeClicks=new AtomicInteger();
        public EfficientWalker.Status getStatus(){return status;} public WorldPoint getDestination(){return destination;}
        public EfficientWalker.PlanningFailure getPlanningFailure(){return EfficientWalker.PlanningFailure.NONE;}
        public void cancel(){status=EfficientWalker.Status.IDLE;} public void shutdown(){}
        public boolean walkTo(WorldPoint target){destination=target;status=EfficientWalker.Status.WALKING;walks.incrementAndGet();return true;}
        public boolean walkToNearestBank(){return walkTo(HOME);}
        public boolean clickWidgetAction(net.runelite.api.widgets.Widget widget,String action){if("Close".equals(action))closeClicks.incrementAndGet();return true;}
    }

    static final class Fixture implements AutoCloseable {
        // Inline mock answers can point back to this fixture and its real collision planner.
        // Release only the mocks this fixture owns; a global clear would affect other tests.
        private final List<Object> ownedMocks=new ArrayList<>();
        private final java.util.Deque<MockedStatic<?>> staticMocks=new ArrayDeque<>();
        private final Map<Integer,ItemComposition> itemDefinitions=new HashMap<>();
        private final Map<Map.Entry<Integer,Integer>,net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> inventorySnapshots=new HashMap<>();
        private boolean closed;
        Client client; EfficientWalkerConfig config;
        final AtomicReference<WorldPoint> location=new AtomicReference<>(HOME); final AtomicReference<QuestState> mm2=new AtomicReference<>(QuestState.IN_PROGRESS);
        final AtomicInteger clock=new AtomicInteger(1); final AtomicBoolean bankOpen=new AtomicBoolean();
        final Map<Integer,Integer> carried=new LinkedHashMap<>(),worn=new HashMap<>(),bankCounts=new HashMap<>();
        final List<Integer> withdrawals=new ArrayList<>(); final List<String> inputs=new ArrayList<>();
        MockedStatic<Microbot> micro; MockedStatic<Rs2Player> player;
        MockedStatic<TravelEquipment> equipment; MockedStatic<Rs2Bank> bank;
        MockedStatic<Rs2Inventory> inventory;
        EfficientWalker walker; Object bankCoordinator; final Engine engine=new Engine();
        private Object originalEngine;
        private Class<?> originalEngineClass;
        private Field combatConfigField, mouseConfigField;
        private EfficientWalkerConfig previousCombatConfig;
        Fixture() throws Exception {
            try {
                client=mockOwned(Client.class);config=mockOwned(EfficientWalkerConfig.class);
                micro=trackStatic(mockStatic(Microbot.class,withSettings().stubOnly()));
                player=trackStatic(mockStatic(Rs2Player.class,withSettings().stubOnly()));
                equipment=trackStatic(mockStatic(TravelEquipment.class,withSettings().stubOnly()));
                bank=trackStatic(mockStatic(Rs2Bank.class)); // closeBank interactions are asserted.
                inventory=trackStatic(mockStatic(Rs2Inventory.class,withSettings().stubOnly()));
                Microbot.pauseAllScripts.set(false);
                Player actor=mockOwned(Player.class); when(actor.getWorldLocation()).thenAnswer(i->location.get());
                when(client.isClientThread()).thenReturn(true);when(client.getLocalPlayer()).thenReturn(actor);
                when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getTickCount()).thenAnswer(i->clock.get());when(client.getWorld()).thenReturn(301);
                when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
                ItemContainer container=mockOwned(ItemContainer.class);when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
                when(container.getItems()).thenAnswer(i->{Item[] items=new Item[28];int slot=0;for(var e:carried.entrySet())items[slot++]=new Item(e.getKey(),e.getValue());return items;});
                when(client.getItemDefinition(anyInt())).thenAnswer(i->itemDefinitions.computeIfAbsent(i.getArgument(0),id->{
                    ItemComposition item=mockOwned(ItemComposition.class);
                    when(item.isStackable()).thenReturn(id==ApeAtollGreegree.TABLET);
                    return item;
                }));
                micro.when(Microbot::getClient).thenReturn(client);ClientThread thread=mockOwned(ClientThread.class);
                when(thread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
                when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));micro.when(Microbot::getClientThread).thenReturn(thread);
                player.when(Rs2Player::getWorldLocation).thenAnswer(i->location.get());
                player.when(()->Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.NOT_STARTED);
                player.when(()->Rs2Player.getQuestState(Quest.MONKEY_MADNESS_II)).thenAnswer(i->mm2.get());
                player.when(()->Rs2Player.getQuestState(Quest.MONKEY_MADNESS_I)).thenReturn(QuestState.FINISHED);
                equipment.when(TravelEquipment::wornItems).thenAnswer(i->Map.copyOf(worn));
                inventory.when(()->Rs2Inventory.hasItem(anyInt())).thenAnswer(i->carried.getOrDefault(i.getArgument(0),0)>0);
                inventory.when(Rs2Inventory::all).thenAnswer(i->{
                    List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> rows=new ArrayList<>();
                    for(var entry:carried.entrySet()){
                        // Copy both values: a row already returned must keep its observed quantity.
                        var snapshot=Map.entry(entry.getKey(),entry.getValue());
                        rows.add(inventorySnapshots.computeIfAbsent(snapshot,key->{
                            var row=mockOwned(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
                            when(row.getId()).thenReturn(key.getKey());when(row.getQuantity()).thenReturn(key.getValue());
                            return row;
                        }));
                    }
                    return rows;
                });
                inventory.when(()->Rs2Inventory.interact(anyInt(),anyString())).thenAnswer(i->{inputs.add(i.getArgument(1)+":"+i.getArgument(0));return true;});
                bank.when(Rs2Bank::isOpen).thenAnswer(i->bankOpen.get());bank.when(Rs2Bank::getBankLiveEpoch).thenReturn(1);
                bank.when(Rs2Bank::bankItems).thenReturn(List.of());bank.when(()->Rs2Bank.count(anyInt())).thenAnswer(i->bankCounts.getOrDefault(i.getArgument(0),0));
                bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);bank.when(()->Rs2Bank.withdrawOne(anyInt())).thenAnswer(i->{withdrawals.add(i.getArgument(0));return true;});
                when(config.allowWildernessTravel()).thenReturn(true);
                when(config.useTeleportations()).thenReturn(true);
                // Configure runs before the facade constructor can return (or throw).
                combatConfigField=WalkerTestReflection.declaredField(TravelCombatPolicy.class,"config");
                combatConfigField.setAccessible(true);previousCombatConfig=(EfficientWalkerConfig)combatConfigField.get(null);
                mouseConfigField=WalkerTestReflection.declaredField(WalkerMouseControl.class,"config");
                mouseConfigField.setAccessible(true);
                walker=new EfficientWalker(client,config,TestRouteSubmissions.immediate());
                originalEngine=get(walker,"engine");originalEngineClass=(Class<?>)get(walker,"engineClass");
                bankCoordinator=get(walker,"bankTeleportBootstrap");
                ExecutorService old=(ExecutorService)get(bankCoordinator,"bankExecutor");old.shutdownNow();ExecutorService worker=mockOwned(ExecutorService.class);
                when(worker.submit(any(Callable.class))).thenAnswer(i->{FutureTask<Object> action=new FutureTask<>((Callable<Object>)i.getArgument(0));action.run();return action;});
                set(bankCoordinator,"bankExecutor",worker);set(walker,"engine",engine);set(walker,"engineClass",Engine.class);
            } catch (Exception | Error failure) {
                try { close(); } catch (Throwable cleanupFailure) {
                    if(cleanupFailure!=failure)failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        void prepareBank() throws Exception {set(bankCoordinator,"pendingTarget",TARGET);set(bankCoordinator,"bankWasOpenAtStart",true);setState("WAITING_BANK_SNAPSHOT");bankTick();}
        void setState(String state) throws Exception {Field f=WalkerTestReflection.declaredField(bankCoordinator.getClass(), "state");f.setAccessible(true);f.set(bankCoordinator,Enum.valueOf((Class)f.getType(),state));}
        void bankTick() throws Exception {invoke(bankCoordinator,"beforeEngineTick");}
        String state() throws Exception {return get(bankCoordinator,"state").toString();}
        <T> T mockOwned(Class<T> type) {
            T value=mock(type,withSettings().stubOnly());
            ownedMocks.add(value);
            return value;
        }
        private <T> MockedStatic<T> trackStatic(MockedStatic<T> value) {
            staticMocks.addLast(value);
            return value;
        }
        public void close() throws Exception {
            if(closed)return;
            closed=true;
            Throwable failure=null;
            try {
                if(walker!=null) {
                    // The facade was pointed at a stub, but its supports still own this engine.
                    if(originalEngine!=null)failure=cleanup(failure,()->set(walker,"engine",originalEngine));
                    if(originalEngineClass!=null)failure=cleanup(failure,()->set(walker,"engineClass",originalEngineClass));
                    failure=cleanup(failure,walker::shutdown);
                }
            } finally {
                failure=cleanup(failure,this::restoreCombatPolicy);
                failure=cleanup(failure,this::shutdownOwnedMouse);
                while(!staticMocks.isEmpty()) {
                    MockedStatic<?> scope=staticMocks.removeLast();
                    failure=cleanup(failure,scope::close);
                }
                // Also release Mockito's last ongoing-stubbing reference on this worker thread.
                if(!ownedMocks.isEmpty())failure=cleanup(failure,()->clearInvocations(ownedMocks.toArray()));
                for(Object value:ownedMocks)failure=cleanup(failure,()->framework().clearInlineMock(value));
                ownedMocks.clear();
                itemDefinitions.clear();inventorySnapshots.clear();
                Microbot.pauseAllScripts.set(false);
            }
            if(failure instanceof Error)throw (Error)failure;
            if(failure instanceof Exception)throw (Exception)failure;
            if(failure!=null)throw new IllegalStateException("Fixture cleanup failed",failure);
        }
        private void restoreCombatPolicy() throws IllegalAccessException {
            if(combatConfigField!=null&&combatConfigField.get(null)==config)
                TravelCombatPolicy.configure(previousCombatConfig);
        }
        private void shutdownOwnedMouse() throws IllegalAccessException {
            synchronized(WalkerMouseControl.class) {
                if(mouseConfigField!=null&&mouseConfigField.get(null)==config)WalkerMouseControl.shutdown();
            }
        }
        @FunctionalInterface private interface Cleanup { void run() throws Exception; }
        private static Throwable cleanup(Throwable failure,Cleanup action) {
            try { action.run(); }
            catch(Throwable next) {
                if(failure==null)return next;
                if(failure!=next)failure.addSuppressed(next);
            }
            return failure;
        }
    }
}
