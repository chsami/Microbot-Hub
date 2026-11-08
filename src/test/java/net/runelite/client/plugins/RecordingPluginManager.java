package net.runelite.client.plugins;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.Microbot;

/** Records the plugins that Microbot switches off, in place of the client's plugin manager, until closed. */
public final class RecordingPluginManager extends PluginManager implements AutoCloseable
{
    public final List<Plugin> stopped = new ArrayList<>();
    private PluginManager previous;

    private RecordingPluginManager()
    {
        super(false, null, null, null, null, null);
    }

    public static RecordingPluginManager install() throws ReflectiveOperationException
    {
        RecordingPluginManager manager = new RecordingPluginManager();
        Field field = field();
        manager.previous = (PluginManager) field.get(null);
        field.set(null, manager);
        return manager;
    }

    /** Puts back the plugin manager that was installed before this one. */
    @Override
    public void close() throws ReflectiveOperationException
    {
        field().set(null, previous);
    }

    private static Field field() throws ReflectiveOperationException
    {
        Field field = Microbot.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        return field;
    }

    @Override
    public void setPluginEnabled(Plugin plugin, boolean enabled)
    {
    }

    @Override
    public boolean stopPlugin(Plugin plugin)
    {
        stopped.add(plugin);
        return true;
    }
}
