package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.droefficientwalker.WalkerMouseProfile.ParkSide;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.OvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WalkerMouseProfileTest {
    @Test public void clickVariationStaysWithinSafeInsetEvenForSmallClickboxes() {
        WalkerMouseProfile profile=new WalkerMouseProfile();
        for(Rectangle bounds:new Rectangle[]{new Rectangle(70,90,1,1),new Rectangle(70,90,2,3),new Rectangle(70,90,40,60)}) {
            int inset=Math.min(3,Math.min(bounds.width,bounds.height)/3);
            for(int i=0;i<500;i++) {
                Point point=profile.randomPoint(bounds);
                assertTrue(bounds.contains(point.getX(),point.getY()));
                assertTrue(point.getX()>=bounds.x+inset && point.getX()<bounds.x+bounds.width-inset);
                assertTrue(point.getY()>=bounds.y+inset && point.getY()<bounds.y+bounds.height-inset);
            }
        }
        assertNull(profile.randomPoint(new Rectangle()));assertNull(profile.randomPoint(null));
    }
    @Test public void fixedEdgeParkingPreservesCursorAlignmentAndRandomEdgeForTheSession() {
        WalkerMouseProfile profile=new WalkerMouseProfile();Point current=new Point(400,250);
        ParkSide session=profile.side(ParkSide.RANDOM);
        for(int i=0;i<100;i++) {
            assertEquals(session,profile.side(ParkSide.RANDOM));
            for(ParkSide side:new ParkSide[]{ParkSide.LEFT,ParkSide.RIGHT,ParkSide.TOP,ParkSide.BOTTOM}) {
                Point out=profile.parkPoint(side,current,800,600);
                Point edge=WalkerMouseProfile.canvasEdge(out,800,600);
                assertFalse(new Rectangle(0,0,800,600).contains(out.getX(),out.getY()));
                assertTrue(new Rectangle(0,0,800,600).contains(edge.getX(),edge.getY()));
                if(side==ParkSide.LEFT||side==ParkSide.RIGHT)assertEquals(current.getY(),out.getY());
                else assertEquals(current.getX(),out.getX());
            }
        }
        assertNull(profile.parkPoint(ParkSide.NONE,current,800,600));
    }
    @Test public void minimapMotionRemovesOvershootWithoutChangingSharedFactory() {
        DefaultMouseMotionNature nature=new DefaultMouseMotionNature();
        OvershootManager overshoots=mock(OvershootManager.class);
        when(overshoots.getOvershoots(any(Flow.class),anyLong(),anyDouble())).thenReturn(2);
        nature.setOvershootManager(overshoots);
        MouseMotionFactory source=new MouseMotionFactory(nature);
        MouseMotionFactory precise=WalkerMouseProfile.motion(source,true);
        MouseMotionFactory canvas=WalkerMouseProfile.motion(source,false);
        assertNotSame(nature,precise.getNature());
        assertSame(overshoots,source.getOvershootManager());assertSame(overshoots,canvas.getOvershootManager());
        assertEquals(0,precise.getOvershootManager().getOvershoots(new Flow(new double[]{1}),500,200));
        assertSame(source.getSpeedManager(),precise.getSpeedManager());
        assertSame(source.getNoiseProvider(),precise.getNoiseProvider());
        assertSame(source.getDeviationProvider(),precise.getDeviationProvider());
    }
    @Test public void minimapAndDisabledMouseCannotScheduleIdleExtras() throws Exception {
        Client client=mock(Client.class);Player player=mock(Player.class);EfficientWalkerConfig config=mock(EfficientWalkerConfig.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
        when(player.getPoseAnimation()).thenReturn(10);when(player.getIdlePoseAnimation()).thenReturn(1);when(player.getAnimation()).thenReturn(-1);
        when(config.useMicrobotMouse()).thenReturn(true);when(config.walkerMouseParkSide()).thenReturn(ParkSide.NONE);
        try {
            WalkerMouseControl.configure(config);
            field("lastClickMinimap").setBoolean(null,true);
            WalkerMouseControl.tick(client,true);assertFalse(field("idleAllowed").getBoolean(null));
            field("lastClickMinimap").setBoolean(null,false);
            WalkerMouseControl.tick(client,true);assertTrue(field("idleAllowed").getBoolean(null));
            when(config.useMicrobotMouse()).thenReturn(false);
            WalkerMouseControl.tick(client,true);assertFalse(field("idleAllowed").getBoolean(null));
        } finally {WalkerMouseControl.shutdown();}
    }
    private Field field(String name) throws Exception {Field field=WalkerTestReflection.declaredField(WalkerMouseControl.class, name);field.setAccessible(true);return field;}
}
