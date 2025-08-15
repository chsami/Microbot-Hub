package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.pestcontrol.PestControlPlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
		PestControlPlugin.class
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).toList();
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
