package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.client.plugins.fishing.FishingPlugin;
import net.runelite.client.plugins.microbot.agentserver.AgentServerPlugin;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.astralrc.AstralRunesPlugin;
import net.runelite.client.plugins.microbot.autofishing.AutoFishingPlugin;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.plugins.microbot.kraken.KrakenPlugin;
import net.runelite.client.plugins.microbot.leftclickcast.LeftClickCastPlugin;
import net.runelite.client.plugins.microbot.sailing.MSailingPlugin;
import net.runelite.client.plugins.microbot.thieving.ThievingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.woodcutting.WoodcuttingPlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
		AIOFighterPlugin.class,
		AgentServerPlugin.class,
		LeftClickCastPlugin.class,
		KrakenPlugin.class
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
