package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Target acquisition must catch the prayer worker's opening without changing its schedule. */
public class AttackHandoffTest {
    private void set(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    private void handoff(boolean opens)throws Exception {
        try(MockedStatic<Microbot> microbot=mockStatic(Microbot.class)) {
            Client client=mock(Client.class);WorldView view=mock(WorldView.class);Player player=mock(Player.class);
            microbot.when(Microbot::getClient).thenReturn(client);
            FcActions actions=mock(FcActions.class);FcTickPrayers driver=mock(FcTickPrayers.class);
            DroFirecapeScript script=new DroFirecapeScript();DroFirecapeConfig config=mock(DroFirecapeConfig.class);
            set(script,"actions",actions);set(script,"tickPrayers",driver);set(script,"config",config);set(script,"enabled",true);
            when(client.getTickCount()).thenReturn(100);when(client.getWorld()).thenReturn(630);
            when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
            when(client.getWorldView(anyInt())).thenReturn(view);when(client.getTopLevelWorldView()).thenReturn(view);
            when(view.getBaseX()).thenReturn(10304);when(view.getBaseY()).thenReturn(5248);when(view.isInstance()).thenReturn(true);
            int[][][] chunks=new int[4][13][13];
            for(int x=0;x<13;x++)for(int y=0;y<13;y++)chunks[0][x][y]=((2368/8+x)<<14)|((5056/8+y)<<3);
            when(view.getInstanceTemplateChunks()).thenReturn(chunks);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(28*128+64,28*128+64,WorldView.TOPLEVEL));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(10332,5276,0));
            Mob ranger=new Mob(7,Kind.RANGER,new Tile(23,28),3,10,10,98,Protection.RANGE,true);
            Constructor<FcFrame> ctor=FcFrame.class.getDeclaredConstructor(Client.class,WorldView.class,Player.class,
                List.class,CollisionGrid.class,Protection.class,Map.class,int.class,Set.class,Map.class,boolean.class);
            ctor.setAccessible(true);
            FcFrame frame=ctor.newInstance(client,view,player,List.of(ranger),new CollisionGrid(new int[64][64]),
                Protection.MAGIC,Map.of(),7,Set.of(),Map.of(),false);
            set(script,"frame",frame);
            AtomicInteger polls=new AtomicInteger();when(driver.ownsInput()).thenReturn(true);
            when(driver.attackInputWindow(eq(100),anyLong())).thenAnswer(a->polls.incrementAndGet()>=3&&opens&&(long)a.getArgument(1)<=250);
            when(actions.attackProtection(any())).thenReturn(Protection.RANGE);
            when(actions.combatProtect(any())).thenAnswer(a->polls.get()>=3&&opens);
            when(actions.overheadActive(any())).thenAnswer(a->polls.get()>=3&&opens);
            when(actions.attack(eq(7),anyString(),eq(true),eq(frame))).thenReturn(true);
            Method method=DroFirecapeScript.class.getDeclaredMethod("tryImmediateAttack",FcFrame.class,Snapshot.class,Protection.class);
            method.setAccessible(true);
            boolean paused=Microbot.pauseAllScripts.get();Microbot.pauseAllScripts.set(false);
            try {
                assertTrue((Boolean)method.invoke(script,frame,frame.model,Protection.NONE));
                if(opens)verify(actions).attack(eq(7),anyString(),eq(true),eq(frame));
                else verify(actions,never()).attack(anyInt(),anyString(),anyBoolean(),any());
                verify(driver,never()).primeMovement(anyInt(),any());
                verify(driver,never()).pulse(any());
            }finally{Microbot.pauseAllScripts.set(paused);}
        }
    }
    @Test public void latePrayerAcknowledgementStillDispatchesRangerInSameControllerPass()throws Exception {handoff(true);}
    @Test public void noConfirmedWindowNeverBypassesProtection()throws Exception {handoff(false);}
}
