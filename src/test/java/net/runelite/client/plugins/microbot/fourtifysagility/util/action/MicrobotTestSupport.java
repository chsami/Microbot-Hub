package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.concurrent.Callable;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;

/** Records the real Microbot API transport without emitting input to a canvas. */
public final class MicrobotTestSupport implements AutoCloseable
{
    private static final Deque<MicrobotTestSupport> ACTIVE = new ArrayDeque<>();
    private final Object previousClient;
    private final Object previousMouse;
    private final Object previousThread;
    private final Object previousState;
    private final Object previousConfig;
    private final Object previousPlugins;
    private final Object previousItems;
    private final Object previousObjects;
    private final Object previousGround;
    private final java.util.Set<Integer> previousViews;
    private final VirtualMouse recorder;
    private boolean closed;

    public MicrobotTestSupport(Client client, BiConsumer<Point, NewMenuEntry> record)
    {
        previousClient = field("client", null, false);
        previousMouse = field("mouse", null, false);
        previousThread = field("clientThread", null, false);
        ClientThread clientThread = new ClientThread()
        {
            @Override
            public <T> T invoke(java.util.function.Supplier<T> operation)
            {
                return operation.get();
            }

            @Override
            public <T> Optional<T> runOnClientThreadOptional(Callable<T> operation, long timeout)
            {
                return runOnClientThreadOptional(operation);
            }

            @Override
            public void invoke(Runnable operation)
            {
                operation.run();
            }

            @Override
            public void invokeLater(Runnable operation)
            {
                operation.run();
            }

            @Override
            public <T> Optional<T> runOnClientThreadOptional(Callable<T> operation)
            {
                try
                {
                    return Optional.ofNullable(operation.call());
                }
                catch (Exception exception)
                {
                    throw new AssertionError(exception);
                }
            }
        };
        try
        {
            Field clientField = ClientThread.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(clientThread, client);
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
        field("clientThread", clientThread, true);
        previousState = field("rs2PlayerStateCache", null, false);
        field("rs2PlayerStateCache", new Rs2PlayerStateCache(new EventBus(), client, clientThread), true);
        previousViews = new java.util.HashSet<>(Microbot.getWorldViewIds());
        Microbot.getWorldViewIds().add(net.runelite.api.WorldView.TOPLEVEL);
        previousGround = field("rs2TileItemCache", null, false);
        field("rs2TileItemCache", new net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache(client, clientThread), true);
        previousObjects = field("rs2TileObjectCache", null, false);
        field("rs2TileObjectCache", new net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache(client, clientThread), true);
        previousConfig = field("configManager", null, false);
        try
        {
            java.lang.reflect.Constructor<?> constructor = net.runelite.client.config.ConfigManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            try
            {
                field("configManager", constructor.newInstance("test", scheduler, new EventBus(), client,
                    null, null, null, null), true);
            }
            finally
            {
                scheduler.shutdownNow();
            }
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
        previousPlugins = field("pluginManager", null, false);
        try
        {
            java.lang.reflect.Constructor<?> constructor = net.runelite.client.plugins.PluginManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            field("pluginManager", constructor.newInstance(false, new EventBus(), null, field("configManager", null, false), null, null), true);
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
        previousItems = field("itemManager", null, false);
        try
        {
            java.lang.reflect.Constructor<?> constructor = net.runelite.client.game.ItemManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            try
            {
                field("itemManager", constructor.newInstance(client, scheduler, clientThread, new EventBus(), null, null), true);
            }
            finally
            {
                scheduler.shutdownNow();
            }
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
        recorder = new VirtualMouse()
        {
            @Override
            public VirtualMouse click(Point point)
            {
                record.accept(point, null);
                return this;
            }

            @Override
            public VirtualMouse quickClick(Point point, NewMenuEntry entry)
            {
                record.accept(point, entry);
                return this;
            }

            @Override
            public VirtualMouse click(Point point, NewMenuEntry entry)
            {
                record.accept(point, entry);
                return this;
            }
        };
        field("client", client, true);
        field("mouse", recorder, true);
        ACTIVE.push(this);
    }

    public static void restoreAll()
    {
        while (!ACTIVE.isEmpty())
        {
            ACTIVE.peek().close();
        }
    }

    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        if (ACTIVE.peek() != this)
        {
            throw new IllegalStateException("Close input fixtures in reverse creation order");
        }
        ACTIVE.pop();
        field("client", previousClient, true);
        field("mouse", previousMouse, true);
        field("clientThread", previousThread, true);
        field("rs2PlayerStateCache", previousState, true);
        field("configManager", previousConfig, true);
        field("rs2TileObjectCache", previousObjects, true);
        Microbot.getWorldViewIds().clear();
        Microbot.getWorldViewIds().addAll(previousViews);
        field("pluginManager", previousPlugins, true);
        field("itemManager", previousItems, true);
        field("rs2TileItemCache", previousGround, true);
        recorder.shutdown();
        closed = true;
    }

    private static Object field(String name, Object value, boolean write)
    {
        try
        {
            Field field = Microbot.class.getDeclaredField(name);
            field.setAccessible(true);
            Object previous = field.get(null);
            if (write)
            {
                field.set(null, value);
            }
            return previous;
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
    }
}
