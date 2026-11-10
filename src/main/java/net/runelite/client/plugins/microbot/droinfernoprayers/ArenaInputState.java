package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.LongSupplier;
import net.runelite.api.Client;

/** Client-event publication of arena eligibility. Input checkpoints never hop to the client thread. */
final class ArenaInputState {
    private final LongSupplier clock;
    private volatile Snapshot snapshot=new Snapshot(false,0);
    private static final class Snapshot {
        final boolean allowed;final long at;
        Snapshot(boolean allowed,long at){this.allowed=allowed;this.at=at;}
    }
    ArenaInputState(){this(System::nanoTime);}
    ArenaInputState(LongSupplier clock){this.clock=clock;}
    /** Client thread only. Death/game-state events also invalidate immediately. */
    void observe(Client client,ArenaProduct product){snapshot=new Snapshot(product.inside(client),clock.getAsLong());}
    void clear(){snapshot=new Snapshot(false,0);}
    boolean allowed(){
        Snapshot current=snapshot;long age=clock.getAsLong()-current.at;
        return current.allowed&&age>=0&&age<=1_200_000_000L;
    }
}
