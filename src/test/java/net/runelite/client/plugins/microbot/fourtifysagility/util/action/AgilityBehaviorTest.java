package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.CompleteLapTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AgilityBehaviorTest
{
    private static final List<AgilityActions> ACTIVE_ACTIONS = new ArrayList<>();
    @AfterEach
    public void restoreInput()
    {
        ACTIVE_ACTIONS.forEach(AgilityActions::reset);
        ACTIVE_ACTIONS.clear();
        MicrobotTestSupport.restoreAll();
    }

    @Test
    public void actionDelayCountsDownOnlyWhileIdle() throws Exception
    {
        FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        Player idle = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) ->
                method.getName().equals("getAnimation") ? -1 : null);
        Player animating = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) ->
                method.getName().equals("getAnimation") ? 713 : null);
        plugin.setTickDelay(2);
        plugin.setStatus("Crossing the log");
        assertTrue(waitForDelay(plugin, animating));
        assertEquals("Crossing the log", plugin.getStatus(), "An animation keeps the activity status");
        assertTrue(waitForDelay(plugin, idle));
        assertEquals("Short pause (2 ticks)", plugin.getStatus());
        assertTrue(waitForDelay(plugin, idle));
        assertEquals("Short pause (1 tick)", plugin.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(waitForDelay(plugin, idle));
    }

    @Test
    public void stoppingCancelsQueuedRequests() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(856));
        fixture.actions.eat(856);

        fixture.actions.reset();
        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void newTickExpiresUndispatchedRequests() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(856));
        fixture.actions.eat(856);

        fixture.actions.nextTick();
        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void queuedItemActionDoesNotUseReplacementInOriginalSlot() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(856, 561));
        fixture.actions.eat(856);
        fixture.setInventory(inventory(561, 856));

        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void foodConsumesExistingPiecesBeforeWholeCake() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(1891, 1893, 1895));

        fixture.actions.eat(1891);
        fixture.dispatch();

        assertEquals(1895, fixture.menu.get(0).itemId);
        assertEquals(2, fixture.menu.get(0).slot);
        fixture.setInventory(inventory(1891, 1893));
        fixture.actions.eat(1891);
        fixture.dispatch();
        assertEquals(1893, fixture.menu.get(1).itemId);
    }

    @Test
    public void foodTiersConsumeLowestDoseOrPieceFirst() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(1897, 1899, 1901, 6685, 6687));

        fixture.actions.eat(1897);
        fixture.dispatch();
        assertEquals(1901, fixture.menu.get(0).itemId);

        fixture.actions.eat(6685);
        fixture.dispatch();
        assertEquals(6687, fixture.menu.get(1).itemId);
    }

    @Test
    public void missingConfiguredFoodSetsStatusWithoutChatSpamAndResumesWhenAdded() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.hitpoints = 5;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public int foodId() { return 385; }
            @Override public int minHealth() { return 10; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.setInventory(inventory(1891));
        task.execute();
        task.execute();
        fixture.dispatch();
        assertEquals("Hitpoints below 10 and no food (ID 385) left; add food to continue", fixture.plugin.getStatus());
        assertEquals(0, fixture.warningCount);
        assertTrue(fixture.menu.isEmpty());

        fixture.setInventory(inventory(385));
        task.execute();
        fixture.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(385, fixture.menu.get(0).itemId);
        assertEquals(0, fixture.warningCount);
    }

    @Test
    public void lowHitpointsWithoutConfiguredFoodNamesTheCauseAndTheSetting() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.hitpoints = 5;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public int minHealth() { return 10; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.setInventory(inventory(385));
        task.execute();
        fixture.dispatch();
        assertEquals("Hitpoints below 10; heal, or set Food item ID under Healing", fixture.plugin.getStatus());
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void lowAgilityWaitsWithoutSpamAndUsesSummerPieWhenAvailable() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.agility = 59;
        fixture.realAgility = 55;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public boolean ignoreMarks() { return true; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.position = new WorldPoint(2728, 3488, 0);
        task.execute();
        task.execute();
        fixture.dispatch();
        assertEquals("Agility below 60; add a summer pie to continue", fixture.plugin.getStatus());
        assertEquals(0, fixture.warningCount);
        assertTrue(fixture.menu.isEmpty());

        fixture.setInventory(inventory(7220));
        task.execute();
        fixture.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(7220, fixture.menu.get(0).itemId);

        fixture.setInventory(new Item[0]);
        fixture.agility = 60;
        fixture.plugin.setStatus("Running the course");
        task.execute();
        // The level is met, so no pie is asked for; this scene has no wall, so the lap asks to come closer.
        assertEquals("Walk closer to the Seers Village start", fixture.plugin.getStatus());
    }

    @Test
    public void aGapTooLargeForASummerPieAsksForALowerCourse() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.agility = 54;
        fixture.realAgility = 54;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public boolean ignoreMarks() { return true; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.position = new WorldPoint(2728, 3488, 0);
        task.execute();
        fixture.dispatch();
        assertEquals("Agility 60 needed for this course; choose a lower-level course", fixture.plugin.getStatus());
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void summerPieIsKeptWhenTheLevelAlreadyMeetsTheObstacle() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.agility = 60;
        fixture.realAgility = 60;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public boolean ignoreMarks() { return true; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.position = new WorldPoint(2728, 3488, 0);
        fixture.setInventory(inventory(7220));
        task.execute();
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void seersLapEndWalksBackToTheWallBeyondTheObjectRange() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.view = proxy(net.runelite.api.WorldView.class, (object, method, args) -> defaultValue(method.getReturnType()));
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
            @Override public boolean ignoreMarks() { return true; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        fixture.agility = 60;
        fixture.realAgility = 60;
        fixture.position = new WorldPoint(2704, 3464, 0);
        fixture.plugin.setStatus("Running the course");
        task.execute();
        assertEquals("Walking to the Seers Village start", fixture.plugin.getStatus());
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void wyrmBasicBranchAndSharedZiplineRemainUsableAtLevelFifty() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.agility = 50;
        fixture.realAgility = 50;
        AgilityConfig config = new AgilityConfig()
        {
            @Override public AgilityCourse area() { return AgilityCourse.COLOSSAL_WYRM; }
            @Override public boolean ignoreMarks() { return true; }
        };
        inject(fixture.plugin, "config", config);
        CompleteLapTask task = new CompleteLapTask(fixture.plugin, fixture.client, config, fixture.actions, fixture.spells);
        for (WorldPoint position : new WorldPoint[] {new WorldPoint(1649, 2910, 1), new WorldPoint(1625, 2933, 2)})
        {
            fixture.position = position;
            fixture.plugin.setStatus("Running the course");
            task.execute();
            assertEquals("Running the course", fixture.plugin.getStatus());
        }
    }

    @Test
    public void runEnergyMustExceedThreshold() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 2000;
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());

        fixture.energy = 2100;
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals(10485788, fixture.menu.get(0).widget);
    }

    @Test
    public void nativeRunToggleRejectsZeroEnergyEvenWithActiveStamina() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 0;
        fixture.varbits.put(25, 1);

        fixture.actions.manageRun(20, 0);
        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void lowEnergyDrinksAnySupportedPotionWithoutCrossingRunThreshold() throws Exception
    {
        for (int id : new int[] {12625, 12627, 12629, 12631, 3016, 3018, 3020, 3022, 3008, 3010, 3012, 3014})
        {
            Fixture fixture = new Fixture();
            fixture.energy = 1000;
            fixture.setInventory(inventory(id));
            fixture.actions.manageRun(20, 0);
            fixture.dispatch();
            assertEquals(1, fixture.menu.size(), "item " + id);
            assertEquals(id, fixture.menu.get(0).itemId);
            assertEquals(2, fixture.menu.get(0).operation);
        }
    }

    @Test
    public void firstSupportedEnergyPotionIsUsedInInventoryOrder() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 100;
        fixture.setInventory(inventory(3014, 12625));

        fixture.actions.manageRun(0, 0);
        fixture.dispatch();
        fixture.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals(3014, fixture.menu.get(0).itemId);
        assertEquals(2, fixture.menu.get(0).operation);
    }

    @Test
    public void activeStaminaSkipsStaminaButStillUsesEnergyPotion() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 100;
        fixture.varbits.put(25, 1);
        fixture.setInventory(inventory(12625, 3016));

        fixture.actions.manageRun(0, 0);
        fixture.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals(3016, fixture.menu.get(0).itemId);
    }

    @Test
    public void activeStaminaDoesNotDrinkAnotherStaminaDose() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 1000;
        fixture.varbits.put(25, 1);
        fixture.varps.put(173, 1);
        fixture.setInventory(inventory(12625));
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void energyPotionWaitsForObservedDoseChangeOrTimeout() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 1000;
        fixture.setInventory(inventory(3016));
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertEquals(1, fixture.menu.size());
        for (int i = 0; i < 7; i++)
        {
            fixture.tick++;
            fixture.actions.manageRun(20, 0);
            fixture.dispatch();
        }
        assertEquals(1, fixture.menu.size());
        fixture.setInventory(inventory(3018));
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertEquals(2, fixture.menu.size());
        assertEquals(3018, fixture.menu.get(1).itemId);
        fixture.tick += 8;
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertEquals(3, fixture.menu.size());
    }

    @Test
    public void queuedEnergyPotionRechecksItsSlotAndStaminaEffect() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.energy = 1000;
        fixture.setInventory(inventory(12625));
        fixture.actions.manageRun(20, 0);
        fixture.varbits.put(25, 1);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
        fixture.varbits.put(25, 0);
        fixture.actions.manageRun(20, 0);
        fixture.setInventory(inventory(3016));
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void energyPotionNotConsumedAtFullEnergyOrWithBankOpen() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.varps.put(173, 1);
        fixture.setInventory(inventory(3016));
        fixture.energy = 10000;
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
        fixture.energy = 1000;
        fixture.widgets.put(786432, widget(786432, false));
        fixture.actions.manageRun(20, 0);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void missingSpellDoesNotSubmitInput() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.setInventory(inventory(856, 561, 554));

        fixture.spells.alchemy(856);
        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.warningCount);
    }

    @Test
    public void alchemyTargetsItemOnlyAfterSelectingExpectedSpell() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.selectRequestedWidget = true;
        fixture.spells.alchemy(856);
        fixture.dispatch();
        assertEquals(2, fixture.menu.size());
        assertEquals(MenuAction.WIDGET_TARGET, fixture.menu.get(0).action);
        assertEquals(MenuAction.WIDGET_TARGET_ON_WIDGET, fixture.menu.get(1).action);
        assertEquals(856, fixture.menu.get(1).itemId);
        assertEquals(0, fixture.menu.get(1).slot);
    }

    @Test
    public void failedSpellSelectionCannotTargetInventory() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.selectedWidget = widget(999, false);

        fixture.spells.alchemy(856);
        fixture.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals(MenuAction.WIDGET_TARGET, fixture.menu.get(0).action);
        assertEquals(0, fixture.warningCount);
        assertEquals(999, fixture.selectedWidget.getId(), "a selection the plugin did not make stays");
        fixture.spells.alchemy(856);
        fixture.dispatch();
        assertEquals(2, fixture.menu.size());
        assertEquals(MenuAction.WIDGET_TARGET, fixture.menu.get(1).action);
    }

    @Test
    public void lateSpellSelectionIsObservedBeforeTargetAndMovedItemIsRejected() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.spells.alchemy(856);
        fixture.actions.dispatch();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (fixture.menu.isEmpty() && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        assertEquals(1, fixture.menu.size());
        fixture.setInventory(inventory(561, 856, 554));
        fixture.selectedWidget = fixture.nativeSpellWidget();
        fixture.awaitWorker();
        assertEquals(1, fixture.menu.size());
        fixture.selectRequestedWidget = true;
        fixture.spells.alchemy(856);
        fixture.dispatch();
        assertEquals(3, fixture.menu.size());
        assertEquals(MenuAction.WIDGET_TARGET_ON_WIDGET, fixture.menu.get(2).action);
        assertEquals(1, fixture.menu.get(2).slot);
    }

    @Test
    public void aSkippedItemCastClearsTheSelectedSpell() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.spells.alchemy(856);
        fixture.actions.dispatch();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (fixture.menu.isEmpty() && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        // The spell is selected, but the item has moved, so the cast on it is skipped.
        fixture.setInventory(inventory(561, 856, 554));
        fixture.selectedWidget = fixture.nativeSpellWidget();
        fixture.awaitWorker();
        assertEquals(1, fixture.menu.size());
        assertNull(fixture.selectedWidget);
    }

    @Test
    public void alchemyWaitsForTheInventoryBeforeTargetingTheItem() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.selectRequestedWidget = true;
        fixture.inventoryAfterSelect = false;
        fixture.spells.alchemy(856);
        fixture.dispatch();
        // The Magic tab stayed open, so an item click would do nothing; the spell is cleared instead.
        assertEquals(1, fixture.menu.size());
        assertNull(fixture.selectedWidget);
    }

    @Test
    public void alchemyDoesNotFollowAnItemMovedToAnotherSlot() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.spells.alchemy(856);
        fixture.setInventory(inventory(561, 856, 554));

        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void alchemyRechecksRunesBeforeSelectingSpell() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installAlchemy();
        fixture.spells.alchemy(856);
        fixture.setInventory(inventory(856, 561));

        fixture.dispatch();

        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void seersTeleportDispatchesOnceAndWaitsForObservedArrival() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installCamelot();
        CompleteLapTask task = fixture.seersTask();
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(2, fixture.menu.get(0).operation);
        for (int i = 0; i < 4; i++)
        {
            fixture.tick++;
            assertTrue(task.waitingForSeersTeleport(fixture.player));
        }
        assertEquals(1, fixture.menu.size());
        fixture.position = new WorldPoint(2726, 3484, 0);
        assertFalse(task.waitingForSeersTeleport(fixture.player));
    }

    @Test
    public void seersTeleportWaitsForIdleAndDiscardsStaleQueuedAction() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installCamelot();
        CompleteLapTask task = fixture.seersTask();
        fixture.animation = 713;
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
        fixture.animation = -1;
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.position = new WorldPoint(2705, 3464, 0);
        fixture.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertFalse(task.waitingForSeersTeleport(fixture.player));
    }

    @Test
    public void seersTeleportPausesOnRejectionOrMissingRequirementsAndResets() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installCamelot();
        CompleteLapTask task = fixture.seersTask();
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.dispatch();
        task.seersTeleportMessage("You must complete the hard tier of the Kandarin achievement diary.");
        assertTrue(task.waitingForSeersTeleport(fixture.player));
        assertTrue(fixture.plugin.getStatus().contains("failed"));
        assertTrue(fixture.plugin.getStatus().contains("Turn off Use diary teleport, or turn the plugin off and on"));
        task.resetSeersTeleport();
        assertFalse(task.waitingForSeersTeleport(fixture.player));

        fixture.setInventory(new Item[0]);
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.dispatch();
        assertTrue(task.waitingForSeersTeleport(fixture.player));
        assertEquals(1, fixture.menu.size());
    }

    @Test
    public void seersTeleportTimesOutWithoutObservedArrival() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.installCamelot();
        CompleteLapTask task = fixture.seersTask();
        task.execute();
        fixture.tick++;
        task.execute();
        fixture.dispatch();
        fixture.tick += 10;
        assertTrue(task.waitingForSeersTeleport(fixture.player));
        assertTrue(fixture.plugin.getStatus().contains("failed"));
        fixture.tick++;
        assertTrue(task.waitingForSeersTeleport(fixture.player));
        assertEquals(1, fixture.menu.size());
    }

    private static boolean waitForDelay(FourtifysAgilityPlugin plugin, Player player) throws Exception
    {
        Method method = FourtifysAgilityPlugin.class.getDeclaredMethod("waitForDelay", Player.class);
        method.setAccessible(true);
        return (boolean) method.invoke(plugin, player);
    }

    private static Item[] inventory(int... ids)
    {
        Item[] items = new Item[ids.length];
        for (int slot = 0; slot < ids.length; slot++)
        {
            items[slot] = new Item(ids[slot], 10);
        }
        return items;
    }

    private static Widget widget(int id, boolean hidden)
    {
        return proxy(Widget.class, (object, method, arguments) -> {
            switch (method.getName())
            {
                case "getBounds":
                    return new java.awt.Rectangle(10, 10, 10, 10);
                case "getId":
                    return id;
                case "isHidden":
                    return hidden;
                case "getOnInvTransmitListener":
                    return new Object[] {1};
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive() || type == void.class)
        {
            return null;
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == byte.class)
        {
            return (byte) 0;
        }
        if (type == short.class)
        {
            return (short) 0;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        if (type == float.class)
        {
            return 0F;
        }
        if (type == double.class)
        {
            return 0D;
        }
        if (type == char.class)
        {
            return '\0';
        }
        throw new IllegalArgumentException(type.getName());
    }

    private static final class MenuRequest
    {
        final int slot;
        final int widget;
        final MenuAction action;
        final int operation;
        final int itemId;

        MenuRequest(Object[] arguments)
        {
            slot = (Integer) arguments[0];
            widget = (Integer) arguments[1];
            action = (MenuAction) arguments[2];
            operation = (Integer) arguments[3];
            itemId = (Integer) arguments[4];
        }
    }

    private static final class Fixture implements InvocationHandler
    {
        final AgilityActions actions = new AgilityActions();
        final Spellbook spells = new Spellbook();
        final FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        final Thread owner = Thread.currentThread();
        final Client client;
        final Player player;
        final Map<Integer, Integer> varbits = new HashMap<>();
        final Map<Integer, Integer> varps = new HashMap<>();
        final Map<Integer, Widget> widgets = new HashMap<>();
        boolean spellInstalled;
        final List<MenuRequest> menu = java.util.Collections.synchronizedList(new ArrayList<>());
        Item[] inventory = new Item[0];
        Item[] equipment = new Item[0];
        int tab = 3;
        boolean inventoryAfterSelect = true;
        int energy;
        int warningCount;
        volatile Widget selectedWidget;
        boolean selectRequestedWidget;
        String spellName = "High Level Alchemy";
        int firstRune = 561;
        int secondRune = 554;
        int tick;
        int animation = -1;
        int hitpoints = 55;
        int agility = 55;
        int realAgility = 55;
        WorldPoint position = new WorldPoint(2704, 3464, 0);
        net.runelite.api.WorldView view;

        Fixture() throws Exception
        {
            ACTIVE_ACTIONS.add(actions);
            client = proxy(Client.class, this);
            player = proxy(Player.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "getWorldLocation": return position;
                    case "getWorldArea": return new WorldArea(position, 1, 1);
                    case "getAnimation": return animation;
                    case "getWorldView": return view;
                    default: return defaultValue(method.getReturnType());
                }
            });
            new MicrobotTestSupport(client, (point, entry) -> {
                MenuRequest request = new MenuRequest(entry == null
                    ? new Object[] {-1, 10485788, MenuAction.CC_OP, 1, -1, "Toggle Run", ""}
                    : new Object[] {entry.getParam0(), entry.getParam1(), entry.getType(), entry.getIdentifier(), entry.getItemId(), entry.getOption(), entry.getTarget()});
                menu.add(request);
                if (request.action == MenuAction.WIDGET_TARGET && selectRequestedWidget)
                {
                    selectedWidget = request.widget == 1000 ? nativeSpellWidget() : widgets.get(request.widget);
                    // Like the game, selecting an item spell shows the inventory again.
                    tab = inventoryAfterSelect ? 3 : tab;
                }
            });
            inject(actions, "client", client);
            inject(spells, "client", client);
            inject(spells, "actions", actions);
            inject(plugin, "client", client);
            widgets.put(14286848, widget(14286848, false));
            widgets.put(10485788, widget(10485788, false));
            widgets.put(9764864, proxy(Widget.class, (object, method, args) -> {
                if (method.getName().equals("getChildren"))
                {
                    Widget[] slots = new Widget[inventory.length];
                    for (int i = 0; i < slots.length; i++)
                    {
                        final int slot = i;
                        slots[i] = proxy(Widget.class, (o, m, a) -> {
                            if (m.getName().equals("getIndex")) return slot;
                            if (m.getName().equals("getItemId")) return inventory[slot].getId();
                            if (m.getName().equals("getActions")) return itemDefinition(inventory[slot].getId()).getInventoryActions();
                            return defaultValue(m.getReturnType());
                        });
                    }
                    return slots;
                }
                if (method.getName().equals("isHidden")) return false;
                return defaultValue(method.getReturnType());
            }));
            setInventory(new Item[0]);
        }

        MagicAction currentSpell()
        {
            return spellName.equals("Camelot Teleport") ? MagicAction.CAMELOT_TELEPORT : MagicAction.HIGH_LEVEL_ALCHEMY;
        }

        Widget nativeSpellWidget()
        {
            return proxy(Widget.class, (object, method, args) -> {
                switch (method.getName())
                {
                    case "getId": return 1000;
                    case "getName": return spellName;
                    case "getText": return "";
                    case "getSpriteId": return java.util.Arrays.stream(inventory).anyMatch(item -> item.getId() == firstRune)
                        && java.util.Arrays.stream(inventory).anyMatch(item -> item.getId() == secondRune) ? currentSpell().getSprite() : -1;
                    case "getBounds": return new java.awt.Rectangle(10, 10, 10, 10);
                    default: return defaultValue(method.getReturnType());
                }
            });
        }

        void setInventory(Item[] items)
        {
            inventory = items;
            net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.storeInventoryItemsInMemory(
                new net.runelite.api.events.ItemContainerChanged(93, client.getItemContainer(93)));
        }

        ItemComposition itemDefinition(int id)
        {
            return proxy(ItemComposition.class, (object, method, args) -> {
                if (method.getName().equals("getName")) return "Item " + id;
                if (method.getName().equals("getInventoryActions"))
                {
                    return new String[] {"Use", id >= 12625 && id <= 12631 || id >= 3008 && id <= 3022 ? "Drink" : "Eat", "Drop"};
                }
                return defaultValue(method.getReturnType());
            });
        }

        void dispatch() throws Exception
        {
            actions.dispatch();
            awaitWorker();
        }

        void awaitWorker() throws Exception
        {
            Field field = AgilityActions.class.getDeclaredField("pending");
            field.setAccessible(true);
            java.util.concurrent.Future<?> pending = (java.util.concurrent.Future<?>) field.get(actions);
            if (pending != null)
            {
                pending.get(7, java.util.concurrent.TimeUnit.SECONDS);
            }
        }

        CompleteLapTask seersTask() throws Exception
        {
            AgilityConfig config = new AgilityConfig()
            {
                @Override public AgilityCourse area() { return AgilityCourse.SEERS_VILLAGE; }
                @Override public boolean seersTeleport() { return true; }
            };
            inject(plugin, "config", config);
            return new CompleteLapTask(plugin, client, config, actions, spells);
        }

        void installCamelot()
        {
            installAlchemy();
            spellName = "Camelot Teleport";
            firstRune = 563;
            secondRune = 556;
            setInventory(inventory(563, 556));
        }

        void installAlchemy()
        {
            setInventory(inventory(856, 561, 554));
            spellInstalled = true;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments)
        {
            switch (method.getName())
            {
                case "isClientThread":
                    return Thread.currentThread() == owner;
                case "getItemContainer":
                    int containerId = (Integer) arguments[0];
                    return proxy(ItemContainer.class, (object, itemMethod, itemArguments) ->
                            itemMethod.getName().equals("getItems")
                            ? containerId == 93 ? inventory : equipment
                            : defaultValue(itemMethod.getReturnType()));
                case "getWidget":
                    int widget = arguments.length == 1 ? (Integer) arguments[0] : (Integer) arguments[0] << 16 | (Integer) arguments[1];
                    if (spellName != null && widget == 1000 && spellInstalled) return nativeSpellWidget();
                    if (widget == (218 << 16 | 3))
                    {
                        return proxy(Widget.class, (o, m, a) -> m.getName().equals("getStaticChildren")
                            ? (spellInstalled ? new Widget[] {nativeSpellWidget()} : new Widget[0])
                            : defaultValue(m.getReturnType()));
                    }
                    return widgets.get(widget);
                case "getWidgetRoots":
                    return spellInstalled ? new Widget[] {nativeSpellWidget()} : new Widget[0];
                case "getItemDefinition":
                    return itemDefinition((Integer) arguments[0]);
                case "getVarbitValue":
                    return varbits.getOrDefault((Integer) arguments[0], 0);
                case "getVarpValue":
                    return varps.getOrDefault((Integer) arguments[0], 0);
                case "getBoostedSkillLevel":
                    return arguments[0] == Skill.HITPOINTS ? hitpoints : arguments[0] == Skill.AGILITY ? agility : 55;
                case "getRealSkillLevel":
                    return arguments[0] == Skill.AGILITY ? realAgility : 55;
                case "getLocalPlayer":
                    return player;
                case "getTopLevelWorldView":
                    return view;
                case "getGameState":
                    return net.runelite.api.GameState.LOGGED_IN;
                case "getVarps":
                    return new int[5000];
                case "getVarcIntValue":
                    return tab;
                case "runScript":
                    Object[] script = (Object[]) arguments[0];
                    if (script.length > 1 && (Integer) script[0] == 915) tab = (Integer) script[1];
                    return null;
                case "getTickCount":
                    return tick;
                case "getCanvasWidth":
                case "getCanvasHeight":
                    return 1000;
                case "getEnergy":
                    return energy;
                case "getSelectedWidget":
                    return selectedWidget;
                case "isWidgetSelected":
                    return selectedWidget != null;
                case "setWidgetSelected":
                    if (!(Boolean) arguments[0])
                    {
                        selectedWidget = null;
                    }
                    return null;
                case "addChatMessage":
                    warningCount++;
                    return null;
                case "menuAction":
                    MenuRequest request = new MenuRequest(arguments);
                    menu.add(request);
                    if (request.action == MenuAction.WIDGET_TARGET && selectRequestedWidget)
                    {
                        selectedWidget = request.widget == 1000 ? nativeSpellWidget() : widgets.get(request.widget);
                    }
                    return null;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
