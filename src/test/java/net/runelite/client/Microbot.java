package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.client.plugins.microbot.slayer.SlayerPlugin;
import net.runelite.client.plugins.microbot.lunarplankmake.LunarPlankMakePlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
            SlayerPlugin.class,
            LunarPlankMakePlugin.class,
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
