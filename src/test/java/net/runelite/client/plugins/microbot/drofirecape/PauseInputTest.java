package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Final native press rechecks the live last monster after mouse travel. */
public class PauseInputTest {
    private static final class Input implements FcPrayerUi.Input {
        NewMenuEntry entry;int clicks;Runnable afterMove=()->{};
        public FcPrayerUi.View capture(InterfaceTab t,Rs2PrayerEnum p){return new FcPrayerUi.View(t.getVarcIntIndex(),-1,null,false);}
        public void key(int key){}
        public void move(Point p,BooleanSupplier permit){if(permit.getAsBoolean())afterMove.run();}
        public boolean click(Point p,NewMenuEntry e,BooleanSupplier permit){if(!permit.getAsBoolean())return false;entry=e;clicks++;return true;}
    }
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);final WorldView world=mock(WorldView.class);
        final Player player=mock(Player.class);final NPC npc=mock(NPC.class);
        final FcActions actions=new FcActions();final Input input=new Input();
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        final MockedStatic<Rs2Tab> tabs=mockStatic(Rs2Tab.class);
        Fixture()throws Exception {
            ClientThread thread=mock(ClientThread.class);microbot.when(Microbot::getClient).thenReturn(client);microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getWorld()).thenReturn(307);
            when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(world);when(client.getWorldView(anyInt())).thenReturn(world);
            when(world.getBaseX()).thenReturn(10304);when(world.getBaseY()).thenReturn(5248);when(world.isInstance()).thenReturn(true);
            int[][][] chunks=new int[4][13][13];for(int x=0;x<13;x++)for(int y=0;y<13;y++)chunks[0][x][y]=((2368/8+x)<<14)|((5056/8+y)<<3);
            when(world.getInstanceTemplateChunks()).thenReturn(chunks);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(28*128+64,28*128+64,WorldView.TOPLEVEL));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(10332,5276,0));when(player.getInteracting()).thenReturn(npc);
            when(npc.getWorldView()).thenReturn(world);when(npc.getName()).thenReturn("Ket-Zek");
            NPCComposition composition=mock(NPCComposition.class);when(composition.getSize()).thenReturn(5);when(npc.getTransformedComposition()).thenReturn(composition);
            when(npc.getHealthRatio()).thenReturn(-1);when(npc.getHealthScale()).thenReturn(-1);
            Rs2NpcModel cached=mock(Rs2NpcModel.class);when(cached.getNpc()).thenReturn(npc);
            Rs2NpcCache cache=mock(Rs2NpcCache.class,RETURNS_DEEP_STUBS);when(cache.query().toList()).thenReturn(List.of(cached));
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            Widget button=mock(Widget.class);when(client.getWidget(182,8)).thenReturn(button);when(button.getBounds()).thenReturn(new Rectangle(700,300,100,30));
            when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(800);
            tabs.when(Rs2Tab::getCurrentTab).thenReturn(InterfaceTab.LOGOUT);
            RecoveryPolicyTest.set(actions,"prayerUi",new FcPrayerUi(input,()->true,()->10000));
        }
        public void close(){tabs.close();microbot.close();}
    }
    @Test public void ongoingFightEmitsOneNativeLogoutClick()throws Exception {
        try(Fixture f=new Fixture()){
            assertTrue(f.actions.requestWavePause(307,()->true));assertEquals(1,f.input.clicks);
            assertEquals("Logout",f.input.entry.getOption());assertEquals(MenuAction.CC_OP,f.input.entry.getType());
            assertEquals((182<<16)|8,f.input.entry.getParam1());assertEquals(1,f.input.entry.getIdentifier());
        }
    }
    @Test public void finalKillDuringMouseTravelCancelsLogoutPress()throws Exception {
        try(Fixture f=new Fixture()) {
            f.input.afterMove=()->when(f.npc.isDead()).thenReturn(true);
            assertFalse(f.actions.requestWavePause(307,()->true));assertEquals(0,f.input.clicks);
        }
    }
    @Test public void changedWaveOrWorldCancelsLogoutBeforePress()throws Exception {
        try(Fixture f=new Fixture()) {
            final boolean[] current={true};f.input.afterMove=()->current[0]=false;
            assertFalse(f.actions.requestWavePause(307,()->current[0]));assertEquals(0,f.input.clicks);
            assertFalse(f.actions.requestWavePause(308,()->true));assertEquals(0,f.input.clicks);
        }
    }
    @Test public void worldSwitcherUsesItsLogoutOperationNotWorldSelection()throws Exception {
        try(Fixture f=new Fixture()) {
            Widget button=mock(Widget.class);when(f.client.getWidget(69,25)).thenReturn(button);
            when(button.getBounds()).thenReturn(new Rectangle(700,320,100,30));
            assertTrue(f.actions.requestWavePause(307,()->true));
            assertEquals((69<<16)|25,f.input.entry.getParam1());assertEquals("Logout",f.input.entry.getOption());
        }
    }
    @Test public void missingLogoutCannotClickWorldSwitcherButton()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getWidget(182,8)).thenReturn(null);
            Widget switcher=mock(Widget.class);when(f.client.getWidget(182,6)).thenReturn(switcher);
            when(switcher.getBounds()).thenReturn(new Rectangle(700,300,100,30));
            assertFalse(f.actions.requestWavePause(307,()->true));assertEquals(0,f.input.clicks);
        }
    }

}
