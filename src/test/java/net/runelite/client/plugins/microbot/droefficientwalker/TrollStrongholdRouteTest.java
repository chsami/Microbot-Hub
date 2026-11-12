package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Public object/route facts from the two authorized recordings; no private recordings are fixtures. */
class TrollStrongholdRouteTest {
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
    private static final WorldPoint FARM = p(2828, 3695, 0);
    private static final WorldPoint BURTHORPE = p(2899, 3551, 0);
    private static final WorldPoint TABLET_LANDING = p(2891, 3679, 0);
    private static final List<Edge> LONG_ROUTE = List.of(
            edge(3745, "Open", "Door", 2823, 3555, 0, 2822, 3555, 0),
            edge(3746, "Open", "Door", 2820, 3557, 0, 2820, 3558, 0),
            edge(3730, "Climb-over", "Stile", 2817, 3561, 0, 2817, 3564, 0),
            edge(3748, "Climb", "Rocks", 2856, 3611, 0, 2856, 3613, 0),
            edge(3748, "Climb", "Rocks", 2833, 3627, 0, 2833, 3629, 0),
            edge(3762, "Open", "Secret Door", 2827, 3646, 0, 2823, 10050, 0),
            edge(3788, "Climb-up", "Stone Staircase", 2852, 10105, 0, 2852, 10109, 1),
            edge(3780, "Unlock", "Prison Door", 2848, 10107, 1, 2847, 10107, 1),
            edge(3788, "Climb-up", "Stone Staircase", 2841, 10108, 1, 2845, 10108, 2),
            edge(3773, "Leave", "Exit", 2837, 10089, 2, 2840, 3690, 0),
            edge(16464, "Climb", "Rocks", 2843, 3692, 0, 2838, 3693, 0));
    private static final Edge TELEPORT_FINAL_ROCK = edge(16464, "Climb", "Rocks",
            2844, 3693, 0, 2838, 3693, 0);

    @Test void catalogueAlreadyContainsEveryRecordedObstacleAndExactLanding() throws Exception {
        try (var f = new Graph()) {
            for (Edge recorded : LONG_ROUTE) {
                Object original = recorded.find(f.original);
                assertSame(f.loader, original.getClass().getClassLoader(), "Check runtime metadata");
                assertNotNull(recorded.find(f.merged), recorded.toString());
            }
            TELEPORT_FINAL_ROCK.find(f.original);
            edge(16464, "Climb", "Rocks", 2838, 3693, 0, 2844, 3693, 0).find(f.original);
        }
    }

    @Test void existingRealCollisionGraphConnectsTheEntireRecordedLongChainToTheFarm() throws Exception {
        try (var f = new Graph()) {
            // These are actual catalogue objects after the existing shared replacement pass.
            // No recorded walking segment is turned into an artificial transport edge.
            List<Object> edges = new ArrayList<>();
            for (Edge recorded : LONG_ROUTE) edges.add(recorded.find(f.merged));
            Object planner = f.planner(edges, true, 73);
            f.assertPath(planner, edges, BURTHORPE, FARM);
            WorldPoint current = BURTHORPE;
            for (Edge recorded : LONG_ROUTE) {
                f.assertPath(planner, edges, current, recorded.from);
                assertEquals(List.of(recorded.from, recorded.to),
                        f.assertPath(planner, edges, recorded.from, recorded.to),
                        "Use this exact recorded transition, not an unrelated alternate: " + recorded);
                current = recorded.to;
            }
            f.assertPath(planner, edges, current, FARM);
        }
    }

    @Test void nativeBootAndAgilityRequirementsStayOnTheirRecordedEdges() throws Exception {
        try (var f = new Graph()) {
            Object mountain = LONG_ROUTE.get(3).find(f.original);
            assertTrue(f.hasBootRequirement(mountain));
            assertFalse(f.usable(mountain, false, 73));
            assertTrue(f.usable(mountain, true, 73));
            assertFalse(f.usable(mountain, true, 14));
            Object finalRock = TELEPORT_FINAL_ROCK.find(f.original);
            assertFalse(f.hasBootRequirement(finalRock), "The shared final shortcut is not a boot gate");
            assertFalse(f.usable(finalRock, false, 72));
            assertTrue(f.usable(finalRock, false, 73));
        }
    }

