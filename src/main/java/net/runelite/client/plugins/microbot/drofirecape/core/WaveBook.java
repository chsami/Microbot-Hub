/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
/*
 * Spawn generation and mapping adapted from damencs/spawn-predictor.
 * Copyright (c) 2022, Damen <gh: damencs>. All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
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

public final class WaveBook {
    /** Minigame landing is west (2399,5177), bank/entrance are east (2445,5178). */
    public static boolean outerTzhaarRegion(int region){return region==9552||region==9808;}
    public static boolean entryPrayerReady(int prayerPoints){return prayerPoints>1;}

    public static final int ROTATION=5, REGION=9551, BASE_X=2368, BASE_Y=5056;
    private static final int[] COLUMNS={4,2,9,11,13,1,6,15,10,8,5,3,12,14,7,4};
    public enum Spawn {
        NW(10,50),CENTRE(30,30),SE(50,25),SOUTH(35,15),SW(10,15);
        public final int x,y;Spawn(int x,int y){this.x=x;this.y=y;}
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class Spawned {
        private final Kind kind;
        private final int wheel;

        public Spawned(Kind kind, int wheel) {
            this.kind = kind;
            this.wheel = wheel;
        }

        public Kind kind() { return kind; }
        public int wheel() { return wheel; }

        public Spawn location(){return lookup(wheel);}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Spawned)) return false;
            Spawned that = (Spawned) other;
            return java.util.Objects.equals(kind, that.kind)
                && wheel == that.wheel;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(kind);
            result = 31 * result + Integer.hashCode(wheel);
            return result;
        }

        @Override
        public String toString() {
            return "Spawned[kind=" + kind + ", wheel=" + wheel + "]";
        }
    }
    // Wiki rotation numbers, not the predictor's 1..16 clock columns.
    private static final int[] STARTS={-1,2,13,8,12,7,3,6,6,14,5,0,9,1,10,4};
    private static final Map<Integer,List<List<Spawned>>> ROTATIONS=buildRotations();
    private static Map<Integer,List<List<Spawned>>> buildRotations() {
        Map<Integer,List<List<Spawned>>> result=new HashMap<>();
        for(int rotation=1;rotation<=15;rotation++) {
            List<List<Spawned>> rows=generate(STARTS[rotation]);
            if(rotation==7) {
                // Upstream switches seed at wave 3 for the wave-4+ preview.
                // Preserve the actual waves 1..3; rotation 8 continues seed 6.
                List<List<Spawned>> shifted=generate(11);
                ArrayList<List<Spawned>> combined=new ArrayList<>(rows);
                for(int wave=4;wave<=63;wave++)combined.set(wave-1,shifted.get(wave-1));
                rows=List.copyOf(combined);
            }
            result.put(rotation,rows);
        }
        return Map.copyOf(result);
    }
    public static boolean validRotation(int rotation){return rotation>=1&&rotation<=15;}
    public static int startValue(int rotation){if(!validRotation(rotation))throw new IllegalArgumentException("Rotation must be 1..15");return STARTS[rotation];}
    private WaveBook(){}
    public static int rotationAtMinute(int serverMinutes) {
        return serverMinutes<0?-1:COLUMNS[Math.floorMod(serverMinutes,16)];
    }
    public static boolean entryWindow(int minute,int seconds) {
        return rotationAtMinute(minute)==ROTATION&&seconds>=8&&seconds<=43;
    }
    public static int secondsToWindow(int minute,int seconds) {
        if(minute<0||seconds<0||seconds>59)return -1;
        for(int d=0;d<=16*60;d++) {
            int total=seconds+d;
            if(entryWindow(minute+total/60,total%60))return d;
        }
        return -1;
    }
    public static Spawn lookup(int n) {
        switch(Math.floorMod(n,15)) {
            case 3: case 7: case 12: return Spawn.NW;
            case 2: case 8: case 13: return Spawn.CENTRE;
            case 0: case 5: case 9: return Spawn.SE;
            case 6: case 11: case 14: return Spawn.SOUTH;
            default: return Spawn.SW;
        }
    }
    public static List<Spawned> wave(int n) {
        if(n<1||n>63)throw new IllegalArgumentException("Wave must be 1..63");
        return wave(ROTATION,n); // Backwards-compatible test/tool overload only.
    }
    public static List<Spawned> wave(int rotation,int n) {
        if(!validRotation(rotation))throw new IllegalArgumentException("Rotation must be 1..15");
        if(n<1||n>63)throw new IllegalArgumentException("Wave must be 1..63");
        return ROTATIONS.get(rotation).get(n-1);
    }
    public static Map<Kind,Integer> composition(int wave) {
        EnumMap<Kind,Integer> counts=new EnumMap<>(Kind.class);
        for(Spawned s:wave(1,wave))counts.merge(s.kind(),1,Integer::sum);
        return Map.copyOf(counts);
    }
    public static boolean compositionMatches(int wave,Collection<Mob> mobs) {
        if(wave<1||wave>63||mobs==null||mobs.isEmpty())return false;
        EnumMap<Kind,Integer> actual=new EnumMap<>(Kind.class);
        for(Mob m:mobs) {
            if(m.kind()==Kind.BABY||m.kind()==Kind.HEALER)return false;
            actual.merge(m.kind(),1,Integer::sum);
        }
        return composition(wave).equals(actual);
    }
    private static List<List<Spawned>> generate(int seed) {
        ArrayList<List<Spawned>> waves=new ArrayList<>();int cycle=seed;
        for(Kind kind:new Kind[]{Kind.BAT,Kind.BLOB,Kind.RANGER,Kind.MELEER,Kind.MAGER}) {
            ArrayList<List<Spawned>> sub=new ArrayList<>();int subCycle=(cycle+1)%15;
            for(List<Spawned> old:waves) {
                ArrayList<Spawned> row=new ArrayList<>();row.add(new Spawned(kind,subCycle));
                for(int i=0;i<old.size();i++)row.add(new Spawned(old.get(i).kind(),(subCycle+i+1)%15));
                sub.add(List.copyOf(row));subCycle=(subCycle+1)%15;
            }
            waves.add(List.of(new Spawned(kind,cycle)));cycle=(cycle+1)%15;
            waves.addAll(sub);cycle=(cycle+sub.size())%15;
            waves.add(List.of(new Spawned(kind,cycle),new Spawned(kind,(cycle+1)%15)));
            cycle=(cycle+1)%15;
        }
        waves.add(List.of(new Spawned(Kind.JAD,cycle)));return List.copyOf(waves);
    }
    public static String describe(int n) {
        if(n<1||n>63)return "-";
        return describe(ROTATION,n);
    }
    public static String describe(int rotation,int n) {
        if(!validRotation(rotation)||n<1||n>63)return "Awaiting rotation/wave evidence";
        return wave(rotation,n).stream().map(s->s.kind().name()+"@"+s.location()).reduce((a,b)->a+", "+b).orElse("-");
    }
}
