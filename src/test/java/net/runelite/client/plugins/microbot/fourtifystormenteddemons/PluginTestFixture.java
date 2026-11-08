package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.overlay.TormentedDemonsOverlay;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * The plugin wired to the fake client. A player stands in the Plinth chamber with a Ring of dueling, food and one
 * demon three tiles away, so the first decision is an attack request.
 */
final class PluginTestFixture
{
    static final int RING = 2552;
    static final int FOOD = 385;
    static final int STAFF = 4675;
    static final int CROSSBOW = 9185;
    final TestWorld world = new TestWorld();
    final TestWorld.QueuedThread thread = new TestWorld.QueuedThread();
    final MutableConfig config = new MutableConfig();
    final FourtifysTormentedDemonsPlugin plugin = new FourtifysTormentedDemonsPlugin();
    final DemonActions actions = new DemonActions(world.client, thread, null, TestWorld.DirectExecutor::new);
    final TestWorld.DemonTarget demon;
    final OverlayManager overlays;
    final List<String> notifications = new ArrayList<>();

    PluginTestFixture() throws Exception
    {
        world.inventory = TestWorld.items(RING, 1, FOOD, 5);
        // Two equipment styles are needed to start; both are worn so no switch is due.
        config.magic = Integer.toString(STAFF);
        config.ranged = Integer.toString(CROSSBOW);
        world.equipment = TestWorld.items(STAFF, 1, CROSSBOW, 1);
        demon = world.demon(3);
        ConfigManager manager = configManager();
        Constructor<?> constructor = OverlayManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int index = 0; index < types.length; index++)
        {
            if (types[index] == ConfigManager.class)
            {
                arguments[index] = manager;
            }
            else if (types[index] == RuneLiteConfig.class)
            {
                arguments[index] = manager.getConfig(RuneLiteConfig.class);
            }
            else if (types[index] == Client.class)
            {
                arguments[index] = world.client;
            }
        }
        overlays = (OverlayManager) constructor.newInstance(arguments);
        inject("client", world.client);
        inject("clientThread", thread);
        inject("config", config);
        inject("actions", actions);
        inject("walking", new Walking(world.client, thread));
        inject("overlays", overlays);
        inject("notifier", notifier());
        inject("overlay", new TormentedDemonsOverlay(plugin, config, world.client, new TooltipManager()));
    }

    void enable()
    {
        plugin.startUp();
    }

    void disable()
    {
        plugin.shutDown();
    }

    void enableAndDrain()
    {
        enable();
        thread.drain();
    }

    /** Runs the game tick's decisions without giving the client tick a chance to dispatch. */
    void decisionOnly()
    {
        world.gameCycle += 30;
        plugin.onGameTick(new GameTick());
    }

    void clientTick()
    {
        plugin.onClientTick(new ClientTick());
    }

    void tick()
    {
        decisionOnly();
        clientTick();
    }

    void standAt(WorldPoint point)
    {
        world.position = point;
    }

    long requests()
    {
        return actions.getRequestsDispatched();
    }

    boolean overlayShown()
    {
        return overlays.anyMatch(overlay -> overlay instanceof TormentedDemonsOverlay);
    }

    void inject(String name, Object value) throws Exception
    {
        Field field = FourtifysTormentedDemonsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    Object read(String name) throws Exception
    {
        Field field = FourtifysTormentedDemonsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    /** A notifier that only posts its notification event, which the fixture records; nothing is shown. */
    private Notifier notifier() throws Exception
    {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Notifier notifier = (Notifier) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, Notifier.class);
        RuneLiteConfig quiet = TestWorld.proxy(RuneLiteConfig.class, (name, args) ->
        {
            switch (name)
            {
                case "sendNotificationsWhenFocused":
                    return true;
                case "notificationRequestFocus":
                    return RequestFocusType.OFF;
                case "notificationSound":
                    return NotificationSound.OFF;
                case "flashNotification":
                    return FlashNotification.DISABLED;
                default:
                    return null;
            }
        });
        EventBus events = new EventBus();
        events.register(new Object()
        {
            @Subscribe
            public void onNotificationFired(NotificationFired event)
            {
                notifications.add(event.getMessage());
            }
        });
        Map<String, Object> fields = Map.of("runeLiteConfig", quiet, "eventBus", events, "client", world.client);
        for (Map.Entry<String, Object> field : fields.entrySet())
        {
            Field target = Notifier.class.getDeclaredField(field.getKey());
            target.setAccessible(true);
            target.set(notifier, field.getValue());
        }
        return notifier;
    }

    private static ConfigManager configManager() throws Exception
    {
        ScheduledExecutorService scheduler = TestWorld.proxy(ScheduledExecutorService.class, (name, args) -> null);
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int index = 0; index < types.length; index++)
        {
            if (types[index] == ScheduledExecutorService.class)
            {
                arguments[index] = scheduler;
            }
            else if (types[index] == EventBus.class)
            {
                arguments[index] = new EventBus();
            }
            else if (types[index] == Gson.class)
            {
                arguments[index] = new Gson();
            }
        }
        ConfigManager manager = (ConfigManager) constructor.newInstance(arguments);
        Class<?> dataClass = Class.forName("net.runelite.client.config.ConfigData");
        Constructor<?> dataConstructor = dataClass.getDeclaredConstructor(File.class);
        dataConstructor.setAccessible(true);
        Object data = dataConstructor.newInstance(new File("build/test-overlay-settings.properties"));
        Field field = ConfigManager.class.getDeclaredField("configProfile");
        field.setAccessible(true);
        field.set(manager, data);
        return manager;
    }
}
