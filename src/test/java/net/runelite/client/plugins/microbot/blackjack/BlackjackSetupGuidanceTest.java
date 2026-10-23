package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackSetupGuidanceTest
{
    @Test
    void guidanceExplainsPairingSuppliesAndBanditPreparation()
    {
        BlackjackConfig config = new BlackjackConfig() {};
        assertTrue(config.guide().contains("BOTH"));
        assertTrue(config.guide().contains("full inventory"));
        assertTrue(config.guide().contains("blackjack equipped"));
        assertTrue(config.caveats().contains("not feature-complete"));
        assertTrue(config.caveats().contains("manually lure"));
        assertTrue(config.showBanditHouse());
    }

    @Test
    void houseHighlightUsesBanditGeometryAndRespectsToggle() throws Exception
    {
        assertTrue(BlackjackScript.banditHouseArea().contains(new WorldPoint(3358, 2993, 0)));
        assertFalse(BlackjackScript.banditHouseArea().contains(new WorldPoint(3350, 2954, 0)));
        for (BlackjackTarget target : new BlackjackTarget[]{BlackjackTarget.BANDIT_41, BlackjackTarget.BANDIT_56, BlackjackTarget.MENAPHITE_THUG})
        {
            for (boolean enabled : new boolean[]{true, false})
            {
                BlackjackScript script = new BlackjackScript();
                try
                {
                    Field field = BlackjackScript.class.getDeclaredField("config");
                    field.setAccessible(true);
                    field.set(script, new BlackjackConfig() {
                        @Override public BlackjackTarget target() { return target; }
                        @Override public boolean showBanditHouse() { return enabled; }
                    });
                    assertEquals(enabled && target != BlackjackTarget.MENAPHITE_THUG, script.shouldHighlightBanditHouse());
                }
                finally
                {
                    Field field = Script.class.getDeclaredField("scheduledExecutorService");
                    field.setAccessible(true);
                    ((ScheduledExecutorService) field.get(script)).shutdownNow();
                }
            }
        }
    }
}
