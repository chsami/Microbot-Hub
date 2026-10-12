package net.runelite.client.plugins.microbot.drozulrah;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Bounded key turns, observed framing, and guaranteed key release; no fight ownership. */
@Slf4j
final class ZulrahTravelCamera {
    static boolean turnToObject(Rs2TileObjectModel object) {
        if(object==null)return false;
        int offset=Rs2Random.betweenInclusive(-4,4);
        int tolerance=Rs2Random.betweenInclusive(8,14);
        Integer rawAngle=read(()->Rs2Camera.getObjectAngle(object));
        if(rawAngle==null)return false;
        int angle=(rawAngle+offset+360)%360;
        log.info("[Dro] Zulrah travel Camera turn offset={}deg tolerance={}deg startYaw={} pitch={}",offset,tolerance,
            read(()->Microbot.getClient().getCameraYaw()),read(()->Rs2Camera.getPitch()));
        if(!turnYaw(angle,tolerance))return false;
        // A narrow yaw error alone does not prove the object is in the viewport.
        if(!objectVisible(object)) {
            int targetPitch=Rs2Random.betweenInclusive(330,370);
            Integer pitch=read(()->Rs2Camera.getPitch());
            if(pitch==null)return false;
            if(Math.abs(pitch-targetPitch)>5) {
                int key=pitch<targetPitch?KeyEvent.VK_UP:KeyEvent.VK_DOWN;
                Rs2Keyboard.keyHold(key);
                try {
                    sleepUntil(()-> {
                        Integer current=read(()->Rs2Camera.getPitch());
                        return Thread.currentThread().isInterrupted() || current!=null
                            && (key==KeyEvent.VK_UP?current>=targetPitch:current<=targetPitch);
                    }, () -> {}, 3000L, 20);
                } finally { Rs2Keyboard.keyRelease(key); }
            }
            if(!objectVisible(object) && !turnYaw(rawAngle,4))return false;
        }
        boolean visible=objectVisible(object);
        log.info("[Dro] Zulrah travel Camera finished yaw={} pitch={} targetVisible={}",
            read(()->Microbot.getClient().getCameraYaw()),read(()->Rs2Camera.getPitch()),visible);
        return visible;
    }
    private static boolean turnYaw(int angle,int tolerance) {
        Integer error=read(()->Rs2Camera.getAngleTo(angle));
        if(error==null)return false;
        if(Math.abs(error)>tolerance) {
            int key=error>0?KeyEvent.VK_LEFT:KeyEvent.VK_RIGHT;
            Rs2Keyboard.keyHold(key);
            try {
                sleepUntil(()-> {
                    Integer remaining=read(()->Rs2Camera.getAngleTo(angle));
                    return Thread.currentThread().isInterrupted() || remaining!=null
                        && (Math.abs(remaining)<=tolerance || (error>0?remaining<0:remaining>0));
                }, () -> {}, 5000L, 20);
            } finally { Rs2Keyboard.keyRelease(key); }
        }
        Integer finalError=read(()->Rs2Camera.getAngleTo(angle));
        log.info("[Dro] Zulrah travel camera stop target={}deg remaining={}deg tolerance={}deg",angle,finalError,tolerance);
        return !Thread.currentThread().isInterrupted() && finalError!=null && Math.abs(finalError)<=tolerance;
    }
    static boolean objectVisible(Rs2TileObjectModel object) {
        return object!=null && Boolean.TRUE.equals(read(()-> {
            Shape shape=object.getClickbox();
            Rectangle viewport=new Rectangle(Microbot.getClient().getViewportXOffset()+8,
                Microbot.getClient().getViewportYOffset()+8,
                Math.max(1,Microbot.getClient().getViewportWidth()-16),Math.max(1,Microbot.getClient().getViewportHeight()-16));
            return shape!=null && !shape.getBounds().isEmpty() && viewport.contains(shape.getBounds())
                && Rs2Camera.isTileOnScreen(object.getLocalLocation());
        }));
    }
    private static <T> T read(Supplier<T> supplier) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> supplier.get()).orElse(null);
    }
}
