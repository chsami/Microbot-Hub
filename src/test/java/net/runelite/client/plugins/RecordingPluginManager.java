package net.runelite.client.plugins;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.Microbot;

/** Records the plugins that Microbot switches off, in place of the client's plugin manager. */
public final class RecordingPluginManager extends PluginManager
{
    public final List<Plugin> stopped = new ArrayList<>();

    private RecordingPluginManager()
    {
        super(false, null, null, null, null, null);
    }

    public static RecordingPluginManager install() throws ReflectiveOperationException
    {
        RecordingPluginManager manager = new RecordingPluginManager();
        Field field = Microbot.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, manager);
        return manager;
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
