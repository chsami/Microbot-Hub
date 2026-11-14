package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Read-only interoperability with Chart Plotter's documented collision formats.
 * Imported cells are planning data only: live() is always false. No network I/O,
 * writes, executable plugin loading, or account information are involved.
 * Format attribution: Dazuzi/Chart Plotter, BSD-2-Clause; THIRD_PARTY_NOTICES.md.
 */
public final class SeaCollisionSeed implements SeaGrid {
    private static final int MAX_CHUNKS=200_000;
    private final Map<Long,long[]> chunks;
    public final String source;
    private SeaCollisionSeed(Map<Long,long[]> chunks,String source){this.chunks=Collections.unmodifiableMap(chunks);this.source=source;}
    public static SeaCollisionSeed empty(){return new SeaCollisionSeed(Collections.emptyMap(),"none");}
    public static SeaCollisionSeed bundled() {
        try { return binary(new ByteArrayInputStream(SeaRecordedData.compressed()), "User-recorded chart: 195180 open / 20436 blocked tiles"); }
        catch (IOException ex) { throw new IllegalStateException("Bundled chart failed integrity checks", ex); }
    }
    public static SeaGrid fallback(SeaGrid primary, SeaGrid secondary) {
        return new SeaGrid() {
            public byte cell(int x,int y) {byte c=primary.cell(x,y);return c==UNKNOWN?secondary.cell(x,y):c;}
            public boolean live(int x,int y) {return false;}
        };
    }
    public int chunkCount(){return chunks.size();}
    @Override public boolean live(int x,int y){return false;}
    @Override public byte cell(int x,int y){
        if(x<0||y<0||x>16383||y>16383)return BLOCKED;
        long[] c=chunks.get(SeaPoint.key(x>>3,y>>3));if(c==null)return UNKNOWN;long b=1L<<((x&7)+((y&7)<<3));
        return (c[0]&b)==0?UNKNOWN:(c[1]&b)==0?OPEN:BLOCKED;
    }
    public static SeaCollisionSeed read(Path file)throws IOException{
        long size=Files.size(file);if(size<1||size>24_000_000)throw new IOException("Seed file size rejected");
        return file.getFileName().toString().endsWith(".bin")?binary(file):text(file);
    }
    private static void put(Map<Long,long[]> map,int x,int y,long known,long blocked)throws IOException{
        if(x<0||y<0||x>2047||y>2047)throw new IOException("Seed coordinate out of world range");
        if(map.size()>=MAX_CHUNKS||map.putIfAbsent(SeaPoint.key(x,y),new long[]{known,blocked&known})!=null)
            throw new IOException("Duplicate/excess seed chunk");
    }
    private static SeaCollisionSeed text(Path file)throws IOException{
        Map<Long,long[]> map=new HashMap<>();String version=null;int lines=0;
        try(BufferedReader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String s;while((s=in.readLine())!=null){
                if(++lines>MAX_CHUNKS+1000||s.length()>256)throw new IOException("Seed text limit exceeded");
                s=s.trim();if(s.isEmpty())continue;String[] p=s.split("\\s+");
                if(version==null){if(p.length!=2||!p[0].equals("data"))throw new IOException("Missing seed version");version=p[1];continue;}
                if(p.length!=3&&p.length!=4)throw new IOException("Invalid seed row");
                try{int x=Integer.parseInt(p[0]),y=Integer.parseInt(p[1]);
                    long known=p.length==3?-1L:Long.parseUnsignedLong(p[2],16);
                    long blocked=Long.parseUnsignedLong(p[p.length-1],16);put(map,x,y,known,blocked);
                }catch(NumberFormatException ex){throw new IOException("Invalid seed number",ex);}
            }
        }
        if(version==null||map.isEmpty())throw new IOException("Empty collision seed");
        return new SeaCollisionSeed(map,"Chart Plotter text "+version);
    }
    private static SeaCollisionSeed binary(Path file)throws IOException{
        return binary(Files.newInputStream(file), "Chart Plotter local binary v1");
    }
    private static SeaCollisionSeed binary(InputStream stream,String source)throws IOException {
        Map<Long,long[]> map=new HashMap<>();
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(new BufferedInputStream(stream)))){
            if(in.readUnsignedByte()!=1)throw new IOException("Unknown Chart Plotter binary version");
            int n=in.readInt();if(n<0||n>MAX_CHUNKS)throw new IOException("Invalid seed chunk count");
            for(int i=0;i<n;i++)put(map,in.readUnsignedShort(),in.readUnsignedShort(),in.readLong(),in.readLong());
            if(in.read()!=-1)throw new IOException("Trailing seed data");
        }
        return new SeaCollisionSeed(map,source);
    }
}
