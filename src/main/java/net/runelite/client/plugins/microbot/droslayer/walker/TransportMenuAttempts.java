package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.HashMap;
import java.util.Map;

/** A repeated unchanged dialogue gets one paced retry, then its enclosing travel timeout wins. */
final class TransportMenuAttempts {
    private final Map<String,long[]> attempts=new HashMap<>();
    boolean allow(String state,long now){
        long[] entry=attempts.get(state);
        if(entry==null){attempts.put(state,new long[]{1,now});return true;}
        if(entry[0]>=2||now-entry[1]<2500L)return false;
        entry[0]++;entry[1]=now;return true;
    }
}
