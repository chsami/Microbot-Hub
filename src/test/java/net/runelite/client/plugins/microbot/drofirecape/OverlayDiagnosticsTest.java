package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayDiagnosticsTest {
    @Test public void compactCardRequestsFireCapeAndFitsCookerDimensions()throws Exception {
        FcControllers script=mock(FcControllers.class);
        when(script.state()).thenReturn("FIGHTING");
        when(script.monsterCount()).thenReturn("0");
        when(script.runtime()).thenReturn(3_720_000L);when(script.wave()).thenReturn(40);
        DroFirecapeConfig config=mock(DroFirecapeConfig.class);ItemManager items=mock(ItemManager.class);
        AsyncBufferedImage cape=new AsyncBufferedImage(null,36,32,BufferedImage.TYPE_INT_ARGB);
        when(items.getImage(6570)).thenReturn(cape);
        DroFirecapeOverlay overlay=new DroFirecapeOverlay(mock(DroFirecapePlugin.class),script,config,items);
        BufferedImage canvas=new BufferedImage(224,132,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=canvas.createGraphics();
        try {
            assertEquals(new Dimension(224,132),overlay.render(graphics));
            assertTrue(new Color(canvas.getRGB(110,120),true).getAlpha()>0);
            overlay.render(graphics);verify(items,times(1)).getImage(6570);
            String preview=System.getProperty("drofirecape.overlay.preview");
            if(preview!=null)javax.imageio.ImageIO.write(canvas,"png",new java.io.File(preview));
        }finally{graphics.dispose();}
    }
    @Test public void hideOverlayStopsDrawingWithoutDisablingLogs() {
        DroFirecapeScript script=new DroFirecapeScript();DroFirecapeConfig config=mock(DroFirecapeConfig.class);
        when(config.hideOverlay()).thenReturn(true);ItemManager items=mock(ItemManager.class);
        DroFirecapeOverlay overlay=new DroFirecapeOverlay(mock(DroFirecapePlugin.class),mock(FcControllers.class),config,items);
        Graphics2D graphics=new BufferedImage(224,132,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{assertNull(overlay.render(graphics));verifyNoInteractions(items);}finally{graphics.dispose();}
        assertTrue(FcDiagnostics.describe(script).contains("state=STOPPED"));
    }
    @Test public void formerOverlayDetailsRemainInLogText()throws Exception {
        Method method=EntryStartupTest.class.getDeclaredMethod("frame",int.class,int.class);method.setAccessible(true);
        FcFrame frame=(FcFrame)method.invoke(null,100,3024);
        DroFirecapeScript script=mock(DroFirecapeScript.class);
        when(script.frame()).thenReturn(frame);when(script.state()).thenReturn(DroFirecapeScript.State.ROTATION_WAIT);
        when(script.status()).thenReturn("Waiting for selected rotation");when(script.warning()).thenReturn("test warning");
        when(script.combatModeName()).thenReturn("Ranged");when(script.offensivePrayerName()).thenReturn("Eagle Eye");
        when(script.rotationStatus()).thenReturn("Rotation verified");when(script.plannerMode()).thenReturn("Minimize exposure");
        when(script.entryRotationText()).thenReturn("5 / any");when(script.entryClockText()).thenReturn("22s");
        when(script.entryGateStatus()).thenReturn("Ready");
        when(script.plan()).thenReturn(new Plan(new Tile(54,36),new Tile(53,36),Protection.RANGE,8,true,2,1,0,"Recorded wall shot"));
        String detail=FcDiagnostics.describe(script);
        for(String expected:new String[]{"status=Waiting for selected rotation","offence=Eagle Eye","warning=test warning",
            "hp=99/99","prayer=99/99","predictorCurrentDesired=5 / any","predictorClock=22s","gate=Ready",
            "cover=","step=","target=8","blocked=2","exposedStyles=1","risk=0","reason=Recorded wall shot"})
            assertTrue(expected,detail.contains(expected));
    }
}
