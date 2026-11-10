package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Arrays;
import java.util.function.*;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;

/** Recorded local Quick-exit only, after local combat input has stopped. */
public final class InfernoExit {
    enum Scene { INSIDE, OUTSIDE, UNKNOWN }
    interface Port {Scene scene();boolean allowed();boolean quickExit();}
    private final Port port;
    private final LongSupplier clock;
    private long started=-1,nextAttempt;
    private int attempts;
    private boolean blocked;
    private String status="Leaving Inferno through Cave exit Quick-exit";
    public InfernoExit(BooleanSupplier allowed){this(new LocalPort(allowed),System::currentTimeMillis);}
    InfernoExit(Port port,LongSupplier clock){this.port=port;this.clock=clock;}
    public String status(){return status;}
    public boolean failed(){return blocked;}
    public boolean tick(){
        Scene scene=port.scene();
        if(scene==Scene.OUTSIDE){status="Inferno exit confirmed outside instance";return true;}
        if(scene==Scene.UNKNOWN){status="Waiting for a loaded scene to confirm Inferno exit";return false;}
        if(!port.allowed()){status="Waiting for input control before Inferno Quick-exit";return false;}
        long now=clock.getAsLong();if(started<0)started=now;
        if(blocked||now-started>=30_000L||attempts>=3&&now>=nextAttempt){
            blocked=true;status="Inferno Quick-exit not confirmed; leave manually before the next task";return false;
        }
        if(now>=nextAttempt){
            nextAttempt=now+8_000L;
            if(port.quickExit()){attempts++;status="Inferno Quick-exit requested; waiting for outside arrival";}
            else status="Recorded Cave exit 30283 not available; waiting for Quick-exit";
        }
        return false;
    }
    private static final class LocalPort implements Port {
        private final BooleanSupplier allowed;
        LocalPort(BooleanSupplier allowed){this.allowed=allowed;}
        public boolean allowed(){return allowed.getAsBoolean();}
        public Scene scene(){return Microbot.getClientThread().runOnClientThreadOptional(()->{
            var c=Microbot.getClient();
            if(c==null||c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null||c.getTopLevelWorldView()==null)return Scene.UNKNOWN;
            if(!c.getTopLevelWorldView().isInstance())return Scene.OUTSIDE;
            return InfernoBoundary.inside(c)?Scene.INSIDE:Scene.UNKNOWN;
        }).orElse(Scene.UNKNOWN);}
        public boolean quickExit(){
            if(!allowed()||Thread.currentThread().isInterrupted()||Microbot.getClientThread().isClientThread()||scene()!=Scene.INSIDE)return false;
            var object=Microbot.getClientThread().runOnClientThreadOptional(()->{
                var c=Microbot.getClient();
                if(!allowed()||!InfernoBoundary.inside(c)||c.isWidgetSelected())return null;
                return Microbot.getRs2TileObjectCache().query().where(o->{
                    var comp=o.getObjectComposition();
                    return o.getId()==30283&&o.getWorldView()==c.getTopLevelWorldView()&&comp!=null&&comp.getId()==30283
                        &&"Cave exit".equals(comp.getName())&&comp.getActions()!=null&&Arrays.asList(comp.getActions()).contains("Quick-exit");
                }).nearestOnClientThread();
            }).orElse(null);
            return object!=null&&allowed()&&object.click("Quick-exit");
        }
    }
}
