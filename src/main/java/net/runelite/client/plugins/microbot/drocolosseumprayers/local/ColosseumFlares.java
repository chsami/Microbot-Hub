package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/**
 * Observed Solarflare travel. Every recorded flare circles a 5x5 square clockwise: four steps along a side, then a
 * right turn at the corner (all 77,004 moves in the server and local recordings to 2026-11-02; no left turn, no side
 * of another length; tools/colosseum-local/flareturn.py). The servers stayed put 325 of 369 times the straight-ahead
 * tile was the player's but the flare turned away, and moved 144 of 157 times it really came.
 */
final class ColosseumFlares
{
    static final int SIDE=4;
    private final Map<Integer,Track> tracks=new HashMap<>();
    private long previousTick=-1;

    private static final class Track
    {
        final int id,x,y,dx,dy;
        /** Steps taken in (dx,dy) since the last turn, or -1 when the track began mid-side. */
        final int steps;
        /** Ticks since the last step. */
        final int still;
        final String key;
        /** First seen through a spawn event, so it stands on a corner until its first step. */
        boolean spawned;

        Track(Npc n,int dx,int dy,int steps,int still)
        {id=n.id;key=n.key;x=n.x;y=n.y;this.dx=dx;this.dy=dy;this.steps=steps;this.still=still;}

        /** At a corner the next step turns right; the normal cadence is one still tick, corners pause longer. */
        boolean turning()
        {return steps>=SIDE||steps<0&&still>=2;}
    }

    void reset()
    {tracks.clear();previousTick=-1;}

    void observe(ColosseumSnapshot f)
    {
        if(f.tick==previousTick)return;
        if(!f.instance||f.hp<=0){reset();return;}
        if(previousTick>=0&&f.tick!=previousTick+1)tracks.clear();
        previousTick=f.tick;

        Set<Integer> replaced=new HashSet<>();
        Set<String> replacedKeys=new HashSet<>();
        for(JsonElement value:f.events)
        {
            if(!value.isJsonObject())continue;
            JsonObject event=value.getAsJsonObject();String kind=string(event,"e","");
            if(!"spawn".equals(kind)&&!"despawn".equals(kind))continue;
            int index=integer(event,"index",integer(event,"actor",-1));
            if(index>=0)replaced.add(index);
            String key=string(event,"actor","");if(!key.isEmpty())replacedKeys.add(key);
        }

        Map<Integer,Track> present=new HashMap<>();
        for(Npc n:f.npcs)
        {
            if(!n.alive()||n.role!=Role.FLARE)continue;
            Track old=tracks.get(n.index);int dx=0,dy=0,steps=-1,still=0;
            if(old!=null&&old.id==n.id&&old.key.equals(n.key)
                &&!replaced.contains(n.index)&&!replacedKeys.contains(n.key))
            {
                int movedX=n.x-old.x,movedY=n.y-old.y;
                if(Math.abs(movedX)+Math.abs(movedY)==1)
                {
                    dx=movedX;dy=movedY;
                    // A flare spawns on a corner, so its first step starts a side; a turn always does.
                    boolean straight=movedX==old.dx&&movedY==old.dy;
                    steps=straight?(old.steps<0?-1:old.steps+1):old.dx!=0||old.dy!=0||old.spawned?1:-1;
                }
                else if(movedX==0&&movedY==0)
                {dx=old.dx;dy=old.dy;steps=old.steps;still=old.still+1;}
                // An unexplained jump or diagonal displacement does not reveal a travel vector.
            }
            Track track=new Track(n,dx,dy,steps,still);
            track.spawned=old==null?replaced.contains(n.index)||replacedKeys.contains(n.key):old.spawned&&dx==0&&dy==0;
            present.put(n.index,track);
        }
        tracks.clear();tracks.putAll(present);
    }

    /** The flare's next tile: straight ahead, or the right turn at a corner. Separate from its current footprint. */
    boolean projected(Tile tile)
    {
        for(Track flare:tracks.values())
        {
            if(flare.dx==0&&flare.dy==0)continue;
            int dx=flare.turning()?flare.dy:flare.dx,dy=flare.turning()?-flare.dx:flare.dy;
            if(tile.x==flare.x+dx&&tile.y==flare.y+dy)return true;
        }
        return false;
    }

    /**
     * {@code tile} is one of a flare's next {@code steps} tiles along its loop. Stepping ahead along that line only
     * puts the player on the flare's following tile (1.0.10 t940-943: one tile north, then caught two ticks later).
     */
    boolean onPath(Tile tile,int steps)
    {
        for(Track flare:tracks.values())
        {
            if(flare.dx==0&&flare.dy==0)continue;
            int x=flare.x,y=flare.y,dx=flare.dx,dy=flare.dy,side=flare.steps;
            for(int i=0;i<steps;i++)
            {
                boolean turn=i==0?flare.turning():side>=SIDE;
                if(turn){int t=dx;dx=dy;dy=-t;side=0;}
                x+=dx;y+=dy;if(side>=0)side++;
                if(tile.x==x&&tile.y==y)return true;
            }
        }
        return false;
    }
}
