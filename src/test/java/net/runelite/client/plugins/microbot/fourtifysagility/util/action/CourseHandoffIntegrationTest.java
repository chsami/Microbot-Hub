package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldView;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.config.BrimhavenVoucherReward;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.CompleteLapTask;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.MovingTask;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.TurnInPyramidTopTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CourseHandoffIntegrationTest
{
    @AfterEach
    public void restoreInput()
    {
        MicrobotTestSupport.restoreAll();
    }

    @Test
    public void finishesVarrockThenWaitsForManualTravelToCanifis() throws Exception
    {
        try
            (Fixture fixture = new Fixture())
        {
            fixture.tick(3215, 3415, 3);
            assertEquals(AgilityCourse.VARROCK, fixture.plugin.getActiveCourse());
            assertEquals(AgilityCourse.CANIFIS, fixture.config.area());
            assertTrue(fixture.plugin.getStatus().contains("Finishing Varrock before Canifis"));
            fixture.tick(3237, 3412, 3);
            fixture.animation = 713;
            fixture.tick(3237, 3417, 0);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
            fixture.animation = -1;
            fixture.tick(3237, 3417, 0);
            assertEquals(AgilityCourse.CANIFIS, fixture.plugin.getActiveCourse());
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
            fixture.tick(3506, 3489, 0);
            // On the course, but this scene has no first obstacle in range.
            assertEquals("Walk closer to the Canifis start", fixture.plugin.getStatus());
        }
    }

    @Test
    public void fallDoesNotStartTheWalker() throws Exception
    {
        try
            (Fixture fixture = new Fixture())
        {
            fixture.tick(3205, 3415, 3);
            fixture.tick(3205, 3415, 0);
            assertEquals(AgilityCourse.VARROCK, fixture.plugin.getActiveCourse());
            fixture.tick(3215, 3415, 3);
            assertEquals(AgilityCourse.VARROCK, fixture.plugin.getActiveCourse());
        }
    }

    @Test
    public void wildernessLogFallWalksBackToTheLogAfterTheLadder() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.WILDERNESS;
            fixture.tick(3002, 3945, 0);
            fixture.tick(3005, 10362, 0);
            fixture.tick(3005, 3964, 0);
            assertEquals("Walking to the log balance", fixture.plugin.getStatus());
        }
    }

    @Test
    public void wildernessRopeFallWalksBackToTheRopeAfterTheLadder() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.WILDERNESS;
            fixture.tick(3005, 3953, 0);
            fixture.tick(3005, 10362, 0);
            fixture.tick(3005, 3964, 0);
            assertEquals("Walking to the rope swing", fixture.plugin.getStatus());
        }
    }

    @Test
    public void wildernessUnknownFallDoesNotGuessWhichObstacleFailed() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.WILDERNESS;
            fixture.tick(3005, 3964, 0);
            assertEquals("Walk back to the obstacle you were attempting", fixture.plugin.getStatus());
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
        }
    }

    @Test
    public void unsupportedDestinationDoesNotThrowAfterFinishing() throws Exception
    {
        try
            (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.tick(3237, 3412, 3);
            fixture.tick(3237, 3417, 0);
            assertEquals("Walk closer to the Agility Pyramid start, or change Course", fixture.plugin.getStatus());
            fixture.tick(3354, 2830, 0);
            assertEquals("Walk closer to the Agility Pyramid start", fixture.plugin.getStatus());
            assertEquals(AgilityCourse.AGILITY_PYRAMID, fixture.plugin.getActiveCourse());
        }
    }

    @Test
    public void startingAwayFromTheSelectedCoursePromptsWithoutWalking() throws Exception
    {
        try
            (Fixture fixture = new Fixture())
        {
            fixture.tick(3280, 3412, 0);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
        }
    }

    @Test
    public void brimhavenWaitsForEntryFeeAndForTheArenaArrow() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.BRIMHAVEN;
            fixture.tick(2808, 3193, 0);
            assertEquals("Bring 200 coins for the arena entry fee", fixture.plugin.getStatus());
            fixture.tick(2805, 9590, 3);
            assertEquals("Waiting for the arena dispenser arrow", fixture.plugin.getStatus());
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
        }
    }

    @Test
    public void brimhavenWaitsForFallAnimationBeforeSeekingRecoveryRope() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.BRIMHAVEN;
            fixture.animation = 846;
            fixture.tick(2783, 9568, 0);
            assertEquals("Waiting to recover from the fall", fixture.plugin.getStatus());
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
            fixture.animation = -1;
            fixture.tick(2783, 9568, 0);
            assertEquals("Walk closer to an arena recovery ladder", fixture.plugin.getStatus());
        }
    }

    @Test
    public void brimhavenLeaveSettingDoesNotPayToReenter() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.BRIMHAVEN;
            fixture.leaveBrimhaven = true;
            fixture.tick(2808, 3193, 0);
            assertEquals("Arena exited; turn off Leave the arena to re-enter", fixture.plugin.getStatus());
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
        }
    }

    @Test
    public void brimhavenVoucherSelectionRemainsAvailable() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.BRIMHAVEN;
            fixture.leaveBrimhaven = true;
            fixture.voucherReward = BrimhavenVoucherReward.TOADFLAX;
            fixture.tick(2808, 3193, 0);
            assertTrue(fixture.plugin.getStatus().contains("Not enough vouchers for Toadflax"));
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
        }
    }

    @Test
    public void pyramidStagingWalksToTheMovingBlockWaitingTile() throws Exception
    {
        try
            (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.tick(3370, 2849, 1);
            assertEquals("Walking to the tile beside the moving block", fixture.plugin.getStatus());
        }
    }

    @Test
    public void pyramidTopCanBeTurnedInAtSimonOutsideCourseArea() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.hasPyramidTop = true;
            fixture.simonPresent = true;
            fixture.tick(3348, 2831, 0);
            assertEquals("Turning in tops", fixture.plugin.getStatus());
            assertEquals(1, fixture.pendingRequests());

            fixture.hasPyramidTop = false;
            fixture.tick(3348, 2831, 0);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
        }
    }

    @Test
    public void pyramidTopAwayFromSimonStillPromptsForTravel() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.hasPyramidTop = true;
            fixture.tick(3200, 3200, 0);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
        }
    }

    @Test
    public void pyramidSaleFinishesStatementAfterTopLeavesInventory() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.hasPyramidTop = true;
            fixture.simonPresent = true;
            fixture.tick(3348, 2831, 0);
            assertEquals(1, fixture.pendingRequests());

            fixture.hasPyramidTop = false;
            fixture.dialogueRoot = 15007744;
            fixture.tick(3348, 2831, 0);
            assertEquals("Turning in tops", fixture.plugin.getStatus());
            assertEquals(1, fixture.pendingRequests());
            assertEquals(0, fixture.menuActions);

            fixture.dialogueRoot = 0;
            fixture.tick(3348, 2831, 0);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
        }
    }

    @Test
    public void unrelatedStatementWithoutTurnInIsNotContinued() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.selected = AgilityCourse.AGILITY_PYRAMID;
            fixture.simonPresent = true;
            fixture.dialogueRoot = 15007744;
            fixture.tick(3348, 2831, 0);
            fixture.actions.dispatch();
            assertEquals(0, fixture.menuActions);
            assertEquals(walkCloser(fixture.selected), fixture.plugin.getStatus());
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        WorldPoint position;
        int animation = -1;
        boolean hasPyramidTop;
        boolean simonPresent;
        boolean leaveBrimhaven;
        BrimhavenVoucherReward voucherReward = BrimhavenVoucherReward.NONE;
        Client client;
        int menuActions;
        int dialogueRoot;
        Object[] lastMenuAction;
        AgilityCourse selected = AgilityCourse.CANIFIS;
        final FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        final AgilityActions actions = new AgilityActions();
        final AgilityConfig config = new AgilityConfig()
        {
            @Override
            public AgilityCourse area()
            {
                return selected;
            }
            @Override
            public boolean ignoreMarks()
            {
                return true;
            }
            @Override
            public boolean brimhavenLeave()
            {
                return leaveBrimhaven;
            }
            @Override
            public BrimhavenVoucherReward brimhavenVoucherReward()
            {
                return voucherReward;
            }
        };

        Fixture() throws Exception
        {
            WorldView[] view = new WorldView[1];
            NPC simon = proxy(NPC.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "getName": return "Simon Templeton";
                    case "getComposition":
                    case "getTransformedComposition":
                        return proxy(net.runelite.api.NPCComposition.class, (o, m, a) ->
                            m.getName().equals("getActions") ? new String[] {"Talk-to"} : defaultValue(m.getReturnType()));
                    case "getLocalLocation": return net.runelite.api.coords.LocalPoint.fromScene(50, 49);
                    case "getId": return 5786;
                    case "getIndex": return 1;
                    case "getWorldLocation": return new WorldPoint(3348, 2830, 0);
                    case "getWorldView": return view[0];
                    default: return defaultValue(method.getReturnType());
                }
            });
            IndexedObjectSet<NPC> npcs = proxy(IndexedObjectSet.class, (object, method, args) ->
                method.getName().equals("iterator")
                    ? (simonPresent ? java.util.List.of(simon) : java.util.List.<NPC>of()).iterator()
                    : method.getName().equals("stream")
                        ? (simonPresent ? java.util.List.of(simon) : java.util.List.<NPC>of()).stream()
                        : defaultValue(method.getReturnType()));
            view[0] = proxy(WorldView.class, (object, method, args) ->
                method.getName().equals("npcs") ? npcs : defaultValue(method.getReturnType()));
            Player player = proxy(Player.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "getWorldLocation":
                        return position;
                    case "getLocalLocation":
                        return net.runelite.api.coords.LocalPoint.fromScene(50, 50);
                    case "getWorldArea":
                        return new WorldArea(position, 1, 1);
                    case "getAnimation":
                        return animation;
                    case "getWorldView":
                        return view[0];
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
            client = proxy(Client.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "isClientThread":
                        return true;
                    case "getLocalPlayer":
                        return player;
                    case "getTopLevelWorldView":
                        return view[0];
                    case "getItemDefinition":
                        return proxy(net.runelite.api.ItemComposition.class, (o, m, a) -> m.getName().equals("getName")
                            ? "Pyramid top" : defaultValue(m.getReturnType()));
                    case "getItemContainer":
                        if ((int) args[0] != 93) return null;
                        return proxy(ItemContainer.class, (o, m, a) -> m.getName().equals("getItems")
                            ? (hasPyramidTop ? new Item[] {new Item(6970, 1)} : new Item[0])
                            : defaultValue(m.getReturnType()));
                    case "getWidget":
                        if (args.length == 1 && ((int) args[0] == dialogueRoot || (int) args[0] == dialogueRoot + 4) && dialogueRoot != 0)
                        {
                            return proxy(Widget.class, (o, m, a) -> defaultValue(m.getReturnType()));
                        }
                        return null;
                    case "menuAction":
                        menuActions++;
                        lastMenuAction = args.clone();
                        return null;
                    case "getGameState":
                        return GameState.LOGGED_IN;
                    case "getBoostedSkillLevel":
                    case "getRealSkillLevel":
                        return selected == AgilityCourse.WILDERNESS ? 99 : 50;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
            Spellbook spells = new Spellbook();
            new MicrobotTestSupport(client, (point, entry) -> {
                menuActions++;
                lastMenuAction = new Object[] {entry.getParam0(), entry.getParam1(), entry.getType(), entry.getIdentifier(), entry.getItemId(), entry.getOption(), entry.getTarget()};
            });
            inject(actions, "client", client);
            inject(plugin, "client", client);
            inject(plugin, "config", config);
            inject(plugin, "actions", actions);
            inject(plugin, "enabled", true);
            inject(plugin, "started", true);
            inject(plugin, "moving", new MovingTask(plugin, client, config, actions, spells));
            inject(plugin, "tops", new TurnInPyramidTopTask(plugin, client, config, actions, spells));
            inject(plugin, "laps", new CompleteLapTask(plugin, client, config, actions, spells));
        }

        int pendingRequests() throws Exception
        {
            Field field = AgilityActions.class.getDeclaredField("queue");
            field.setAccessible(true);
            Object queue = field.get(actions);
            Field requests = queue.getClass().getDeclaredField("queue");
            requests.setAccessible(true);
            return ((java.util.Deque<?>) requests.get(queue)).size();
        }

        void tick(int x, int y, int plane)
        {
            position = new WorldPoint(x, y, plane);
            net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.storeInventoryItemsInMemory(
                new net.runelite.api.events.ItemContainerChanged(93, client.getItemContainer(93)));
            plugin.onGameTick(new GameTick());
        }

        @Override
        public void close()
        {
            actions.reset();
        }
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        if (name.equals("enabled") || name.equals("started"))
        {
            Field lifecycleField = instance.getClass().getDeclaredField("lifecycle");
            lifecycleField.setAccessible(true);
            Object state = lifecycleField.get(instance);
            Field stateField = state.getClass().getDeclaredField(name);
            stateField.setAccessible(true);
            stateField.set(state, value);
            return;
        }
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (type == boolean.class)
        {
            return false;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        return null;
    }

    private static String walkCloser(AgilityCourse course)
    {
        return "Walk closer to the " + course.getDisplayName() + " start, or change Course";
    }
}
