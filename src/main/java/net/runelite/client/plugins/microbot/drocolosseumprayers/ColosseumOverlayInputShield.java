package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.Canvas;
import java.awt.Point;
import java.awt.event.*;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.util.input.CanvasInputListener;
import net.runelite.client.plugins.microbot.util.input.StretchMapper;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

/** Excludes only real overlay gestures from the input arbiter; keyboard and game input stay intact. */
final class ColosseumOverlayInputShield extends MouseAdapter implements AutoCloseable {
    private final Predicate<Point> area;
    private final Predicate<Point> motionArea;
    private Canvas canvas;
    private MouseListener buttons;
    private MouseMotionListener motion;
    private final java.util.Set<Integer> captured=new java.util.HashSet<>();
    private final java.util.Set<Integer> forwarded=new java.util.HashSet<>();
    ColosseumOverlayInputShield(Predicate<Point> area){this(area,area);}
    ColosseumOverlayInputShield(Predicate<Point> area,Predicate<Point> motionArea){this.area=area;this.motionArea=motionArea;}
    void attach(Canvas next){
        if(next==canvas)return;
        close();if(next==null)return;
        for(MouseListener listener:next.getMouseListeners())if(listener instanceof CanvasInputListener){buttons=listener;break;}
        for(MouseMotionListener listener:next.getMouseMotionListeners())if(listener instanceof CanvasInputListener){motion=listener;break;}
        if(buttons==null||motion==null){buttons=null;motion=null;return;}
        canvas=next;
        next.removeMouseListener(buttons);next.removeMouseMotionListener(motion);
        next.addMouseListener(this);next.addMouseMotionListener(this);
    }
    private boolean inside(MouseEvent e){var p=StretchMapper.toCanvas(e.getX(),e.getY());return area.test(new Point(p.getX(),p.getY()));}
    private boolean blocksMotion(MouseEvent e){var p=StretchMapper.toCanvas(e.getX(),e.getY());return motionArea.test(new Point(p.getX(),p.getY()));}
    @Override public void mouseMoved(MouseEvent e){if(!BotEventGuard.isSynthetic()){captured.clear();if(!blocksMotion(e))motion.mouseMoved(e);}}
    @Override public void mouseDragged(MouseEvent e){if(!BotEventGuard.isSynthetic()&&captured.isEmpty()&&!blocksMotion(e))motion.mouseDragged(e);}
    @Override public void mousePressed(MouseEvent e){if(BotEventGuard.isSynthetic())return;if(inside(e)){if(blocksMotion(e))captured.add(e.getButton());else captured.remove(e.getButton());forwarded.remove(e.getButton());}else{captured.remove(e.getButton());forwarded.add(e.getButton());buttons.mousePressed(e);}}
    @Override public void mouseReleased(MouseEvent e){if(BotEventGuard.isSynthetic())return;if(forwarded.remove(e.getButton()))buttons.mouseReleased(e);captured.remove(e.getButton());}
    @Override public void mouseEntered(MouseEvent e){if(!BotEventGuard.isSynthetic()&&!blocksMotion(e))buttons.mouseEntered(e);}
    @Override public void mouseExited(MouseEvent e){if(!BotEventGuard.isSynthetic())buttons.mouseExited(e);}
    @Override public void close(){
        if(canvas!=null){canvas.removeMouseListener(this);canvas.removeMouseMotionListener(this);canvas.addMouseListener(buttons);canvas.addMouseMotionListener(motion);}
        canvas=null;buttons=null;motion=null;captured.clear();forwarded.clear();
    }
}
