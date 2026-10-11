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
final class OptionalTelemetry extends FcTelemetry {
    @Override
    void open(boolean enabled) {
        close();maps.clear();directory=null;
        if(!enabled)return;
        try {
            String stamp=DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").format(LocalDateTime.now());
            directory=Paths.get(System.getProperty("user.home"),".runelite","dro-firecape",stamp);
            Files.createDirectories(directory);writer=Files.newBufferedWriter(directory.resolve("events.jsonl"));
            event("session","controller "+DroFirecapePlugin.version+"; frame schema 3; full background observations including healers; local movement acknowledgements; live validation pending");
        }catch(IOException e){Microbot.log("[Dro Firecape] Trace unavailable: "+e);}
    }
    @Override
    synchronized void decision(FcFrame f,int wave,Plan plan) {
        if(writer==null)return;
        event("decision","tick="+f.tick+" wave="+wave+" player="+f.model.player()+" template="+f.template
            +" energy="+f.rawEnergy+" overhead="+f.overhead+" plan="+plan+" mobs="+f.model.mobs());
        snapshot(f,wave,plan,"frame","", "", "", "",Protection.NONE);
    }
    /** Called once per observed tick on the BACKGROUND loop, including early
     * healer/supply/pause returns. The plan is a recorded intent, not an input ACK. */
    synchronized void observation(FcFrame f,int wave,Plan plan,String owner,String combat,
                                  String group,String lure,Protection requested) {
        if(writer==null)return;
        snapshot(f,wave,plan,"observation",owner,combat,group,lure,requested);
    }
    protected void snapshot(FcFrame f,int wave,Plan plan,String type,String owner,String combat,
                          String group,String lure,Protection requested) {
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
        b.append("{\"type\":\"").append(type).append("\",\"schema\":3,\"tick\":").append(f.tick)
            .append(",\"wave\":").append(wave).append(",\"base\":[").append(f.baseX).append(',').append(f.baseY).append(',').append(f.plane)
            .append("],\"map\":\"").append(mapName).append("\",\"player\":").append(tile(f.model.player()))
            .append(",\"time\":\"").append(Instant.now()).append("\",\"capturedAtMs\":").append(f.capturedAt)
            .append(",\"world\":").append(f.world).append(",\"moving\":").append(f.moving)
            .append(",\"ownerDetector\":\"").append(escape(owner)).append("\",\"combatPhaseAtLog\":\"").append(escape(combat))
            .append("\",\"healerGroupAtLog\":\"").append(escape(group)).append("\",\"healerLureAtLog\":\"").append(escape(lure))
            .append("\",\"requestedPrayer\":\"").append(requested).append("\",\"observedPrayer\":\"").append(f.overhead).append('"')
            .append(",\"maxHp\":").append(f.maxHp).append(",\"maxPrayer\":").append(f.maxPrayer)
            .append(",\"baseDefence\":").append(f.baseDefence).append(",\"baseRanged\":").append(f.baseRanged)
            .append(",\"ranged\":").append(f.ranged).append(",\"baseMagic\":").append(f.baseMagic).append(",\"magic\":").append(f.magic)
            .append(",\"weaponId\":").append(f.weaponId).append(",\"specialEnergy\":").append(f.specialEnergy)
            .append(",\"template\":").append(f.template==null?"null":"["+f.template.getX()+","+f.template.getY()+","+f.template.getPlane()+"]")
            .append(",\"healersTargetingJad\":").append(new TreeSet<>(f.healersTargetingJad))
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
        b.append("],\"inventory\":[");comma=false;
        for(FcFrame.ItemSlot item:f.inventory) {
            if(comma)b.append(',');comma=true;
            b.append("{\"slot\":").append(item.slot()).append(",\"id\":").append(item.id())
                .append(",\"quantity\":").append(item.quantity()).append('}');
        }
        b.append("],\"plan\":");
        if(plan==null)b.append("null");
        else b.append("{\"destination\":").append(tile(plan.destination())).append(",\"step\":").append(tile(plan.nextStep()))
            .append(",\"prayer\":\"").append(plan.protection()).append("\",\"target\":").append(plan.targetIndex())
            .append(",\"safe\":").append(plan.safe()).append(",\"risk\":").append(plan.risk())
            .append(",\"reason\":\"").append(escape(plan.reason())).append("\"}");
        write(b.append('}').toString());
    }}
