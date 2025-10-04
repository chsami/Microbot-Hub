package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.client.plugins.fishing.FishingPlugin;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.astralrc.AstralRunesPlugin;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingPlugin;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.plugins.microbot.MossKiller.MossKillerPlugin;
import net.runelite.client.plugins.microbot.CRONOVISOR.CRONOVISORPlugin;
import net.runelite.client.plugins.microbot.tutorialisland.TutorialIslandPlugin;
import net.runelite.client.plugins.microbot.Pizza.PizzaPlugin;
import net.runelite.client.plugins.microbot.SummerPie.SummerPiesPlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
            MossKillerPlugin.class,
            CRONOVISORPlugin.class,
            TutorialIslandPlugin.class,
            PizzaPlugin.class,
            SummerPiesPlugin.class
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
