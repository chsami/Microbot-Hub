package net.runelite.client.plugins.microbot.fourtifysgemmining;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.overlay.GemMiningOverlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

final class PluginTestFixture
{
    final TestWorld world = new TestWorld();
    final FourtifysGemMiningPlugin plugin = new FourtifysGemMiningPlugin();
    final QueuedThread thread = new QueuedThread();
    final GemMiningConfig config;
    final OverlayManager overlays;
    final FakeWalking walking;
    final EventBus events = new EventBus();
    final Deque<MenuEntry> pendingMenus = new ArrayDeque<>();

    PluginTestFixture() throws Exception
    {
        this(new MutableConfig());
    }

    PluginTestFixture(GemMiningConfig config) throws Exception
    {
        this.config = config;
        world.onMenuRequest = pendingMenus::addLast;
        world.rock(2825, 3000);
        Constructor<?> constructor = OverlayManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ConfigManager manager = configManager();
        walking = new FakeWalking(world, thread);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == ConfigManager.class)
            {
                arguments[i] = manager;
            }
            else if (types[i] == RuneLiteConfig.class)
            {
                arguments[i] = manager.getConfig(RuneLiteConfig.class);
            }
            else if (types[i] == Client.class)
            {
                arguments[i] = world.client;
            }
        }
        overlays = (OverlayManager) constructor.newInstance(arguments);
        inject("client", world.client);
        inject("clientThread", thread);
        inject("config", config);
        inject("actions", world.actions);
        inject("walking", walking);
        inject("overlays", overlays);
        inject("overlay", new GemMiningOverlay(plugin, config, world.client, new TooltipManager()));
        events.register(plugin);
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

    void decisionOnly()
    {
        world.gameCycle += 30;
        plugin.onGameTick(new GameTick());
    }

    void clientTick()
    {
        plugin.onClientTick(new ClientTick());
        deliverMenus();
    }

    void deliverMenus()
    {
        while (!pendingMenus.isEmpty())
        {
            events.post(new MenuOptionClicked(pendingMenus.removeFirst()));
        }
    }

    void tick()
    {
        decisionOnly();
        clientTick();
    }

    void chat(ChatMessageType type, String message)
    {
        events.post(new ChatMessage(null, type, "", message, "", 0));
    }

    void manualBagAction(String option)
    {
        int bagSlot = -1;
        for (int slot = 0; slot < world.inventory.length; slot++)
        {
            int id = world.inventory[slot].getId();
            if (id == 12020 || id == 24481)
            {
                bagSlot = slot;
                break;
            }
        }
        if (bagSlot < 0)
        {
            throw new IllegalStateException("No gem bag in inventory");
        }
        int selectedSlot = bagSlot;
        int itemId = world.inventory[bagSlot].getId();
        Widget child = world.client.getWidget(149, 0).getChild(selectedSlot);
        int op = Arrays.asList(child.getActions()).indexOf(option) + 1;
        if (op == 0)
        {
            throw new IllegalArgumentException("Bag action unavailable: " + option);
        }
        MenuEntry entry = TestWorld.proxy(MenuEntry.class, (name, args) -> {
            switch (name)
            {
                case "getOption":
                    return option;
                case "getTarget":
                    return itemId == 12020 ? "Gem bag" : "Open gem bag";
                case "getIdentifier":
                    return op;
                case "getType":
                    return op <= 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY;
                case "getParam0":
                    return selectedSlot;
                case "getParam1":
                    return 149 << 16;
                case "getItemId":
                    return itemId;
                case "getItemOp":
                    return option.equals("Fill") ? 1 : option.equals("Check") ? 3 : option.equals("Empty") ? 4 : 2;
                case "getWidget":
                    return child;
                case "isItemOp":
                    return true;
                default:
                    return null;
            }
        });
        events.post(new MenuOptionClicked(entry));
    }

    void inject(String name, Object value) throws Exception
    {
        Field field = FourtifysGemMiningPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    static ConfigManager configManager() throws Exception
    {
        ScheduledExecutorService scheduler = TestWorld.proxy(ScheduledExecutorService.class, (name, args) -> null);
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == ScheduledExecutorService.class)
            {
                arguments[i] = scheduler;
            }
            else if (types[i] == EventBus.class)
            {
                arguments[i] = new EventBus();
            }
            else if (types[i] == Gson.class)
            {
                arguments[i] = new Gson();
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

    static final class MutableConfig implements GemMiningConfig
    {
        GemMine mine = GemMine.SURFACE;
        int minDelayTicks;
        int maxDelayTicks;

        @Override
        public GemMine mine()
        {
            return mine;
        }

        @Override
        public int tickDelayMin()
        {
            return minDelayTicks;
        }

        @Override
        public int tickDelayMax()
        {
            return maxDelayTicks;
        }
    }

    static final class QueuedThread extends ClientThread
    {
        final Deque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void invokeLater(Runnable task)
        {
            pending.addLast(task);
        }

        void runNext()
        {
            pending.removeFirst().run();
        }

        void drain()
        {
            while (!pending.isEmpty())
            {
                runNext();
            }
        }
    }
}
