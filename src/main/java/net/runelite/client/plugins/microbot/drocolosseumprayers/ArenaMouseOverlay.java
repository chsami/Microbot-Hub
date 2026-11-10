package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A fading trail of emitted positions and the native macOS arrow cursor; no timer or animation thread. */
public final class ArenaMouseOverlay extends Overlay
{
    private static final java.awt.image.BufferedImage CURSOR=loadCursor();
    private final ArenaMouse mouse;
    ArenaMouseOverlay(ArenaMouse mouse){
        this.mouse=mouse;setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);setPriority(Overlay.PRIORITY_HIGH);setNaughty();
    }
    public boolean ownsCursor(){return mouse.cursor()!=null&&!InputArbiter.isHuman();}
    @Override public Dimension render(Graphics2D graphics){
        if(!ownsCursor())return null;
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            long now=System.nanoTime();Point previous=null;
            g.setStroke(new BasicStroke(2));
            for(ArenaMouse.Sample sample:mouse.samples(now)){
                int alpha=(int)Math.max(0,Math.min(180,180*(1.0-(double)(now-sample.at)/ArenaMouse.TRAIL_NANOS)));
                g.setColor(new Color(70,225,255,alpha));
                if(previous!=null)g.drawLine(previous.getX(),previous.getY(),sample.point.getX(),sample.point.getY());
                previous=sample.point;
            }
            Point p=mouse.cursor();if(p==null)return null;
            // AppKit arrow: logical size 28x40, native hotspot (5,5). The tip tracks the click point.
            if(CURSOR!=null)g.drawImage(CURSOR,p.getX()-5,p.getY()-5,28,40,null);
        } finally {g.dispose();}
        return null;
    }
    private static java.awt.image.BufferedImage loadCursor(){
        try(java.io.InputStream in=ArenaMouseOverlay.class.getResourceAsStream("macos-arrow.png")){
            return in==null?null:javax.imageio.ImageIO.read(in);
        } catch(java.io.IOException unavailable){return null;}
    }
}
