package net.runelite.client.plugins.microbot.autofarmer;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathScript;
/** Holds manual map walking without disabling the pathfinder used by the farmer. */
final class NavigationGuard {
    private final Supplier<ShortestPathScript> lookup;
    private ShortestPathScript script;
    private boolean originalWalking;
    NavigationGuard() { this(NavigationGuard::find); }
    NavigationGuard(Supplier<ShortestPathScript> lookup) { this.lookup=lookup; }
    private static ShortestPathScript find() {
        ShortestPathPlugin plugin=Microbot.getPlugin(ShortestPathPlugin.class);
        if(plugin==null)return null;
        try {
            java.lang.reflect.Field field=ShortestPathPlugin.class.getDeclaredField("shortestPathScript");field.setAccessible(true);
            return (ShortestPathScript)field.get(plugin);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot acquire manual navigation control for this client version",e);}
    }
    synchronized void acquire() {
        if(script!=null)return;
        ShortestPathScript found=lookup.get();
        if(found==null)return;
        script=found;originalWalking=script.isWalkingEnabled();
        // Cancel the stale destination before any farmer walk starts.
        script.setTriggerWalker(null);
        if(script.isWalkingEnabled())script.toggleWalking();
    }
    synchronized String enforce() {
        if(script==null){acquire();return null;}
        if(!script.isWalkingEnabled() && script.getTriggerWalker()==null)return null;
        String conflict="Competing manual Shortest Path route: " + script.getTriggerWalker();
        if(script.isWalkingEnabled())script.toggleWalking();
        script.setTriggerWalker(null);
        return conflict;
    }
    synchronized void release() {
        if(script==null)return;
        // Restore the prior setting, never the stale destination.
        script.setTriggerWalker(null);
        if(script.isWalkingEnabled()!=originalWalking)script.toggleWalking();
        script=null;
    }
}
