package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.client.plugins.fishing.FishingPlugin;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.astralrc.AstralRunesPlugin;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingPlugin;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.plugins.microbot.tutorialisland.TutorialIslandPlugin;
import net.runelite.client.plugins.microbot.LT.Bones.BonesPlugin;
import net.runelite.client.plugins.microbot.LT.MossKiller.MossKillerPlugin;
import net.runelite.client.plugins.microbot.LT.nateplugins.moneymaking.natehumidifier.HumidifierPlugin;
import net.runelite.client.plugins.microbot.LT.nateplugins.skilling.arrowmaker.ArrowPlugin;
import net.runelite.client.plugins.microbot.LT.Pizza.PizzaPlugin;
import net.runelite.client.plugins.microbot.LT.SummerPie.SummerPiesPlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
            SummerPiesPlugin.class
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
