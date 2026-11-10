package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.Arrays;

/**
 * Coarse, ADVISORY geography from the user's alpha map. It supplies a whole-voyage
 * direction around continents, not permission to enter a tile. Unknown pixels
 * remain unknown. Live navigation always goes through Hull on a live SeaGrid.
 */
public final class SeaReferenceMap {
    public final int west,south,cellSize,width,height;
    private final byte[] cells;
    private final byte[] clearance;
    public SeaReferenceMap() {this(SeaReferenceData.WEST,SeaReferenceData.SOUTH,SeaReferenceData.CELL,
            SeaReferenceData.WIDTH,SeaReferenceData.HEIGHT,SeaReferenceData.decode());}
    public SeaReferenceMap(int west,int south,int cellSize,int width,int height,byte[] cells) {
        if(cellSize<1||width<1||height<1||(long)width*height>2_000_000||cells.length!=width*height)
            throw new IllegalArgumentException("Invalid reference raster");
        this.west=west;this.south=south;this.cellSize=cellSize;this.width=width;this.height=height;
        this.cells=cells.clone();for(byte b:this.cells)if(b<0||b>2)throw new IllegalArgumentException("Invalid reference cell");
        clearance=distances();
    }
    public boolean contains(SeaPoint p) {return index(p.x,p.y)>=0;}
    public int index(int x,int y) {
        int cx=Math.floorDiv(x-west,cellSize),cy=Math.floorDiv(y-south,cellSize);
        return cx<0||cy<0||cx>=width||cy>=height?-1:cy*width+cx;
    }
    public SeaPoint point(int index) {
        return new SeaPoint(west+index%width*cellSize+cellSize/2,south+index/width*cellSize+cellSize/2);
    }
    public byte cell(int x,int y) {int i=index(x,y);return i<0?SeaGrid.UNKNOWN:cells[i];}
    byte cellAt(int i) {return cells[i];}
    int clearanceAt(int i) {return clearance[i]&255;}
    private byte[] distances() {
        byte[] d=new byte[cells.length];Arrays.fill(d,(byte)31);int[] q=new int[cells.length];int head=0,tail=0;
        for(int i=0;i<cells.length;i++)if(cells[i]!=SeaGrid.OPEN){d[i]=0;q[tail++]=i;}
        while(head<tail){int i=q[head++],x=i%width,y=i/width,v=(d[i]&255)+1;if(v>=31)continue;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=width||yy>=height)continue;int n=yy*width+xx;
                if((d[n]&255)>v){d[n]=(byte)v;q[tail++]=n;}
            }
        }return d;
    }
}
