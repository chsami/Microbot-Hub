package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinigameRecordedSequenceTest {
    @Test void landingBudgetIncludesExtraTimeAfterFullChannel() throws Exception {
        var fixed=WalkerTestReflection.declaredField(NetworkTransportSupport.class, "MINIGAME_FIXED_CHANNEL_MS");fixed.setAccessible(true);
        var grace=WalkerTestReflection.declaredField(NetworkTransportSupport.class, "MINIGAME_LANDING_GRACE_MS");grace.setAccessible(true);
        assertEquals(15000L,fixed.getLong(null));assertEquals(10000L,grace.getLong(null));
    }
    private static Object fightPit()throws Exception {
        Class<?> type=Class.forName(NetworkTransportSupport.class.getName()+"$EdgeType");
        Class<?> edge=Class.forName(NetworkTransportSupport.class.getName()+"$NetworkEdge");
        Constructor<?> ctor=edge.getDeclaredConstructors()[0];ctor.setAccessible(true);
        return ctor.newInstance(Enum.valueOf((Class)type,"MINIGAME"),new WorldPoint(3162,3489,0),new WorldPoint(2399,5177,0),"TzHaar Fight Pit","Grouping",-1,0);
    }
}
