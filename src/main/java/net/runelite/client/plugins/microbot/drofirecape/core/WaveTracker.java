/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import java.util.regex.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Chat and complete NPC groups identify waves; original spawn events resolve any of 15 rotations. */
public final class WaveTracker {
    private static final Pattern WAVE=Pattern.compile("\\bWave\\s*:\\s*(\\d{1,2})\\b",Pattern.CASE_INSENSITIVE);
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class SpawnEvidence {
        private final int tick;
        private final int index;
        private final Kind kind;
        private final int templateX;
        private final int templateY;

        public SpawnEvidence(int tick, int index, Kind kind, int templateX, int templateY) {
            this.tick = tick;
            this.index = index;
            this.kind = kind;
            this.templateX = templateX;
            this.templateY = templateY;
        }

        public int tick() { return tick; }
        public int index() { return index; }
        public Kind kind() { return kind; }
        public int templateX() { return templateX; }
        public int templateY() { return templateY; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpawnEvidence)) return false;
            SpawnEvidence that = (SpawnEvidence) other;
            return tick == that.tick
                && index == that.index
                && java.util.Objects.equals(kind, that.kind)
                && templateX == that.templateX
                && templateY == that.templateY;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(tick);
            result = 31 * result + Integer.hashCode(index);
            result = 31 * result + java.util.Objects.hashCode(kind);
            result = 31 * result + Integer.hashCode(templateX);
            result = 31 * result + Integer.hashCode(templateY);
            return result;
        }

        @Override
        public String toString() {
            return "SpawnEvidence[tick=" + tick + ", index=" + index + ", kind=" + kind + ", templateX=" + templateX + ", templateY=" + templateY + "]";
        }
    }
    private final List<SpawnEvidence> pending=new ArrayList<>();
    private final Set<Integer> verifiedWaves=new HashSet<>();
    private final Set<Integer> candidates=new LinkedHashSet<>();
    private int wave,rotation,startedTick=-1,lastObservedTick=-1,emptyTicks,confirmedWaves;
    private boolean mismatch,seenMonsters,freshEntry;
    private String reason="Waiting for wave evidence";

    public WaveTracker(){reset();}
    public synchronized void reset() {
        wave=rotation=0;startedTick=lastObservedTick=-1;emptyTicks=confirmedWaves=0;
        mismatch=seenMonsters=freshEntry=false;reason="Waiting for wave evidence";
        pending.clear();verifiedWaves.clear();resetCandidates();
    }
    private void resetCandidates(){candidates.clear();for(int r=1;r<=15;r++)candidates.add(r);}
    /** Arm BEFORE the entrance dispatch; first-wave spawn delay is not a failed run. */
    public synchronized void begin(int predictedRotation) {
        reset();freshEntry=true;
        if(WaveBook.validRotation(predictedRotation))rotation=predictedRotation;
        reason="Rotation "+rotationLabel()+"; awaiting first wave (intro/spawn delay)";
    }
    public synchronized int wave(){return wave;}
    public synchronized int rotation(){return rotation;}
    public synchronized boolean mismatch(){return mismatch;}
    public synchronized String status(){return reason;}
    public synchronized int confirmations(){return confirmedWaves;}
    public synchronized boolean hasSeenMonsters(){return seenMonsters;}
    public synchronized boolean predictionReady(){return WaveBook.validRotation(rotation)&&!mismatch&&candidates.contains(rotation);}
    public synchronized Set<Integer> candidates(){return Set.copyOf(candidates);}
    private String rotationLabel(){return rotation==0?"?":String.valueOf(rotation);}

    /** A hint cannot overwrite a rotation established by live spawn evidence. */
    public synchronized void hintRotation(int value) {
        if(WaveBook.validRotation(value)&&rotation==0&&candidates.contains(value))rotation=value;
    }
    public synchronized boolean chat(String text,int tick) {
        if(text==null)return false;
        Matcher m=WAVE.matcher(text.replaceAll("<[^>]*>","").replace('\u00a0',' '));
        if(!m.find())return false;int next=Integer.parseInt(m.group(1));
        if(next<1||next>63||next<wave)return false;
        if(next==wave) {
            if(startedTick<0)startedTick=tick;
            return false; // Duplicate chat never resets spawn evidence or a live plan.
        }
        startWave(next,tick,"Game wave message");return true;
    }
    private void startWave(int next,int tick,String source) {
        wave=next;startedTick=tick;emptyTicks=0;seenMonsters=false;
        mismatch=false;
        // Keep the event-before-chat pair; discard earlier waves and death-spawns.
        pending.removeIf(e->e.tick()<tick-2);
        reason=source+": wave "+wave+" / rotation "+rotationLabel();
    }
    public synchronized void restore(int savedWave){restore(rotation,savedWave);}
    public synchronized void restore(int savedRotation,int savedWave) {
        if(savedWave<1||savedWave>63)return;
        if(WaveBook.validRotation(savedRotation))rotation=savedRotation;
        wave=savedWave;startedTick=-1;seenMonsters=false;emptyTicks=0;freshEntry=false;
        pending.clear();mismatch=false;
        reason="Resumed wave "+wave+" / rotation "+rotationLabel()+"; live combat active";
    }
    /** Instance reconstruction must not treat moved NPC positions as original spawns. */
    public synchronized void sceneReloaded() {
        pending.clear();startedTick=-1;emptyTicks=0;lastObservedTick=-1;freshEntry=false;
         // Preserve wave, rotation and accumulated candidate evidence.
    }
    public synchronized void spawned(SpawnEvidence e) {
        if(e==null)return;
        pending.add(e);if(pending.size()>128)pending.remove(0);
    }

    /**
     * Read-only scene fallback. Chat is preferred, but attacks do not require chat.
     * Never identify a later wave from the leftovers of the current wave: advancing
     * without chat requires a clear interval AND the complete next-wave composition.
     * Baby blobs and healers cannot be mistaken for a new primary wave.
     */
    public synchronized void observe(int tick,Collection<Mob> mobs,int predictorWave) {
        if(mobs==null)return;
        boolean newTick=tick!=lastObservedTick;
        if(newTick)lastObservedTick=tick;
        if(wave==0&&freshEntry&&predictorWave==1&&mobs.isEmpty()) {
            wave=1;startedTick=-1;reason="Wave 1 armed; waiting for cave introduction / first spawn";
        }
        if(mobs.isEmpty()) {
            if(newTick&&seenMonsters)emptyTicks++;
            return;
        }
        int previous=wave;
        boolean clearInterval=seenMonsters&&emptyTicks>=2;
        if(wave==0) {
            if(freshEntry&&WaveBook.compositionMatches(1,mobs))
                startWave(1,earliestRecentSpawn(tick),"First-wave NPC evidence");
            else if(predictorWave>=1&&predictorWave<=63&&WaveBook.compositionMatches(predictorWave,mobs)) {
                startWave(predictorWave,-1,"Predictor wave + matching NPC group");
                // Unknown mid-wave adoption is NOT independent spawn-location proof.
            }
        } else if(seenMonsters&&emptyTicks>=2&&wave<63&&WaveBook.compositionMatches(wave+1,mobs)) {
            startWave(wave+1,earliestRecentSpawn(tick),"Clear interval + next-wave NPC group");
        } else if(predictorWave>wave&&predictorWave<=63&&emptyTicks>=2
                &&WaveBook.compositionMatches(predictorWave,mobs)) {
            startWave(predictorWave,earliestRecentSpawn(tick),"Predictor resynchronization + complete NPC group");
        }
        if(wave>0) {
            if(!seenMonsters&&startedTick<0&&freshEntry&&wave==1)
                startedTick=earliestRecentSpawn(tick);
            seenMonsters=true;
            boolean babies=mobs.stream().anyMatch(m->m.kind()==Kind.BABY);
            if(!clearInterval||wave!=previous||babies)emptyTicks=0;
        }
        if(wave>0)freshEntry=false;
    }
    private int earliestRecentSpawn(int tick) {
        int first=tick;
        for(SpawnEvidence e:pending)if(e.tick()>=tick-3&&e.tick()<=tick&&e.kind()!=Kind.BABY&&e.kind()!=Kind.HEALER)
            first=Math.min(first,e.tick());
        return first;
    }
    public synchronized void verify(int tick) {
        if(wave<1||startedTick<0||tick-startedTick<2||tick-startedTick>10||verifiedWaves.contains(wave))return;
        Map<Integer,SpawnEvidence> unique=new LinkedHashMap<>();
        for(SpawnEvidence e:pending) {
            if(e.tick()<startedTick-2||e.tick()>startedTick+3||e.kind()==Kind.HEALER||e.kind()==Kind.BABY)continue;
            unique.putIfAbsent(e.index(),e);
        }
        if(unique.isEmpty())return;
        // Incomplete spawn packets must not discard the correct rotation prematurely.
        Map<Kind,Integer> actual=new EnumMap<>(Kind.class);
        for(SpawnEvidence e:unique.values())actual.merge(e.kind(),1,Integer::sum);
        if(!actual.equals(WaveBook.composition(wave))) {
            if(tick-startedTick>=6)reason="Partial spawn evidence: wave "+wave+"; continuing live combat";
            return;
        }
        Set<Integer> matching=new LinkedHashSet<>();
        for(int r=1;r<=15;r++)if(matches(r,unique.values()))matching.add(r);
        if(matching.isEmpty()) {
            mismatch=true;reason="Spawn positions differ from known tables at wave "+wave+"; live combat, previews suspended";
            return;
        }
        Set<Integer> intersect=new LinkedHashSet<>(candidates);intersect.retainAll(matching);
        // Recover from a stale outside prediction or reconstructed scene evidence.
        candidates.clear();candidates.addAll(intersect.isEmpty()?matching:intersect);
        mismatch=false;
        if(candidates.size()==1)rotation=candidates.iterator().next();
        else if(!candidates.contains(rotation))rotation=0;
        verifiedWaves.add(wave);confirmedWaves++;
        reason=rotation>0?"Rotation "+rotation+" spawn group verified: wave "+wave
            :"Wave "+wave+" verified; rotation candidates "+candidates+" (combat continues)";
    }
    private boolean matches(int r,Collection<SpawnEvidence> evidence) {
        List<WaveBook.Spawned> remaining=new ArrayList<>(WaveBook.wave(r,wave));
        for(SpawnEvidence e:evidence) {
            int rx=e.templateX()-WaveBook.BASE_X,ry=e.templateY()-WaveBook.BASE_Y;
            int match=-1;
            for(int i=0;i<remaining.size();i++) {
                WaveBook.Spawned expected=remaining.get(i);
                if(expected.kind()==e.kind()&&Math.abs(expected.location().x-rx)<=2
                    &&Math.abs(expected.location().y-ry)<=2){match=i;break;}
            }
            if(match<0)return false;
            remaining.remove(match);
        }
        return remaining.isEmpty();
    }
}
