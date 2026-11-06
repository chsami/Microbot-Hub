package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NpcAttackOptionsTest
{
    @Test
    public void onlyHiddenNpcSettingReturnsAnActionableProblemWithoutWritingSettings() throws Exception
    {
        for (int option : new int[] {0, 1, 2, 3})
        {
            List<Integer> reads = new ArrayList<>();
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, arguments) ->
                {
                    if (!method.getName().equals("getVarpValue"))
                    {
                        throw new AssertionError("Unexpected client operation: " + method.getName());
                    }
                    int id = (Integer) arguments[0];
                    reads.add(id);
                    return id == VarPlayerID.OPTION_ATTACKPRIORITY_NPC ? option : 3;
                });
            BarrowsActions actions = new BarrowsActions();
            Field field = BarrowsActions.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(actions, client);
            String problem = actions.npcAttackProblem();
            if (option == 3)
            {
                assertTrue(problem.contains("NPC Attack options are set to Hidden"));
                assertTrue(problem.contains("Settings > Controls"));
                assertTrue(problem.endsWith("then enable the plugin again"));
            }
            else
            {
                assertNull(problem);
            }
            assertEquals(List.of(VarPlayerID.OPTION_ATTACKPRIORITY_NPC), reads);
        }
    }
}
