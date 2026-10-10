/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Human-readable events plus machine-replayable scene-relative decision frames. */
class FcTelemetry implements AutoCloseable {
    protected BufferedWriter writer;protected Path directory;
    protected final Set<String> maps=new HashSet<>();
    void open(boolean enabled) {
        close();maps.clear();directory=null;
        if(!enabled)return;
        try {
            String stamp=DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").format(LocalDateTime.now());
            directory=Paths.get(System.getProperty("user.home"),".runelite","dro-firecape",stamp);
            Files.createDirectories(directory);writer=Files.newBufferedWriter(directory.resolve("events.jsonl"));
            event("session","controller "+DroFirecapePlugin.version+"; frame schema 2; local movement acknowledgements; live validation pending");
        }catch(IOException e){Microbot.log("[Dro Firecape] Trace unavailable: "+e);}
    }
    synchronized void event(String type,String detail) {
        write("{\"time\":\""+Instant.now()+"\",\"type\":\""+escape(type)+"\",\"detail\":\""+escape(detail)+"\"}");
    }
    protected void write(String line) {
        if(writer==null)return;
        try{writer.write(line);writer.newLine();writer.flush();}
        catch(IOException e){Microbot.log("[Dro Firecape] Trace write failed: "+e);close();}
    }
    synchronized void decision(FcFrame f,int wave,Plan plan) {
        if(writer==null)return;
        event("decision","tick="+f.tick+" wave="+wave+" player="+f.model.player()+" template="+f.template
            +" energy="+f.rawEnergy+" overhead="+f.overhead+" plan="+plan+" mobs="+f.model.mobs());
        String mapName="collision-"+f.world+"-"+f.baseX+"-"+f.baseY+"-"+f.plane+".csv";
        if(directory!=null&&f.cave&&!maps.contains(mapName)) {
            try(BufferedWriter map=Files.newBufferedWriter(directory.resolve(mapName))) {
                map.write("sceneX,sceneY,flags\n");
                for(int x=0;x<f.model.grid().width;x++)for(int y=0;y<f.model.grid().height;y++)
                    map.write(x+","+y+","+f.model.grid().flag(new Tile(x,y))+"\n");
                maps.add(mapName);
            }catch(IOException e){event("map-error",e.toString());}
        }
        StringBuilder b=new StringBuilder(768);
        b.append("{\"type\":\"frame\",\"schema\":2,\"tick\":").append(f.tick)
            .append(",\"wave\":").append(wave).append(",\"base\":[").append(f.baseX).append(',').append(f.baseY).append(',').append(f.plane)
            .append("],\"map\":\"").append(mapName).append("\",\"player\":").append(tile(f.model.player()))
            .append(",\"hp\":").append(f.hp).append(",\"prayer\":").append(f.prayer)
            .append(",\"energyRaw\":").append(f.rawEnergy).append(",\"running\":").append(f.running)
            .append(",\"range\":").append(f.model.weaponRange()).append(",\"interaction\":").append(f.interactingIndex)
            .append(",\"capturedAgeMs\":").append(System.currentTimeMillis()-f.capturedAt)
            .append(",\"mobs\":[");
        boolean comma=false;
        for(Mob m:f.model.mobs()) {
            if(comma)b.append(',');comma=true;
            b.append("{\"index\":").append(m.index()).append(",\"kind\":\"").append(m.kind())
                .append("\",\"tile\":").append(tile(m.tile())).append(",\"size\":").append(m.size())
                .append(",\"healthRatio\":").append(m.healthRatio()).append(",\"healthScale\":").append(m.healthScale())
                .append(",\"attackTick\":").append(m.lastAttackTick()).append(",\"style\":\"").append(m.lastStyle())
                .append("\",\"attackingPlayer\":").append(m.attackingPlayer()).append('}');
        }
        b.append("],\"plan\":");
        if(plan==null)b.append("null");
        else b.append("{\"destination\":").append(tile(plan.destination())).append(",\"step\":").append(tile(plan.nextStep()))
            .append(",\"prayer\":\"").append(plan.protection()).append("\",\"target\":").append(plan.targetIndex())
            .append(",\"safe\":").append(plan.safe()).append(",\"risk\":").append(plan.risk())
            .append(",\"reason\":\"").append(escape(plan.reason())).append("\"}");
        write(b.append('}').toString());
    }
    protected static String tile(Tile tile){return tile==null?"null":"["+tile.x()+","+tile.y()+"]";}
    String location(){return directory==null?"disabled":directory.toString();}
    protected static String escape(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
    @Override public synchronized void close(){if(writer!=null){try{writer.close();}catch(IOException ignored){}writer=null;}}    void observation(FcFrame f,int wave,Plan plan,String owner,String combat,String group,String lure,Protection requested) { }
}
