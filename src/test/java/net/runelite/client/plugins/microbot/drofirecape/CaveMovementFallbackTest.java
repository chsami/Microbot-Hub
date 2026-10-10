package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Replays the stuck bat's unchanged player position through actual movement dispatch. */
public class CaveMovementFallbackTest {
    private static final Tile COVER=new Tile(54,36),WEST=new Tile(53,36);
    private static void set(Object object,String name,Object value)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    private static Object get(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Object call(Object object,String name,Class<?>[] types,Object... args)throws Exception {
        Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);
    }
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);final WorldView view=mock(WorldView.class);
        final Player player=mock(Player.class);final Mouse mouse=mock(Mouse.class);
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        final MockedStatic<InputArbiter> arbiter=mockStatic(InputArbiter.class);
        final MockedStatic<Perspective> perspective=mockStatic(Perspective.class);
        final MockedStatic<Rs2MiniMap> minimap=mockStatic(Rs2MiniMap.class);
        final boolean previousPause=Microbot.pauseAllScripts.get();
        Fixture() {
            Microbot.pauseAllScripts.set(false);
            ClientThread thread=mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(a->Optional.ofNullable(((Callable<?>)a.getArgument(0)).call()));
            microbot.when(Microbot::getClient).thenReturn(client);microbot.when(Microbot::getClientThread).thenReturn(thread);
            microbot.when(Microbot::getMouse).thenReturn(mouse);microbot.when(Microbot::isLoggedIn).thenReturn(true);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            when(client.getWorld()).thenReturn(307);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(view);
            when(client.getWorldView(anyInt())).thenReturn(view);
            when(view.getBaseX()).thenReturn(10304);when(view.getBaseY()).thenReturn(5248);when(view.isInstance()).thenReturn(true);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            int[][][] chunks=new int[4][13][13];
            for(int x=0;x<13;x++)for(int y=0;y<13;y++)chunks[0][x][y]=((2368/8+x)<<14)|((5056/8+y)<<3);
            when(view.getInstanceTemplateChunks()).thenReturn(chunks);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(54*128+64,36*128+64,WorldView.TOPLEVEL));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(10358,5284,0));
            when(client.getBoostedSkillLevel(any())).thenReturn(99);when(client.getRealSkillLevel(any())).thenReturn(99);
            when(client.getCanvasWidth()).thenReturn(800);when(client.getCanvasHeight()).thenReturn(600);
            when(client.getViewportWidth()).thenReturn(600);when(client.getViewportHeight()).thenReturn(450);
            perspective.when(()->Perspective.localToCanvas(eq(client),any(LocalPoint.class),eq(0))).thenReturn(new Point(300,200));
            minimap.when(()->Rs2MiniMap.worldToMinimap(any())).thenReturn(new Point(690,100));
            minimap.when(()->Rs2MiniMap.isPointInsideMinimap(any())).thenReturn(true);
        }
        FcFrame frame(int tick,List<Mob> mobs,CollisionGrid grid)throws Exception {
            when(client.getTickCount()).thenReturn(tick);
            Constructor<FcFrame> ctor=FcFrame.class.getDeclaredConstructor(Client.class,WorldView.class,Player.class,
                List.class,CollisionGrid.class,Protection.class,Map.class,int.class,Set.class,Map.class,boolean.class);
            ctor.setAccessible(true);
            FcFrame f=ctor.newInstance(client,view,player,mobs,grid,Protection.NONE,Map.of(),7,Set.of(),
                Map.of(RecordedLureBook.ITALY,COVER,RecordedLureBook.WEST_PEEK,new Tile(52,36)),false);
            assertTrue(f.cave);return f;
        }
        FcFrame frame(int tick)throws Exception{return frame(tick,List.of(),new CollisionGrid(new int[104][104]));}
        public void close(){minimap.close();perspective.close();arbiter.close();microbot.close();Microbot.pauseAllScripts.set(previousPause);}
    }
    @Test public void failedShortStepClicksOnlyItsVisibleGroundPointThroughNativeInput()throws Exception {
        try(Fixture f=new Fixture()) {
            assertTrue(new FcActions().move(f.frame(100),WEST,false));
            f.microbot.verify(()->Microbot.doInvoke(argThat((NewMenuEntry e)->e.getType()==MenuAction.WALK&&e.getParam0()==300&&e.getParam1()==200),eq(new Rectangle(300,200,1,1))));
            verifyNoInteractions(f.mouse);f.minimap.verifyNoInteractions();
        }
    }
    @Test public void invisibleOrOutsideViewportFallbackNeverClicksAnEdge()throws Exception {
        for(Point point:new Point[]{null,new Point(700,200),new Point(1,200),new Point(300,599)})try(Fixture f=new Fixture()) {
            f.perspective.when(()->Perspective.localToCanvas(eq(f.client),any(LocalPoint.class),eq(0))).thenReturn(point);
            assertFalse(new FcActions().move(f.frame(100),WEST,false));
            f.microbot.verify(()->Microbot.doInvoke(any(),any()),never());verifyNoInteractions(f.mouse);
        }
    }
    @Test public void longMovementKeepsExistingMinimapInput()throws Exception {
        try(Fixture f=new Fixture()) {
            assertTrue(new FcActions().move(f.frame(100),COVER.add(-8,0),true));
            verify(f.mouse).click(new Point(690,100));f.microbot.verify(()->Microbot.doInvoke(any(),any()),never());
            assertFalse(new FcActions().move(f.frame(100),COVER.add(-5,0),false));
        }
    }
    @Test public void realDispatchChangesInputAfterNoMovementAndBoundsRepeatedCommands()throws Exception {
        try(Fixture f=new Fixture()) {
            DroFirecapeScript script=new DroFirecapeScript();FcActions actions=mock(FcActions.class);
            set(script,"actions",actions);set(script,"config",mock(DroFirecapeConfig.class));
            when(actions.combatProtect(any())).thenReturn(true);
            when(actions.move(any(),any(),anyBoolean())).thenReturn(true);
            Plan plan=new Plan(WEST,WEST,Protection.NONE,-1,true,0,0,0,"Recorded bat step");
            for(int tick:new int[]{100,103,106,109,110,118,119}) {
                FcFrame frame=f.frame(tick);set(script,"frame",frame);
                call(script,"movePlan",new Class<?>[]{FcFrame.class,Plan.class},frame,plan);
            }
            verify(actions,never()).move(any(),eq(WEST),eq(true));
            verify(actions,times(4)).move(any(),eq(WEST),eq(false));
            // No click on the player's own tile, even while a previous command is pending.
            FcFrame frame=f.frame(120);set(script,"frame",frame);
            call(script,"movePlan",new Class<?>[]{FcFrame.class,Plan.class},frame,new Plan(COVER,COVER,Protection.NONE,-1,true,0,0,0,"Hold safe tile"));
            verify(actions,times(4)).move(any(),any(),anyBoolean());
        }
    }
    @Test public void generalWatchdogUsesOneWestStepAndDoesNotFireDuringProgressOrJad()throws Exception {
        try(Fixture f=new Fixture()) {
            DroFirecapeScript script=new DroFirecapeScript();FcActions actions=mock(FcActions.class);
            DroFirecapeConfig config=mock(DroFirecapeConfig.class);when(config.demonstrationLures()).thenReturn(true);
            set(script,"actions",actions);set(script,"config",config);
            when(actions.combatProtect(any())).thenReturn(true);when(actions.move(any(),any(),anyBoolean())).thenReturn(true);
            int[][] flags=new int[104][104];for(int y=26;y<=35;y++)flags[54][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
            Mob bat=new Mob(61459,Kind.BAT,new Tile(54,25),1,-1,-1,-1,Protection.MELEE,true);
            FcFrame frame=f.frame(100,List.of(bat),new CollisionGrid(flags));set(script,"frame",frame);
            long now=System.currentTimeMillis();set(script,"lastProgressAt",now-6100);
            assertTrue((Boolean)call(script,"recoverStalledCombat",new Class<?>[]{FcFrame.class,long.class},frame,now));
            verify(actions).move(frame,WEST,false);assertTrue(((LureController)get(script,"lures")).hasPendingReturn());
            assertFalse((Boolean)call(script,"recoverStalledCombat",new Class<?>[]{FcFrame.class,long.class},frame,now+100));
            set(script,"lastProgressAt",now+7000);
            assertFalse((Boolean)call(script,"recoverStalledCombat",new Class<?>[]{FcFrame.class,long.class},frame,now+7100));
            Mob jad=new Mob(2,Kind.JAD,new Tile(40,40),5,-1,-1,-1,Protection.MAGIC,true);
            frame=f.frame(120,List.of(jad),new CollisionGrid(flags));set(script,"lastProgressAt",now-60000);
            assertFalse((Boolean)call(script,"recoverStalledCombat",new Class<?>[]{FcFrame.class,long.class},frame,now+20000));
        }
    }
    @Test public void movingMonstersDelayRecoveryButRepeatedUnchangedFramesDoNot()throws Exception {
        try(Fixture f=new Fixture()) {
            DroFirecapeScript script=new DroFirecapeScript();
            Mob bat=new Mob(61459,Kind.BAT,new Tile(54,25),1,-1,-1,-1,Protection.MELEE,true);
            FcFrame frame=f.frame(100,List.of(bat),new CollisionGrid(new int[104][104]));
            call(script,"observeRecoveryMotion",new Class<?>[]{FcFrame.class},frame);
            long old=System.currentTimeMillis()-6100;set(script,"lastRecoveryMotionAt",old);
            call(script,"observeRecoveryMotion",new Class<?>[]{FcFrame.class},frame);
            assertEquals(old,get(script,"lastRecoveryMotionAt"));
            Mob approaching=new Mob(61459,Kind.BAT,new Tile(54,26),1,-1,-1,-1,Protection.MELEE,true);
            FcFrame moved=f.frame(101,List.of(approaching),new CollisionGrid(new int[104][104]));
            call(script,"observeRecoveryMotion",new Class<?>[]{FcFrame.class},moved);
            assertTrue((Long)get(script,"lastRecoveryMotionAt")>old);
            set(script,"lastProgressAt",old);
            assertFalse((Boolean)call(script,"recoverStalledCombat",new Class<?>[]{FcFrame.class,long.class},moved,System.currentTimeMillis()));
        }
    }
}
