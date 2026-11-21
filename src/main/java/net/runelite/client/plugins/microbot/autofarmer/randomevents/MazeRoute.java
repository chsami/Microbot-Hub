package net.runelite.client.plugins.microbot.autofarmer.randomevents;

import java.util.*;
import net.runelite.api.CollisionDataFlag;

/** Cardinal scene pathfinder. Door edges are explicit; walls are never ignored globally. */
final class MazeRoute {
    static final int[] DX={-1,0,1,0}, DY={0,1,0,-1};
    static final int[] OUT={128,2,8,32}, IN={8,32,128,2};
    static final class Edge {
        final int ax,ay,bx,by,door;
        Edge(int ax,int ay,int bx,int by,int door){this.ax=ax;this.ay=ay;this.bx=bx;this.by=by;this.door=door;}
        boolean joins(int x,int y,int nx,int ny){return ax==x && ay==y && bx==nx && by==ny || bx==x && by==y && ax==nx && ay==ny;}
    }
    static boolean canStep(int[][] flags,int x,int y,int nx,int ny){
        if(x<0 || nx<0 || y<0 || ny<0 || x>=flags.length || nx>=flags.length || y>=flags[x].length || ny>=flags[nx].length)return false;
        if((flags[nx][ny]&CollisionDataFlag.BLOCK_MOVEMENT_FULL)!=0)return false;
        for(int d=0;d<4;d++)if(nx==x+DX[d] && ny==y+DY[d])return (flags[x][y]&OUT[d])==0 && (flags[nx][ny]&IN[d])==0;
        return false;
    }
    static List<int[]> path(int[][] flags,int sx,int sy,int gx,int gy,List<Edge> doors){
        if(flags==null || sx<0 || sy<0 || sx>=flags.length || sy>=flags[sx].length)return List.of();
        int[][][] previous=new int[flags.length][][];
        for(int x=0;x<flags.length;x++){previous[x]=new int[flags[x].length][];}
        ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{sx,sy});previous[sx][sy]=new int[]{sx,sy};int[] goal=null;
        while(!queue.isEmpty()){
            int[] p=queue.remove();int x=p[0],y=p[1];
            if(Math.abs(x-gx)+Math.abs(y-gy)<=1){goal=p;break;}
            for(int d=0;d<4;d++){
                int nx=x+DX[d],ny=y+DY[d];if(nx<0 || ny<0 || nx>=flags.length || ny>=flags[nx].length || previous[nx][ny]!=null)continue;
                boolean walk=canStep(flags,x,y,nx,ny);
                if(!walk){boolean door=false;for(Edge e:doors)if(e.joins(x,y,nx,ny)){door=true;break;}if(!door)continue;}
                previous[nx][ny]=p;queue.add(new int[]{nx,ny});
            }
        }
        if(goal==null)return List.of();List<int[]> result=new ArrayList<>();int[] p=goal;
        while(p[0]!=sx || p[1]!=sy){result.add(p);p=previous[p[0]][p[1]];}Collections.reverse(result);return result;
    }
}
