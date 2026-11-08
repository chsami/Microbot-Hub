package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MarksOfGraceIntegrationTest
{
    @Test
    public void inventoryEventsCountPickupsOnceAndIgnoreOtherContainers() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        assertEquals(0, fixture.plugin.getMarksOfGrace());
        fixture.inventoryEvent(42);
        fixture.inventoryEvent(42);
        assertEquals(2, fixture.plugin.getMarksOfGrace());
        fixture.plugin.onItemContainerChanged(new ItemContainerChanged(94, container(200)));
        fixture.inventoryEvent(43);
        assertEquals(3, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void tickFallbackCountsUnreportedChangesWithoutDoubleCountingEvents() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        fixture.quantity = 41;
        fixture.tick();
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.inventoryEvent(41);
        fixture.tick();
        assertEquals(1, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void offCourseInventoryGainsBecomeBaselineInsteadOfCollectedMarks() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        fixture.inventoryEvent(41);
        fixture.position = new WorldPoint(3280, 3412, 0);
        fixture.inventoryEvent(100);
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.position = new WorldPoint(3215, 3415, 3);
        fixture.inventoryEvent(100);
        fixture.inventoryEvent(101);
        assertEquals(2, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void bankTransfersAndTheFirstTickAfterClosingDoNotIncreaseTotal() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        fixture.inventoryEvent(41);
        fixture.bankOpen = true;
        fixture.tick();
        fixture.inventoryEvent(0);
        fixture.inventoryEvent(100);
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.bankOpen = false;
        fixture.inventoryEvent(120);
        fixture.tick();
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.inventoryEvent(121);
        assertEquals(2, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void loadingPreservesSessionTotalAndRebaselinesRestoredInventory() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        fixture.inventoryEvent(41);
        fixture.changeState(GameState.LOADING);
        fixture.inventoryEvent(80);
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.changeState(GameState.LOGGED_IN);
        fixture.inventoryEvent(100);
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.inventoryEvent(101);
        assertEquals(2, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void disabledOrNotStartedPluginIgnoresInventoryEvents() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        inject(fixture.plugin, "enabled", false);
        fixture.inventoryEvent(100);
        assertEquals(0, fixture.plugin.getMarksOfGrace());
        inject(fixture.plugin, "enabled", true);
        inject(fixture.plugin, "started", false);
        fixture.inventoryEvent(200);
        assertEquals(0, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void unavailableInventorySuspendsUntilTheNextValidSnapshot() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryEvent(40);
        fixture.inventoryEvent(41);
        fixture.inventoryAvailable = false;
        fixture.tick();
        fixture.inventoryAvailable = true;
        fixture.quantity = 100;
        fixture.tick();
        assertEquals(1, fixture.plugin.getMarksOfGrace());
        fixture.inventoryEvent(101);
        assertEquals(2, fixture.plugin.getMarksOfGrace());
    }

    @Test
    public void courseRewardsExcludeStartingItemsAndCountNewGainsAfterTurnIn() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.course = AgilityCourse.AGILITY_PYRAMID;
        fixture.position = new WorldPoint(3360, 2840, 0);
        fixture.rewardEvent(6970, 2);
        fixture.rewardEvent(6970, 3);
        fixture.rewardEvent(6970, 0);
        fixture.rewardEvent(6970, 1);
        assertEquals(2, fixture.plugin.getPyramidTops());

        fixture.course = AgilityCourse.BRIMHAVEN;
        fixture.position = new WorldPoint(2783, 9568, 3);
        fixture.rewardEvent(29480, 20);
        fixture.rewardEvent(29480, 21);
        assertEquals(1, fixture.plugin.getArenaTickets());
        assertEquals(0, fixture.plugin.getWildernessTickets());
    }

    @Test
    public void autoProgressIsAbsentFromTheActiveConfiguration()
    {
        for (java.lang.reflect.Method method : AgilityConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertFalse(item != null && "autoProgress".equals(item.keyName()));
        }
    }

    @Test
    public void automaticTicketRedemptionIsAbsentFromTheActiveConfiguration()
    {
        for (java.lang.reflect.Method method : AgilityConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertFalse(item != null && "brimhavenRedeemTickets".equals(item.keyName()));
        }
    }

    private static final class Fixture
    {
        final FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        final AgilityActions actions = new AgilityActions();
        WorldPoint position = new WorldPoint(3215, 3415, 3);
        GameState gameState = GameState.LOGGED_IN;
        int quantity;
        boolean bankOpen;
        boolean inventoryAvailable = true;
        AgilityCourse course = AgilityCourse.VARROCK;

        Fixture() throws Exception
        {
            Player player = proxy(Player.class, (object, method, args) ->
                    "getWorldLocation".equals(method.getName()) ? position : defaultValue(method.getReturnType()));
            Widget visible = proxy(Widget.class, (object, method, args) -> defaultValue(method.getReturnType()));
            Client client = proxy(Client.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "getGameState":
                        return gameState;
                    case "getLocalPlayer":
                        return player;
                    case "getItemContainer":
                        return inventoryAvailable ? container(quantity)
                        : null;
                    case "getWidget":
                        int id = (Integer) args[0];
                        // The Bank PIN guard ends ticks after marks observation, before task dispatch.
                        return id == 13959168 || id == 786432 && bankOpen ? visible : null;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
            inject(actions, "client", client);
            inject(plugin, "client", client);
            inject(plugin, "actions", actions);
            inject(plugin, "config", new AgilityConfig()
            {
                @Override
                public AgilityCourse area()
                {
                    return course;
                }
            });
            inject(plugin, "enabled", true);
            inject(plugin, "started", true);
        }

        void inventoryEvent(int value)
        {
            quantity = value;
            plugin.onItemContainerChanged(new ItemContainerChanged(93, container(value)));
        }

        void tick()
        {
            plugin.onGameTick(new GameTick());
        }

        void rewardEvent(int id, int amount)
        {
            ItemContainer items = proxy(ItemContainer.class, (object, method, args) ->
                "count".equals(method.getName()) && args != null && args.length == 1
                    ? ((Integer) args[0] == id ? amount : 0) : defaultValue(method.getReturnType()));
            plugin.onItemContainerChanged(new ItemContainerChanged(93, items));
        }

        void changeState(GameState state)
        {
            gameState = state;
            GameStateChanged event = new GameStateChanged();
            event.setGameState(state);
            plugin.onGameStateChanged(event);
        }
    }

    private static ItemContainer container(int quantity)
    {
        return proxy(ItemContainer.class, (object, method, args) ->
                "count".equals(method.getName()) && args != null && args.length == 1
                ? ((Integer) args[0] == 11849 ? quantity : 0) : defaultValue(method.getReturnType()));
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
}