    @Test void sharedSecretEntranceRequiresFinishedQuestAndReplacesUngatedNativeCopies() throws Exception {
        try (var f = new Graph()) {
            List<Transport> entrances = f.catalog.stream()
                    .filter(NetworkTransportSupport::isTrollStrongholdSecretEntrance)
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(2, entrances.size());
            assertEquals(2, f.original.stream().filter(value -> id(value) == 3762).count());
            f.quest.set(QuestState.NOT_STARTED);
            List<Object> denied = new ArrayList<>(f.original);
            f.installShared(denied);
            assertEquals(0, denied.stream().filter(value -> id(value) == 3762).count(),
                    "A rejected maintained entrance must not leave an ungated duplicate copy");
            assertEquals(2, f.shared.values().stream()
                    .filter(NetworkTransportSupport::isTrollStrongholdSecretEntrance).count());
            Method allowed = method(NetworkTransportSupport.class, "sharedRequirementsSatisfiedNow", Transport.class);
            for (QuestState state : List.of(QuestState.NOT_STARTED, QuestState.IN_PROGRESS, QuestState.FINISHED)) {
                f.quest.set(state);
                for (Transport entrance : entrances)
                    assertEquals(state == QuestState.FINISHED, allowed.invoke(f.bridge, entrance), state.toString());
            }
            Transport exit = f.catalog.stream().filter(row -> row.getObjectId() == 3761
                    && p(2823, 10050, 0).equals(row.getOrigin())).findFirst().orElseThrow();
            assertFalse(NetworkTransportSupport.isTrollStrongholdSecretEntrance(exit));
        }
    }

    @Test void recordedTeleportLandingReachesTheSameFarmWithoutBootDependentEdges() throws Exception {
        try (var f = new Graph()) {
            // The recorded surface leg is ordinary collision-map walking plus this existing
            // final shortcut. Exclude all unrelated alternative journeys from the proof.
            List<Object> edges = List.of(TELEPORT_FINAL_ROCK.find(f.merged));
            assertTrue(edges.stream().allMatch(edge -> !f.bootRequired(edge)));
            assertTrue(edges.stream().allMatch(edge -> f.usable(edge, false, 73)));
            Object planner = f.planner(edges, false, 73);
            List<WorldPoint> path = f.assertPath(planner, edges, TABLET_LANDING, FARM);
            assertTrue(path.stream().allMatch(tile -> tile.getPlane() == 0), "Use the recorded surface approach");
            assertTrue(path.contains(TELEPORT_FINAL_ROCK.from), "Reach the recorded east-side approach by ordinary walking");
            assertTrue(path.contains(TELEPORT_FINAL_ROCK.to), "Retain the observed 73 Agility shortcut landing");
            f.assertPath(planner, edges, TABLET_LANDING, TELEPORT_FINAL_ROCK.from);
            assertEquals(List.of(TELEPORT_FINAL_ROCK.from, TELEPORT_FINAL_ROCK.to),
                    f.assertPath(planner, edges, TELEPORT_FINAL_ROCK.from, TELEPORT_FINAL_ROCK.to));
            f.assertPath(planner, edges, TELEPORT_FINAL_ROCK.to, FARM);
            assertFalse(path.contains(p(2823, 10050, 0)), "The teleport approach does not use the secret entrance");
        }
    }

    @Test void tabletMetadataAndActualSharedProviderRetainRecordedItemAndBreakActionWithoutBoots() throws Exception {
        Transport tablet = TransportResources.loadAllFromResources().values().stream()
                .flatMap(Set::stream).filter(row -> row.getType() == TransportType.TELEPORTATION_ITEM
                        && row.getItemRequirements().stream().anyMatch(group -> group.getAlternatives().containsKey(11747)))
                .findFirst().orElseThrow();
        assertEquals(p(2890, 3679, 0), tablet.getDestination());
        assertEquals(1, tablet.getDestination().distanceTo2D(TABLET_LANDING));
        assertEquals(1, tablet.getItemRequirements().size());
        assertEquals(Map.of(11747, 1), tablet.getItemRequirements().get(0).getAlternatives());
        assertTrue(tablet.isConsumable());
        try (var f = new ApeAtollGreegreeIntegrationTest.Fixture()) {
            f.carried.put(11747, 1);
            Rs2ItemModel item = mock(Rs2ItemModel.class);
            when(item.getId()).thenReturn(11747);
            when(item.getActionFromList(anyList())).thenReturn("Break");
            f.inventory.when(() -> Rs2Inventory.get(11747)).thenReturn(item);
            CatalogTeleportSupport support = (CatalogTeleportSupport) field(f.walker, "catalogTeleportSupport");
            support.refreshForPlanning();
            Object provider = null;
            for (Object candidate : ((Map<?, ?>) field(support, "providers")).keySet())
                if (Arrays.stream((int[]) field(candidate, "itemIds")).anyMatch(id -> id == 11747)) provider = candidate;
            assertNotNull(provider, "The existing shared bridge must actually install tablet11747");
            assertEquals(tablet.getDestination(), method(provider.getClass(), "getDestination").invoke(provider));
            assertEquals("Break", ((Supplier<?>) field(provider, "itemActionSupplier")).get());
            Method available = method(provider.getClass(), "isAvailableForRoute", WorldPoint.class, WorldPoint.class);
            assertEquals(true, available.invoke(provider, p(3165, 3487, 0), FARM));
            assertFalse(f.carried.containsKey(3105));
            assertFalse(f.worn.containsKey(3105));
            f.carried.remove(11747);
            assertEquals(false, available.invoke(provider, p(3165, 3487, 0), FARM));
            assertTrue(f.inputs.isEmpty(), "Provider admission is not a teleport/arrival receipt");
        }
    }

