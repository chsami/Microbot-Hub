package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackCombatDetectionTest
{
    @Test
    void randomEventAddressingPlayerIsNotCombatButThugTargetingPlayerIs() throws Exception
    {
        BlackjackScript script = script(BlackjackTarget.MENAPHITE_THUG);
        try
        {
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, args) -> null);
            WorldPoint inside = new WorldPoint(3350, 2955, 0);
            assertFalse(script.isBlackjackAttacker(npc("Genie", 0, inside, player), player));
            assertFalse(script.isBlackjackAttacker(npc("Street urchin", 0, inside, player), player));
            assertTrue(script.isBlackjackAttacker(npc("Menaphite Thug", 55, inside, player), player));
            assertFalse(script.isBlackjackAttacker(npc("Menaphite Thug", 55, inside, null), player));
            assertFalse(script.isBlackjackAttacker(npc("Menaphite Thug", 55,
                    new WorldPoint(3360, 2960, 0), player), player));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void banditDetectionRespectsSelectedLevel() throws Exception
    {
        for (BlackjackTarget target : new BlackjackTarget[]{BlackjackTarget.BANDIT_41, BlackjackTarget.BANDIT_56})
        {
            BlackjackScript script = script(target);
            try
            {
                Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                        new Class<?>[]{Player.class}, (proxy, method, args) -> null);
                WorldPoint inside = new WorldPoint(3358, 2993, 0);
                int level = target == BlackjackTarget.BANDIT_41 ? 41 : 56;
                assertTrue(script.isBlackjackAttacker(npc("Bandit", level, inside, player), player));
                assertFalse(script.isBlackjackAttacker(npc("Bandit", level == 41 ? 56 : 41, inside, player), player));
                assertFalse(script.isBlackjackAttacker(npc("Genie", 0, inside, player), player));
                assertFalse(script.isBlackjackAttacker(npc("Bandit", level,
                        new WorldPoint(3350, 2954, 0), player), player));
            }
            finally
            {
                dispose(script);
            }
        }
    }

    private static Rs2NpcModel npc(String name, int level, WorldPoint tile, Player interacting)
    {
        NPC actor = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class},
                (proxy, method, args) -> method.getName().equals("getInteracting") ? interacting : null);
        return new Rs2NpcModel(actor)
        {
            @Override public String getName() { return name; }
            @Override public int getCombatLevel() { return level; }
            @Override public WorldPoint getWorldLocation() { return tile; }
        };
    }

    private static BlackjackScript script(BlackjackTarget target) throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        Field config = BlackjackScript.class.getDeclaredField("config");
        config.setAccessible(true);
        config.set(script, new BlackjackConfig() { @Override public BlackjackTarget target() { return target; } });
        return script;
    }

    private static void dispose(BlackjackScript script) throws Exception
    {
        Field executor = Script.class.getDeclaredField("scheduledExecutorService");
        executor.setAccessible(true);
        ((ScheduledExecutorService) executor.get(script)).shutdownNow();
    }
}
