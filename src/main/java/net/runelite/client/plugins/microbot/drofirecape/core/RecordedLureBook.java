/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Tile;

/**
 * Candidate positions from the user's two R5 recordings, NOT a timed click replay.
 * Coordinates are relative to Fight Caves template region 9551, never instance-world
 * coordinates. See docs/RECORDING-REVIEW.md for source ticks and exclusions.
 * A candidate must still pass the current collision/threat checks. A position alone
 * is not a safespot: which side the NPC approached from matters.
 */
public final class RecordedLureBook {
    private RecordedLureBook() { }
    public static final Tile ITALY = new Tile(46,44);
    public static final Tile PULL = new Tile(54,61);
    public static final Tile NORTHWEST = new Tile(23,60);
    public static final Tile WEST_PEEK = new Tile(44,44);
    public static final Tile POCKET_EAST = new Tile(54,48); // 2422,5104
    public static final Tile POCKET_WEST = new Tile(45,48); // 2413,5104
    public static final Tile MELEE_WALL = new Tile(45,39); // 2413,5095
    private static final Tile EAST = ITALY;
    public static final Tile SOUTH_FACE = new Tile(45,38);
    public static List<Tile> anchors(){return List.of(ITALY,SOUTH_FACE,WEST_PEEK,PULL,NORTHWEST,MELEE_WALL);}
    public static List<Tile> anchorsWithPockets(){return List.of(ITALY,SOUTH_FACE,WEST_PEEK,PULL,NORTHWEST,MELEE_WALL,POCKET_EAST,POCKET_WEST);}
    private static List<Tile> points(int... xy) {
        ArrayList<Tile> result=new ArrayList<>();
        for(int i=0;i<xy.length;i+=2)result.add(new Tile(xy[i],xy[i+1]));
        return List.copyOf(result);
    }
    public static boolean hasMistakeMarker(int wave) {
        return wave==4||wave==11||wave==12||wave==17||wave==18||wave==19;
    }
    public static List<Tile> candidates(int rotation,int wave) {
        // No invented demonstrations for the other rotations or waves 26 onward.
        if(rotation!=5||wave<1||wave>25||hasMistakeMarker(wave))return List.of();
        switch(wave) {
            case 1: return points(42,48,48,49,50,49);
            case 2: return points(46,44,48,44,51,45,50,44,46,44,45,44,46,44);
            case 3: return points(46,44,44,44,46,44);
            case 4: return points(46,44);
            case 5: return points(49,44,53,44,49,44,44,44,46,44);
            case 6: return points(46,44,52,44,51,45);
            case 7: return points(46,44,38,37);
            case 8: return points(40,39,42,43,42,35);
            case 9: return points(46,44,50,44,46,44,50,44,45,37);
            case 10: return points(46,44,52,44,45,22,45,38,45,35);
            case 11: return points(38,37,45,38);
            case 12: return points(30,48,28,48);
            case 13: return points(54,44,54,61,47,45,42,43,45,43,46,44);
            case 14: return points(52,44,40,40,41,36);
            case 15: return points(53,45,45,36);
            case 16: return points(49,44,30,48);
            case 17: return points(45,40,45,36);
            case 18: return points(51,44,45,38);
            case 19: return points(53,44,45,39);
            case 20: return points(45,39,51,44,45,38,45,38,45,38,45,35);
            case 21: return points(49,44,51,45,23,52,23,56,23,54,29,48,33,48,31,48,28,48);
            case 22: return points(50,45,53,44,45,36,45,38);
            case 23: return points(51,45,53,44,47,44,45,40,45,38);
            case 24: return points(41,38,54,61,52,61,48,52,45,40);
            case 25: return points(45,40,43,42,54,56,45,39);
            default: return List.of(ITALY);
        }
    }
    /** Only an observed opening. Wave 15 is intentionally absent: its opening is missing. */
    public static Tile opening(int rotation,int wave) {
        if(rotation!=5)return null;
        if(wave==1)return new Tile(42,48);
        if(wave>=2&&wave<=6&&wave!=4||wave==9||wave==10)return EAST;
        if(wave==14)return new Tile(52,44);
        // No demonstrated opening is inferred from a wave containing MISTAKE.
        if(wave==4||wave==11||wave==12||wave==17||wave==18||wave==19)return null;
        if(wave==20)return new Tile(51,44);
        if(wave==22)return new Tile(53,44);
        if(wave==23)return new Tile(51,45);
        return null;
    }
}
