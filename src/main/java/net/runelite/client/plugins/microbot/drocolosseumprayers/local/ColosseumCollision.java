/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
/*
 * Line-of-sight traversal adapted from RuneLite WorldArea.
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.Npc;

/** Directional collision and bounded routes in live template coordinates. */
final class ColosseumCollision
{
    private static final int UNLOADED = 0x1000000;
    private static final int BLOCKED = 0x100 | 0x40000 | 0x200000 | UNLOADED;
    private static final int MISSILE_BLOCKED = 0x20000 | UNLOADED;
    private final int x1, y1, width, height;
    private final int[] flags;
    private final boolean[] occupied;
    private final boolean valid;
    private final ColosseumSnapshot snapshot;

    ColosseumCollision(ColosseumSnapshot snapshot)
    {
        this.snapshot = snapshot;
        JsonObject collision = ColosseumSnapshot.object(snapshot.raw, "collision");
        x1 = ColosseumSnapshot.integer(collision, "x1", 1792);
        y1 = ColosseumSnapshot.integer(collision, "y1", 3072);
        int w = ColosseumSnapshot.integer(collision, "x2", x1 - 1) - x1 + 1;
        int h = ColosseumSnapshot.integer(collision, "y2", y1 - 1) - y1 + 1;
        JsonArray data = ColosseumSnapshot.array(collision, "flags");
        valid = w > 0 && h > 0 && w <= 104 && h <= 104 && data.size() == w * h;
        width = valid ? w : 1; height = valid ? h : 1;
        flags = new int[width * height]; occupied = new boolean[flags.length];
        if (!valid) { Arrays.fill(flags, UNLOADED); return; }
        for (int i = 0; i < flags.length; i++)
        {
            JsonElement e = data.get(i);
            try { flags[i] = e.getAsInt(); }
            catch (RuntimeException invalid) { flags[i] = UNLOADED; }
        }
        for (Npc npc : snapshot.npcs) if (npc.alive())
            for (int x = npc.x; x < npc.x + npc.size; x++)
                for (int y = npc.y; y < npc.y + npc.size; y++)
                    if (inside(x, y)) occupied[index(x, y)] = true;
    }

    boolean available() { return valid && open(snapshot.x, snapshot.y); }
    boolean inside(int x, int y) { return x >= x1 && y >= y1 && x < x1 + width && y < y1 + height; }
    int flag(int x, int y) { return inside(x, y) ? flags[index(x, y)] : UNLOADED; }
    boolean open(int x, int y) { return valid && inside(x, y) && (flag(x, y) & BLOCKED) == 0; }
    boolean free(int x, int y) { return open(x, y) && !occupied[index(x, y)]; }
    private int index(int x, int y) { return (y - y1) * width + x - x1; }
    private Tile tile(int index) { return new Tile(x1 + index % width, y1 + index / width); }

    private boolean cardinal(int x, int y, int nx, int ny)
    {
        int dx = nx - x, dy = ny - y;
        if (Math.abs(dx) + Math.abs(dy) != 1 || !open(nx, ny)) return false;
        int out = dx > 0 ? 8 : dx < 0 ? 128 : dy > 0 ? 2 : 32;
        int in = dx > 0 ? 128 : dx < 0 ? 8 : dy > 0 ? 32 : 2;
        return (flag(x, y) & out) == 0 && (flag(nx, ny) & in) == 0;
    }

    boolean step(Tile from, Tile to)
    {
        return step(from, to, true);
    }

    /** A player step: walls block it, NPC footprints do not (the game walks the player through them). */
    boolean playerStep(Tile from, Tile to)
    {
        return step(from, to, false);
    }

