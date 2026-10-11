/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
/*
 * Directional line-of-sight traversal adapted from RuneLite WorldArea.
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** RuneLite collision flags copied into an immutable scene grid. */
public final class CollisionGrid {
    public static final int NORTH=2,EAST=8,SOUTH=32,WEST=128;
    public static final int OBJECT=256,FLOOR_DECORATION=0x40000,FLOOR=0x200000,UNLOADED=0x1000000;
    public static final int FULL=OBJECT|FLOOR_DECORATION|FLOOR|UNLOADED;
    public static final int PROJECTILE_OBJECT=0x20000;
    private final int[][] flags;
    public final int width,height;
    public CollisionGrid(int[][] f) {
        if(f==null||f.length==0||f[0].length==0)throw new IllegalArgumentException("Empty collision grid");
        width=f.length;height=f[0].length;flags=new int[width][height];
        for(int x=0;x<width;x++) {
            if(f[x].length!=height)throw new IllegalArgumentException("Ragged collision grid");
            flags[x]=f[x].clone();
        }
    }
    public boolean inside(Tile t){return t!=null&&t.x()>=0&&t.y()>=0&&t.x()<width&&t.y()<height;}
    public int flag(Tile t){return inside(t)?flags[t.x()][t.y()]:-1;}
    public boolean open(Tile t){return inside(t)&&(flag(t)&FULL)==0;}
    private boolean edge(Tile a,Tile b,boolean projectile) {
        if(!inside(a)||!inside(b))return false;
        int dx=b.x()-a.x(),dy=b.y()-a.y();
        if(Math.abs(dx)+Math.abs(dy)!=1)return false;
        int outgoing=dx==1?EAST:dx==-1?WEST:dy==1?NORTH:SOUTH;
        int incoming=dx==1?WEST:dx==-1?EAST:dy==1?SOUTH:NORTH;
        int shift=projectile?9:0;
        int block=projectile?PROJECTILE_OBJECT|UNLOADED:FULL;
        return (flag(b)&block)==0&&(flag(a)&(outgoing<<shift))==0&&(flag(b)&(incoming<<shift))==0;
    }
    public boolean step(Tile a,Tile b) {
        if(a.equals(b))return open(a);
        int dx=b.x()-a.x(),dy=b.y()-a.y();
        if(Math.abs(dx)>1||Math.abs(dy)>1||!open(b))return false;
        if(dx==0||dy==0)return edge(a,b,false);
        Tile h=a.add(dx,0),v=a.add(0,dy);
        // Both corner edges must be clear; never permit diagonal clipping.
        int out=dx<0?(dy>0?1:64):(dy>0?4:16);
        int in=dx<0?(dy>0?16:4):(dy>0?64:1);
        return (flag(a)&out)==0&&(flag(b)&in)==0&&edge(a,h,false)&&edge(a,v,false)
                &&edge(h,b,false)&&edge(v,b,false);
    }
    public boolean areaStep(Mob mob,Tile to,List<Mob> others,Tile player) {
        int dx=to.x()-mob.tile().x(),dy=to.y()-mob.tile().y();
        if(Math.abs(dx)>1||Math.abs(dy)>1)return false;
        for(int x=0;x<mob.size();x++)for(int y=0;y<mob.size();y++) {
            Tile a=mob.tile().add(x,y),b=to.add(x,y);
            if(!step(a,b)||b.equals(player))return false;
            for(Mob other:others)if(other.index()!=mob.index()&&other.occupies(b)&&!other.occupies(a))return false;
        }
        return true;
    }
    /** Fixed-point directional LOS traversal, adapted from RuneLite WorldArea (BSD-2-Clause).
     * Do not use symmetric supercover LOS: it can incorrectly call an exposed tile safe.
     */
    public boolean sight(Tile from,Tile to) {
        if(!inside(from)||!inside(to))return false;
        if(from.equals(to))return true;
        int dx=to.x()-from.x(),dy=to.y()-from.y();
        int ax=Math.abs(dx),ay=Math.abs(dy);
        int xf=PROJECTILE_OBJECT|UNLOADED|((dx<0?EAST:WEST)<<9);
        int yf=PROJECTILE_OBJECT|UNLOADED|((dy<0?NORTH:SOUTH)<<9);
        if(ax>ay) {
            int x=from.x(),yb=(from.y()<<16)+0x8000-(dy<0?1:0);
            int slope=(dy<<16)/ax,dir=dx<0?-1:1;
            while(x!=to.x()) {
                x+=dir;int y=yb>>>16;
                if((flag(new Tile(x,y))&xf)!=0)return false;
                yb+=slope;int ny=yb>>>16;
                if(ny!=y&&(flag(new Tile(x,ny))&yf)!=0)return false;
            }
        } else {
            int y=from.y(),xb=(from.x()<<16)+0x8000-(dx<0?1:0);
            int slope=(dx<<16)/ay,dir=dy<0?-1:1;
            while(y!=to.y()) {
                y+=dir;int x=xb>>>16;
                if((flag(new Tile(x,y))&yf)!=0)return false;
                xb+=slope;int nx=xb>>>16;
                if(nx!=x&&(flag(new Tile(nx,y))&xf)!=0)return false;
            }
        }
        return true;
    }
    /** Player-to-NPC LOS is directional and must not reuse NPC-to-player LOS. */
    public boolean playerSight(Tile p,Mob m) {
        for(int x=0;x<m.size();x++)for(int y=0;y<m.size();y++)
            if((x==0||y==0||x==m.size()-1||y==m.size()-1)&&sight(p,m.tile().add(x,y)))return true;
        return false;
    }
    public boolean sight(Mob m,Tile p) {
        // All perimeter tiles are considered, not just the SW origin of a large NPC.
        for(int x=0;x<m.size();x++)for(int y=0;y<m.size();y++)
            if((x==0||y==0||x==m.size()-1||y==m.size()-1)&&sight(m.tile().add(x,y),p))return true;
        return false;
    }
    public boolean melee(Mob m,Tile p) {
        if(m.occupies(p))return true;
        if(m.distance(p)!=1)return false;
        for(int x=0;x<m.size();x++)for(int y=0;y<m.size();y++) {
            Tile t=m.tile().add(x,y);
            if(Math.abs(t.x()-p.x())+Math.abs(t.y()-p.y())==1&&edge(t,p,false))return true;
        }
        return false;
    }
    /** One BFS per snapshot, reused for all candidate destinations. */
    public final class PathTree {
        private final Tile start;
        private final int[][] previous;
        private PathTree(Tile start,int[][] previous){this.start=start;this.previous=previous;}
        public List<Tile> to(Tile target) {
            if(!inside(target)||previous[target.x()][target.y()]<0)return List.of();
            ArrayList<Tile> out=new ArrayList<>();Tile at=target;
            for(int guard=0;guard<=width*height;guard++) {
                out.add(at);if(at.equals(start)){Collections.reverse(out);return out;}
                int code=previous[at.x()][at.y()];if(code<0)return List.of();
                at=new Tile(code/height,code%height);
            }
            return List.of();
        }
    }
    public PathTree pathsFrom(Tile start,List<Mob> blocked) {
        return pathsFrom(start,blocked,tile->true);
    }
    /** Additional tactical exclusion (Jad buffer), without changing terrain collision semantics. */
    public PathTree pathsFrom(Tile start,List<Mob> blocked,java.util.function.Predicate<Tile> allowed) {
        int[][] previous=new int[width][height];for(int[] row:previous)Arrays.fill(row,-1);
        PathTree tree=new PathTree(start,previous);if(!open(start))return tree;
        boolean[][] occupied=new boolean[width][height];
        for(Mob mob:blocked)for(int x=0;x<mob.size();x++)for(int y=0;y<mob.size();y++) {
            Tile t=mob.tile().add(x,y);if(inside(t))occupied[t.x()][t.y()]=true;
        }
        // Primitive queue avoids repeated BFS and object allocation for every candidate.
        int[] queue=new int[width*height];int head=0,tail=0;
        int origin=start.x()*height+start.y();queue[tail++]=origin;previous[start.x()][start.y()]=origin;
        while(head<tail) {
            int code=queue[head++];Tile a=new Tile(code/height,code%height);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
                if(dx==0&&dy==0)continue;Tile b=a.add(dx,dy);
                if(!inside(b)||previous[b.x()][b.y()]>=0||occupied[b.x()][b.y()]||!allowed.test(b)||!step(a,b))continue;
                if(dx!=0&&dy!=0&&(occupied[a.x()+dx][a.y()]||occupied[a.x()][a.y()+dy]))continue;
                previous[b.x()][b.y()]=code;queue[tail++]=b.x()*height+b.y();
            }
        }
        return tree;
    }
    public List<Tile> path(Tile start,Tile target,List<Mob> blocked) {
        return pathsFrom(start,blocked).to(target);
    }
    public List<Tile> corners() {
        ArrayList<Tile> out=new ArrayList<>();
        for(int x=1;x<width-1;x++)for(int y=1;y<height-1;y++) {
            Tile t=new Tile(x,y);if(!open(t))continue;
            boolean n=!open(t.add(0,1)),s=!open(t.add(0,-1)),e=!open(t.add(1,0)),w=!open(t.add(-1,0));
            boolean diag=!open(t.add(1,1))||!open(t.add(1,-1))||!open(t.add(-1,1))||!open(t.add(-1,-1));
            if((n||s)&&(e||w)||(!n&&!s&&!e&&!w&&diag))out.add(t);
        }
        return out;
    }
}