    @Test void spellMetadataKeepsEadgarModernSpellbookAndRuneRequirementsWithoutBootGate() throws Exception {
        TeleportProviders.TeleportProvider provider = TeleportProviders.createProviders().stream().filter(row -> "Trollheim Teleport".equals(row.name))
                .findFirst().orElseThrow();
        Spell spell = (Spell) field(provider, "spell");
        assertEquals(Rs2Spellbook.MODERN, spell.getSpellbook());
        assertEquals(61, spell.getRequiredLevel());
        assertEquals(Map.of(Runes.FIRE, 2, Runes.LAW, 2), spell.getRequiredRunes());
        BooleanSupplier requirement = (BooleanSupplier) field(provider, "availability");
        try (var player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getQuestState(Quest.EADGARS_RUSE)).thenReturn(QuestState.NOT_STARTED);
            assertFalse(requirement.getAsBoolean());
            player.when(() -> Rs2Player.getQuestState(Quest.EADGARS_RUSE)).thenReturn(QuestState.FINISHED);
            assertTrue(requirement.getAsBoolean());
            assertTrue(provider.isAvailableWithRequirements(false, 0, candidate -> true, id -> false, requirement.getAsBoolean()));
            assertFalse(provider.isAvailableWithRequirements(false, 0, candidate -> false, id -> false, true));
            assertFalse(provider.isAvailableWithRequirements(true, 0, candidate -> true, id -> false, true));
        }
    }

    private static final class Graph implements AutoCloseable {
        final MockedStatic<Microbot> dro = mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
        final AtomicReference<QuestState> quest = new AtomicReference<>(QuestState.FINISHED);
        final ClassLoader loader;
        final NetworkTransportSupport bridge;
        final List<Object> original;
        final List<Object> merged;
        final List<Transport> catalog;
        final Map<Object, Transport> shared = new IdentityHashMap<>();
        final Class<?> plannerClass;

        @SuppressWarnings("unchecked") Graph() throws Exception {
            player.when(() -> Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.FINISHED);
            player.when(() -> Rs2Player.getQuestState(Quest.TROLL_STRONGHOLD)).thenAnswer(call -> quest.get());
            player.when(() -> Rs2Player.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
            fixture.setup();
            loader = (ClassLoader) field(fixture, "loader");
            bridge = (NetworkTransportSupport) field(fixture, "bridge");
            List<Edge> recorded = new ArrayList<>(LONG_ROUTE);
            recorded.add(edge(3762, "Open", "Secret Door", 2828, 3646, 0, 2823, 10050, 0));
            recorded.add(edge(3761, "Open", "Exit", 2823, 10050, 0, 2827, 3646, 0));
            catalog = ((List<Transport>) field(fixture, "catalog")).stream()
                    .filter(row -> recorded.stream().anyMatch(spec -> spec.matchesSource(row)))
                    .collect(java.util.stream.Collectors.toList());
            set(bridge, "sharedCatalog", catalog);
            Client client = (Client) field(bridge, "client");
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            Player local = mock(Player.class);
            when(local.getWorldLocation()).thenReturn(BURTHORPE);
            when(client.getLocalPlayer()).thenReturn(local);
            ClientThread thread = mock(ClientThread.class);
            when(thread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(Microbot::getClient).thenReturn(client);
            Class<?> edgeClass = (Class<?>) field(fixture, "transportClass");
            for (String[] binding : List.of(new String[] {"transportOriginField", "origin"},
                    new String[] {"transportDestinationField", "destination"}, new String[] {"transportActionField", "action"},
                    new String[] {"transportTargetField", "target"}, new String[] {"transportObjectIdField", "objectId"})) {
                Field value = WalkerTestReflection.declaredField(edgeClass, binding[1]);
                value.setAccessible(true);
                set(bridge, binding[0], value);
            }
            original = new ArrayList<>((List<?>) method(Class.forName(SHADOW + ".TransportCatalog", true, loader), "loadAllTransports").invoke(null));
            merged = new ArrayList<>(original);
            installShared(merged);
            shared.putAll((Map<Object, Transport>) field(bridge, "sharedCatalogEdges"));
            plannerClass = Class.forName(SHADOW + ".CollisionRoutePlanner", true, loader);
        }

        void installShared(List<Object> edges) throws Exception {
            ((Map<?, ?>) field(bridge, "sharedCatalogEdges")).clear();
            method(NetworkTransportSupport.class, "addSharedCatalogEdges", List.class).invoke(bridge, edges);
        }
        boolean bootRequired(Object edge) {
            try { return hasBootRequirement(edge); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
        boolean hasBootRequirement(Object edge) throws Exception {
            return (boolean) method(edge.getClass(), "requiresItem", String.class).invoke(edge, "CLIMBING_BOOTS");
        }
        boolean usable(Object edge, boolean boots, int agility) {
            try {
                Transport source = shared.get(edge);
                if (source != null && !(boolean) method(NetworkTransportSupport.class,
                        "sharedRequirementsSatisfiedNow", Transport.class).invoke(bridge, source)) return false;
                ToIntFunction<Skill> skills = skill -> skill == Skill.AGILITY ? agility : 99;
                BiPredicate<String, Integer> items = (name, quantity) -> !"CLIMBING_BOOTS".equals(name) || boots;
                IntUnaryOperator variables = id -> 0;
                Predicate<Quest> quests = value -> value != Quest.TROLL_STRONGHOLD || quest.get() == QuestState.FINISHED;
                return (boolean) method(edge.getClass(), "meetsRequirements", ToIntFunction.class, BiPredicate.class,
                        IntUnaryOperator.class, IntUnaryOperator.class, Predicate.class)
                        .invoke(edge, skills, items, variables, variables, quests);
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
        Object planner(List<Object> edges, boolean boots, int agility) throws Exception {
            return method(plannerClass, "create", List.class, Predicate.class)
                    .invoke(null, edges, (Predicate<Object>) edge -> usable(edge, boots, agility));
        }
        @SuppressWarnings("unchecked") List<WorldPoint> assertPath(Object planner, List<Object> edges,
                                                                  WorldPoint from, WorldPoint to) throws Exception {
            Object result = method(plannerClass, "planRoute", WorldPoint.class, Collection.class,
                    Set.class, Set.class, Set.class).invoke(planner, from, List.of(to), Set.of(), Set.of(), new HashSet<>(edges));
            List<WorldPoint> path = (List<WorldPoint>) field(result, "path");
            assertFalse(path.isEmpty(), "Missing real collision route " + from + " -> " + to + "; failure=" + field(result, "failure"));
            assertEquals(from, path.get(0));
            assertEquals(to, path.get(path.size() - 1));
            return path;
        }
        @Override public void close() { player.close(); dro.close(); }
    }

    private static final class Edge {
        final int id;
        final String action;
        final String name;
        final WorldPoint from;
        final WorldPoint to;
        Edge(int id, String action, String name, WorldPoint from, WorldPoint to) {
            this.id = id; this.action = action; this.name = name; this.from = from; this.to = to;
        }
        Object find(List<Object> edges) {
            return edges.stream().filter(value -> matches(value)).findFirst()
                    .orElseThrow(() -> new AssertionError("Missing recorded catalogue edge " + this));
        }
        boolean matchesSource(Transport value) {
            return id == value.getObjectId() && action.equals(value.getAction()) && name.equals(value.getName())
                    && from.equals(value.getOrigin()) && to.equals(value.getDestination());
        }
        boolean matches(Object value) {
            try { return Integer.valueOf(id).equals(field(value, "objectId")) && action.equals(field(value, "action"))
                    && name.equals(field(value, "target")) && from.equals(field(value, "origin")) && to.equals(field(value, "destination")); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
        @Override public String toString() { return id + " " + action + " " + name + " " + from + " -> " + to; }
    }
    private static Edge edge(int id, String action, String name, int x, int y, int plane, int dx, int dy, int dplane) {
        return new Edge(id, action, name, p(x, y, plane), p(dx, dy, dplane));
    }
    private static WorldPoint p(int x, int y, int plane) { return new WorldPoint(x, y, plane); }
    private static int id(Object owner) {
        try { return (Integer) field(owner, "objectId"); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = WalkerTestReflection.declaredField(owner.getClass(), name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = WalkerTestReflection.declaredField(owner.getClass(), name); field.setAccessible(true); field.set(owner, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = WalkerTestReflection.declaredMethod(type, name, parameters); method.setAccessible(true); return method;
    }
}