    private boolean step(Tile from, Tile to, boolean npcBlocks)
    {
        int dx = to.x - from.x, dy = to.y - from.y;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || !passable(to.x, to.y, npcBlocks)) return false;
        if (dx == 0 && dy == 0) return true;
        if (dx == 0 || dy == 0) return cardinal(from.x, from.y, to.x, to.y);
        int outgoing = dx < 0 ? (dy > 0 ? 1 : 64) : (dy > 0 ? 4 : 16);
        int incoming = dx < 0 ? (dy > 0 ? 16 : 4) : (dy > 0 ? 64 : 1);
        return (flag(from.x, from.y) & outgoing) == 0 && (flag(to.x, to.y) & incoming) == 0
            && passable(from.x + dx, from.y, npcBlocks) && passable(from.x, from.y + dy, npcBlocks)
            && cardinal(from.x, from.y, from.x + dx, from.y)
            && cardinal(from.x, from.y, from.x, from.y + dy)
            && cardinal(from.x + dx, from.y, to.x, to.y)
            && cardinal(from.x, from.y + dy, to.x, to.y);
    }

    private boolean passable(int x, int y, boolean npcBlocks)
    {
        return npcBlocks ? free(x, y) : open(x, y);
    }

    /** Match Main's WorldArea dominant-axis fixed-point traversal, including directional rounding. */
    boolean ray(int x, int y, int tx, int ty)
    {
        if (!valid || !inside(x, y) || !inside(tx, ty)) return false;
        if ((flag(x, y) & UNLOADED) != 0 || (flag(tx, ty) & UNLOADED) != 0) return false;
        int dx = tx - x, dy = ty - y;
        if (dx == 0 && dy == 0) return true;
        int xMask = MISSILE_BLOCKED | ((dx < 0 ? 8 : 128) << 9);
        int yMask = MISSILE_BLOCKED | ((dy < 0 ? 2 : 32) << 9);
        if (Math.abs(dx) > Math.abs(dy))
            return rayAxis(x - x1, y - y1, tx - x1, dy, Math.abs(dx), xMask, yMask, true);
        return rayAxis(y - y1, x - x1, ty - y1, dx, Math.abs(dy), yMask, xMask, false);
    }

    /** Check the entered major-axis tile before applying the minor-axis slope, as WorldArea does. */
    private boolean rayAxis(int major, int minor, int target, int minorDelta, int majorDistance,
                            int majorMask, int minorMask, boolean horizontal)
    {
        int minorFixed = (minor << 16) + 0x8000 - (minorDelta < 0 ? 1 : 0);
        int slope = (minorDelta << 16) / majorDistance;
        int direction = Integer.signum(target - major);
        while (major != target)
        {
            major += direction;
            int currentMinor = minorFixed >>> 16;
            if ((rayFlag(major, currentMinor, horizontal) & majorMask) != 0) return false;
            minorFixed += slope;
            int nextMinor = minorFixed >>> 16;
            if (nextMinor != currentMinor && (rayFlag(major, nextMinor, horizontal) & minorMask) != 0) return false;
        }
        return true;
    }

    private int rayFlag(int major, int minor, boolean horizontal)
    {
        return horizontal ? flag(x1 + major, y1 + minor) : flag(x1 + minor, y1 + major);
    }

    boolean playerSight(Tile player, Npc npc)
    {
        if (npc.distance(player.x, player.y) == 0) return false;
        for (int x = npc.x; x < npc.x + npc.size; x++)
            for (int y = npc.y; y < npc.y + npc.size; y++)
                if (facing(npc, player, x, y) && ray(player.x, player.y, x, y)) return true;
        return false;
    }

    boolean npcSight(Npc npc, Tile player)
    {
        if (!valid) return true; // Missing collision cannot certify protection behind cover.
        for (int x = npc.x; x < npc.x + npc.size; x++)
            for (int y = npc.y; y < npc.y + npc.size; y++)
                if (facing(npc, player, x, y) && ray(x, y, player.x, player.y)) return true;
        return false;
    }

    /** Recorded enemy firing checks the player-to-closest-footprint ray, unlike WorldArea sight. */
    boolean firingSight(Npc npc, Tile player)
    {
        if (npc.distance(player.x, player.y) == 0) return false;
        if (!valid) return true; // Preserve the existing unknown-collision protection contract.
        int x = Math.max(npc.x, Math.min(npc.x + npc.size - 1, player.x));
        int y = Math.max(npc.y, Math.min(npc.y + npc.size - 1, player.y));
        return ray(player.x, player.y, x, y);
    }

    /** One observed movement step projected for protection only; never an executable NPC route. */
    boolean approachingSight(Npc npc,Tile player,int dx,int dy)
    {
        if(!valid||Math.abs(dx)>1||Math.abs(dy)>1||dx==0&&dy==0)return false;
        int nx=npc.x+dx,ny=npc.y+dy;
        for(int x=nx;x<nx+npc.size;x++)for(int y=ny;y<ny+npc.size;y++)if(!open(x,y))return false;
        if(dx!=0)
        {
            int edge=dx>0?npc.x+npc.size-1:npc.x;
            for(int y=npc.y;y<npc.y+npc.size;y++)if(!cardinal(edge,y,edge+dx,y))return false;
        }
        if(dy!=0)
        {
            int edge=dy>0?npc.y+npc.size-1:npc.y;
            for(int x=nx;x<nx+npc.size;x++)if(!cardinal(x,edge,x,edge+dy))return false;
        }
        for(int x=nx;x<nx+npc.size;x++)for(int y=ny;y<ny+npc.size;y++)
            if((player.x<nx&&x==nx||player.x>=nx+npc.size&&x==nx+npc.size-1
                ||player.y<ny&&y==ny||player.y>=ny+npc.size&&y==ny+npc.size-1)&&ray(x,y,player.x,player.y))return true;
        return false;
    }

    /** One native footprint step toward the current player; this does not issue an NPC route. */
    Tile npcApproach(Npc npc,Tile player)
    {
        return npcApproach(npc,player,npc.x,npc.y);
    }

    private Tile npcApproach(Npc npc,Tile player,int x,int y)
    {
        return npcApproach(npc,player,x,y,true);
    }

    /**
     * @param npcsBlock other enemies' footprints stop the step, and a melee actor stops at diagonal distance one. False
     *                  (walls only, for the walking Jaguar claw): at distance one it takes the x step when the walls
     *                  allow it, as recorded Jaguars did beside a still player (84 of 85 frames).
     */
    private Tile npcApproach(Npc npc,Tile player,int x,int y,boolean npcsBlock)
    {
        Tile current=new Tile(x,y);
        int distance=Math.max(0,Math.max(Math.max(x-player.x,player.x-x-npc.size+1),Math.max(y-player.y,player.y-y-npc.size+1)));
        int dx=player.x<x?-1:player.x>=x+npc.size?1:0;
        int dy=player.y<y?-1:player.y>=y+npc.size?1:0;
        if(!npcsBlock&&valid&&npc.meleeOnly()&&distance==1)
            return dx!=0&&dy!=0&&npcCanTravel(npc,x,y,dx,0,false)?new Tile(x+dx,y):current;
        // The observed melee actors stop at diagonal distance one as well. They cannot
        // step through that corner merely because a cardinal melee edge is unavailable.
        if(!valid||(npc.meleeOnly()?distance<=1:distance<=15&&npcFiringAt(npc,player,current)))return current;
        if(dx==0&&dy==0)return current;
        if(npcCanTravel(npc,x,y,dx,dy,npcsBlock))return new Tile(x+dx,y+dy);
        if(dx!=0&&npcCanTravel(npc,x,y,dx,0,npcsBlock))return new Tile(x+dx,y);
        if(dy!=0&&npcCanTravel(npc,x,y,0,dy,npcsBlock))return new Tile(x,y+dy);
        return current;
    }

    boolean npcFiringAfterApproach(Npc npc,Tile player)
    {
        return npcFiringAt(npc,player,npcApproach(npc,player));
    }

    /** Firing sight within {@code steps} native approach steps toward {@code player} (at most four). */
    /**
     * {@link #npcFiringAfterApproach} with enemies in their death animation treated as gone: the tile opens within about
     * three ticks and a shooter waiting on it steps in and fires that tick (1.0.65; live 2026-11-05 16:09 wave 5 t1835-1838:
     * a Jaguar died beside the NW pillar, the Shaman behind it stepped onto its tile and cast, 24 unprotected).
     */
    boolean npcFiringAfterApproachPastDying(Npc npc,Tile player)
    {
        dyingOpens=true;
        try{return npcFiringAfterApproach(npc,player);}finally{dyingOpens=false;}
    }
    boolean npcFiringAfterApproach(Npc npc,Tile player,int steps)
    {
        Tile at=new Tile(npc.x,npc.y);
        for(int i=0;i<Math.min(4,steps);i++)
        {
            Tile next=npcApproach(npc,player,at.x,at.y);
            if(next.equals(at))return false;
            at=next;
            if(npcFiringAt(npc,player,at))return true;
        }
        return false;
    }

    private boolean npcFiringAt(Npc npc,Tile player,Tile next)
    {
        int x=Math.max(next.x,Math.min(next.x+npc.size-1,player.x));
        int y=Math.max(next.y,Math.min(next.y+npc.size-1,player.y));
        // Colossus attack range is 15 tiles (1.0.64: a step that only brings sight but not range is no throw).
        return (x!=player.x||y!=player.y)&&Math.max(Math.abs(x-player.x),Math.abs(y-player.y))<=15&&ray(player.x,player.y,x,y);
    }

    /** Bounded melee reach keeps the whole actor footprint outside walls and other NPCs. */
    boolean npcMeleeAfterApproach(Npc npc,Tile player,int steps)
    {
        Tile at=new Tile(npc.x,npc.y);
        int limit=Math.max(0,Math.min(4,steps));
        for(int i=0;i<=limit;i++)
        {
            if(npcMeleeAt(npc,player,at))return true;
            if(i==limit)return false;
            Tile next=npcApproach(npc,player,at.x,at.y);
            if(next.equals(at))return false;
            at=next;
        }
        return false;
    }

    /**
     * The pillar trap: stepping straight toward {@code player}, the NPC stalls before it can melee. Recorded Jaguars
     * and Minotaurs spend ~75% of their player-targeting frames parked like this (e.g. at the pillar's north side
     * while the player stands two tiles south-west of it).
     */
    boolean npcTrapped(Npc npc,Tile player)
    {
        return npcTrapped(npc,player,true);
    }

    /**
     * {@link #npcTrapped} by walls alone. Another enemy in the way is no trap for a claw: it moves on, or is walked
     * through (local 2026-11-04 09:07 wave 1 t102-104: the Jaguar stepped onto the Shaman's tile and clawed 50).
     */
    boolean npcWallTrapped(Npc npc,Tile player)
    {
        return npcTrapped(npc,player,false);
    }

    private boolean npcTrapped(Npc npc,Tile player,boolean npcsBlock)
    {
        if(!valid)return false;
        Tile at=new Tile(npc.x,npc.y);
        for(int i=0;i<16;i++)
        {
            if(npcMeleeAt(npc,player,at))return false;
            Tile next=npcApproach(npc,player,at.x,at.y,npcsBlock);
            if(next.equals(at))return true;
            at=next;
        }
        return false;
    }

    private boolean npcMeleeAt(Npc npc,Tile player,Tile at)
    {
        if(player.x<at.x-1||player.x>at.x+npc.size||player.y<at.y-1||player.y>at.y+npc.size)return false;
        if(player.x>=at.x&&player.x<at.x+npc.size&&player.y>=at.y&&player.y<at.y+npc.size)return true;
        for(int x=at.x;x<at.x+npc.size;x++)for(int y=at.y;y<at.y+npc.size;y++)
            if(Math.abs(x-player.x)+Math.abs(y-player.y)==1&&cardinal(x,y,player.x,player.y))return true;
        return false;
    }

    /** Other enemies' footprints block a step (1.0.45 modes, restored 1.0.63). */
    private static final int NPCS_BLOCK=0;
    /** Walls only: another enemy in the way moves on or is walked through. */
    private static final int WALLS_ONLY=1;
    /** Enemies block, except one playing its death animation. */
    private static final int DYING_OPEN=2;
    private boolean dyingOpens;
    /**
     * A melee actor diagonal to {@code player} reaches claw range with its x step only: diagonal to a still player it
     * stepped sideways when that was free in 84 of 85 frames and stayed when it was blocked in 1,615 of 1,619, even
     * with the y step free (server + local recordings, 1.0.20's analysis).
     */
    private boolean diagonalStep(Npc npc,Tile player,int mode)
    {
        if(!valid||!npc.meleeOnly()||npc.distance(player.x,player.y)!=1||melee(npc,player))return false;
        int dx=player.x<npc.x?-1:player.x>=npc.x+npc.size?1:0;
        int dy=player.y<npc.y?-1:player.y>=npc.y+npc.size?1:0;
        if(dx==0||dy==0)return false;
        dyingOpens=mode==DYING_OPEN;
        try{return npcCanTravel(npc,npc.x,npc.y,dx,0,mode!=WALLS_ONLY);}finally{dyingOpens=false;}
    }
    /** Diagonal to {@code player} and only walls could stop its x step (1.0.36's walking rule). */
    boolean diagonalStepOpenByWalls(Npc npc,Tile player)
    {
        return diagonalStep(npc,player,WALLS_ONLY);
    }
    /**
     * Diagonal to {@code player}, its x step held only by an enemy that is dying: the tile opens within about three
     * ticks and the claw comes the tick it does (1.0.44 wave 2, 2026-11-04 22:14, t234-237: a Jaguar at (1816,3116)
     * waited on a dying Shaman at (1815,3116), stepped in and clawed 18+10 with no protection up).
     */
    boolean diagonalStepHeldByDying(Npc npc,Tile player)
    {
        return diagonalStep(npc,player,DYING_OPEN)&&!diagonalStep(npc,player,NPCS_BLOCK);
    }
    /**
     * 1.0.97 (ColosseumMarch): while set, the other enemies stand where this map puts them instead of where the snapshot
     * has them. Null everywhere outside {@link #marchStep}, so every other caller reads the snapshot as before.
     */
    private java.util.Map<Integer,Tile> marching;
    static final int MARCH_RULE=Integer.getInteger("colo.marchRule",1);
    static final boolean MARCH_NEAR=!"false".equals(System.getProperty("colo.marchNear")),MARCH_DIAGONAL=!"false".equals(System.getProperty("colo.marchDiagonal")),
        MARCH_CLAW=!"false".equals(System.getProperty("colo.marchClaw")),MARCH_DIAGONAL_CLAW="true".equals(System.getProperty("colo.marchDiagonalClaw"));
    /**
     * One native step of {@code npc} from {@code at} toward {@code player}, the other enemies standing on {@code others}
     * (index to south-west tile). A shooter stops once it is within {@code range} with a firing ray; a melee actor at
     * distance one. Same order as {@link #npcApproach}: diagonal, then x, then y.
     */
    Tile marchStep(Npc npc,Tile at,Tile player,java.util.Map<Integer,Tile> others,int range)
    {
        if(!valid)return at;
        int x=at.x,y=at.y;
        int distance=Math.max(0,Math.max(Math.max(x-player.x,player.x-x-npc.size+1),Math.max(y-player.y,player.y-y-npc.size+1)));
        if(npc.meleeOnly())
        {
            if(npcMeleeAt(npc,player,at))return at;
            // Diagonal to the player a claw takes its x step when that is free, else waits (84 of 85 recorded frames).
            if(distance<=1)
            {
                if(!MARCH_CLAW)return at;
                int sx=player.x<x?-1:player.x>=x+npc.size?1:0;
                marching=others;
                try{return sx!=0&&npcCanTravel(npc,x,y,sx,0,true)?new Tile(x+sx,y):at;}finally{marching=null;}
            }
        }
        // Beside the player (diagonals included) no shooter steps, sight or not (MARCH_NEAR).
        else if(distance<=(MARCH_NEAR?1:0)||distance<=range&&npcFiringAt(npc,player,at))return at;
        int dx,dy;
        if(MARCH_RULE==0){dx=player.x<x?-1:player.x>=x+npc.size?1:0;dy=player.y<y?-1:player.y>=y+npc.size?1:0;}
        else{dx=Integer.signum(player.x-x);dy=Integer.signum(player.y-y);}
        if(dx==0&&dy==0)return at;
        marching=others;
        try
        {
            // A shooter's diagonal step also needs both of its straight steps clear of other monsters (MARCH_DIAGONAL).
            if(dx!=0&&dy!=0&&npcCanTravel(npc,x,y,dx,dy,true)
                &&(!MARCH_DIAGONAL||npc.meleeOnly()&&!MARCH_DIAGONAL_CLAW||marchClear(npc,x+dx,y)&&marchClear(npc,x,y+dy)))return new Tile(x+dx,y+dy);
            if(dx!=0&&npcCanTravel(npc,x,y,dx,0,true))return new Tile(x+dx,y);
            if(dy!=0&&npcCanTravel(npc,x,y,0,dy,true))return new Tile(x,y+dy);
            return at;
        }
        finally{marching=null;}
    }
    private boolean marchClear(Npc npc,int x,int y)
    {
        for(int tx=x;tx<x+npc.size;tx++)for(int ty=y;ty<y+npc.size;ty++)
            for(Npc other:snapshot.npcs)
            {
                Tile t=other.index==npc.index?null:marching.get(other.index);
                if(t!=null&&tx>=t.x&&tx<t.x+other.size&&ty>=t.y&&ty<t.y+other.size)return false;
            }
        return true;
    }
    /** {@code npc} standing on {@code at} is within {@code range} of {@code player} with a firing ray. */
    boolean firingFrom(Npc npc,Tile at,Tile player,int range)
    {
        int x=Math.max(at.x,Math.min(at.x+npc.size-1,player.x)),y=Math.max(at.y,Math.min(at.y+npc.size-1,player.y));
        return Math.max(Math.abs(x-player.x),Math.abs(y-player.y))<=range&&npcFiringAt(npc,player,at);
    }
    /** {@code npc} standing on {@code at} can claw {@code player}. */
    boolean meleeFrom(Npc npc,Tile at,Tile player){return valid&&npcMeleeAt(npc,player,at);}
    private boolean npcTileAvailable(Npc npc,int x,int y,boolean npcsBlock)
    {
        if(!open(x,y))return false;
        if(!npcsBlock)return true;
        if(marching!=null)
        {
            for(Npc other:snapshot.npcs)
            {
                Tile t=other.index==npc.index?null:marching.get(other.index);
                if(t!=null&&x>=t.x&&x<t.x+other.size&&y>=t.y&&y<t.y+other.size)return false;
            }
            return true;
        }
        for(Npc other:snapshot.npcs)
            if(other.index!=npc.index&&other.enemy()&&other.role!=ColosseumSnapshot.Role.FLARE&&other.distance(x,y)==0
                &&!(dyingOpens&&other.dying()))return false;
        return true;
    }

    /** RuneLite WorldArea footprint edge/wall checks with current combat-NPC occupancy. */
    private boolean npcCanTravel(Npc npc,int baseX,int baseY,int dx,int dy,boolean npcsBlock)
    {
        int sx=baseX+dx,sy=baseY+dy,ex=sx+npc.size-1,ey=sy+npc.size-1;
        int cx=dx>0?ex:sx,cy=dy>0?ey:sy;
        int xf=BLOCKED,yf=BLOCKED,xyf=BLOCKED,xsouth=BLOCKED,xnorth=BLOCKED,ywest=BLOCKED,yeast=BLOCKED;
        if(dx<0){xf|=8;xsouth|=32|16;xnorth|=2|4;}
        if(dx>0){xf|=128;xsouth|=32|64;xnorth|=2|1;}
        if(dy<0){yf|=2;ywest|=128|1;yeast|=8|4;}
        if(dy>0){yf|=32;ywest|=128|64;yeast|=8|16;}
        if(dx<0&&dy<0)xyf|=4;
        if(dx<0&&dy>0)xyf|=16;
        if(dx>0&&dy<0)xyf|=1;
        if(dx>0&&dy>0)xyf|=64;
        if(dx!=0)
        {
            for(int y=sy;y<=ey;y++)if((flag(cx,y)&xf)!=0||!npcTileAvailable(npc,cx,y,npcsBlock))return false;
            for(int y=sy+1;y<=ey;y++)if((flag(cx,y)&xsouth)!=0)return false;
            for(int y=ey-1;y>=sy;y--)if((flag(cx,y)&xnorth)!=0)return false;
        }
        if(dy!=0)
        {
            for(int x=sx;x<=ex;x++)if((flag(x,cy)&yf)!=0||!npcTileAvailable(npc,x,cy,npcsBlock))return false;
            for(int x=sx+1;x<=ex;x++)if((flag(x,cy)&ywest)!=0)return false;
            for(int x=ex-1;x>=sx;x--)if((flag(x,cy)&yeast)!=0)return false;
        }
        if(dx!=0&&dy!=0)
        {
            if((flag(cx,cy)&xyf)!=0||!npcTileAvailable(npc,cx,cy,npcsBlock))return false;
            if(npc.size==1&&((flag(cx,cy-dy)&xf)!=0||(flag(cx-dx,cy)&yf)!=0))return false;
        }
        return true;
    }

    private static boolean facing(Npc npc, Tile p, int x, int y)
    {
        return p.x < npc.x && x == npc.x || p.x >= npc.x + npc.size && x == npc.x + npc.size - 1
            || p.y < npc.y && y == npc.y || p.y >= npc.y + npc.size && y == npc.y + npc.size - 1;
    }

    boolean melee(Npc npc, Tile tile)
    {
        return melee(npc, npc.x, npc.y, tile);
    }

    /** Melee reach after the actor's next native approach step toward {@code tile}. */
    boolean meleeAfterApproach(Npc npc, Tile tile)
    {
        Tile next = npcApproach(npc, tile);
        return melee(npc, next.x, next.y, tile);
    }

    private boolean melee(Npc npc, int nx, int ny, Tile tile)
    {
        int distance = Math.max(0, Math.max(Math.max(nx - tile.x, tile.x - nx - npc.size + 1), Math.max(ny - tile.y, tile.y - ny - npc.size + 1)));
        if (distance == 0) return true;
        if (distance > 1) return false;
        for (int x = nx; x < nx + npc.size; x++)
            for (int y = ny; y < ny + npc.size; y++)
                if (Math.abs(x - tile.x) + Math.abs(y - tile.y) == 1 && cardinal(x, y, tile.x, tile.y)) return true;
        return false;
    }

    Paths paths(Tile start, int maxDistance) { return new Paths(start, maxDistance); }

    /**
     * The observed player routes take excess cardinal steps before their diagonal tail.
     * NPC footprints can be crossed in transit (including when Sol walks over the player).
     * Callers still validate destination occupancy and hazards separately.
     */
    Paths movementPaths(Tile start, int maxDistance) { return new Paths(start, maxDistance, false); }

    final class Paths
    {
        private final int[] previous = new int[flags.length], distances = new int[flags.length];
        final List<Tile> reachable = new ArrayList<>();
        final Tile start;
        Paths(Tile start, int maxDistance)
        {
            this(start, maxDistance, true);
        }
        Paths(Tile start, int maxDistance, boolean npcBlocks)
        {
            this.start = start;
            Arrays.fill(previous, -1); Arrays.fill(distances, -1);
            if (!open(start.x, start.y)) return;
            int[] queue = new int[flags.length]; int head = 0, tail = 0, origin = index(start.x, start.y);
            int[][] directions = npcBlocks
                ? new int[][] {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}}
                : new int[][] {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};
            previous[origin] = origin; distances[origin] = 0; queue[tail++] = origin;
            while (head < tail)
            {
                int at = queue[head++]; Tile from = tile(at); reachable.add(from);
                if (distances[at] >= maxDistance) continue;
                for (int[] direction : directions)
                {
                    Tile to = new Tile(from.x + direction[0], from.y + direction[1]);
                    if (!inside(to.x, to.y) || previous[index(to.x, to.y)] >= 0 || !step(from, to, npcBlocks)) continue;
                    int next = index(to.x, to.y); previous[next] = at; distances[next] = distances[at] + 1;
                    queue[tail++] = next;
                }
            }
        }
        int distance(Tile target) { return inside(target.x, target.y) ? distances[index(target.x, target.y)] : -1; }
        /** A player step on this map: walls block it, NPC footprints do not. */
        boolean playerStep(Tile from, Tile to) { return ColosseumCollision.this.playerStep(from, to); }
        List<Tile> to(Tile target)
        {
            if (distance(target) < 0) return Collections.emptyList();
            List<Tile> result = new ArrayList<>();
            int at = index(target.x, target.y);
            for (int steps = 0; steps < previous.length; steps++)
            {
                result.add(tile(at));
                if (previous[at] == at) { Collections.reverse(result); return result; }
                at = previous[at];
                if (at < 0) return Collections.emptyList();
            }
            return Collections.emptyList();
        }
    }

    static final class Tile
    {
        final int x, y;
        Tile(int x, int y) { this.x = x; this.y = y; }
        int distance(Tile other) { return Math.max(Math.abs(x - other.x), Math.abs(y - other.y)); }
        @Override public boolean equals(Object obj) { return obj instanceof Tile && x == ((Tile) obj).x && y == ((Tile) obj).y; }
        @Override public int hashCode() { return 31 * x + y; }
        @Override public String toString() { return x + "," + y; }
    }
}
