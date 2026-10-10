/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

/** Immutable snapshots; no live client objects are used by the planner. */
public final class FcModel {
    private FcModel() {}
    public enum Protection { NONE, MELEE, RANGE, MAGIC }
    public enum Kind {
        BAT("Tz-Kih",1,4,1,Protection.MELEE,5),
        BLOB("Tz-Kek",2,4,1,Protection.MELEE,7),
        BABY("Tz-Kek",1,4,1,Protection.MELEE,4),
        RANGER("Tok-Xil",3,4,15,Protection.RANGE,13),
        MELEER("Yt-MejKot",4,4,1,Protection.MELEE,25),
        MAGER("Ket-Zek",5,4,15,Protection.MAGIC,55),
        JAD("TzTok-Jad",5,8,15,Protection.MAGIC,97),
        HEALER("Yt-HurKot",1,4,1,Protection.MELEE,14);
        public final String name;
        public final int size, speed, range, maxHit;
        public final Protection protection;
        Kind(String name,int size,int speed,int range,Protection protection,int maxHit) {
            this.name=name;this.size=size;this.speed=speed;this.range=range;
            this.protection=protection;this.maxHit=maxHit;
        }
        public static Kind identify(String name,int size) {
            if(name==null)return null;
            for(Kind k:values()) if(k.name.equalsIgnoreCase(name)) {
                return k==BLOB && size==1?BABY:k;
            }
            return null;
        }
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class Tile {
        private final int x;
        private final int y;

        public Tile(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() { return x; }
        public int y() { return y; }

        public int distance(Tile b){return Math.max(Math.abs(x-b.x),Math.abs(y-b.y));}
        public Tile add(int dx,int dy){return new Tile(x+dx,y+dy);}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Tile)) return false;
            Tile that = (Tile) other;
            return x == that.x
                && y == that.y;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            return result;
        }

        @Override
        public String toString() {
            return "Tile[x=" + x + ", y=" + y + "]";
        }
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class Mob {
        private final int index;
        private final Kind kind;
        private final Tile tile;
        private final int size;
        private final int healthRatio;
        private final int healthScale;
        private final int lastAttackTick;
        private final Protection lastStyle;
        private final boolean attackingPlayer;

        public Mob(int index, Kind kind, Tile tile, int size, int healthRatio, int healthScale, int lastAttackTick, Protection lastStyle, boolean attackingPlayer) {
            this.index = index;
            this.kind = kind;
            this.tile = tile;
            this.size = size;
            this.healthRatio = healthRatio;
            this.healthScale = healthScale;
            this.lastAttackTick = lastAttackTick;
            this.lastStyle = lastStyle;
            this.attackingPlayer = attackingPlayer;
        }

        public int index() { return index; }
        public Kind kind() { return kind; }
        public Tile tile() { return tile; }
        public int size() { return size; }
        public int healthRatio() { return healthRatio; }
        public int healthScale() { return healthScale; }
        public int lastAttackTick() { return lastAttackTick; }
        public Protection lastStyle() { return lastStyle; }
        public boolean attackingPlayer() { return attackingPlayer; }

        public boolean occupies(Tile p) {
            return p.x>=tile.x&&p.x<tile.x+size&&p.y>=tile.y&&p.y<tile.y+size;
        }
        public int distance(Tile p) {
            int dx=Math.max(Math.max(tile.x-p.x,0),p.x-(tile.x+size-1));
            int dy=Math.max(Math.max(tile.y-p.y,0),p.y-(tile.y+size-1));
            return Math.max(dx,dy);
        }
        public Mob at(Tile p){return new Mob(index,kind,p,size,healthRatio,healthScale,lastAttackTick,lastStyle,attackingPlayer);}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Mob)) return false;
            Mob that = (Mob) other;
            return index == that.index
                && java.util.Objects.equals(kind, that.kind)
                && java.util.Objects.equals(tile, that.tile)
                && size == that.size
                && healthRatio == that.healthRatio
                && healthScale == that.healthScale
                && lastAttackTick == that.lastAttackTick
                && java.util.Objects.equals(lastStyle, that.lastStyle)
                && attackingPlayer == that.attackingPlayer;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(index);
            result = 31 * result + java.util.Objects.hashCode(kind);
            result = 31 * result + java.util.Objects.hashCode(tile);
            result = 31 * result + Integer.hashCode(size);
            result = 31 * result + Integer.hashCode(healthRatio);
            result = 31 * result + Integer.hashCode(healthScale);
            result = 31 * result + Integer.hashCode(lastAttackTick);
            result = 31 * result + java.util.Objects.hashCode(lastStyle);
            result = 31 * result + Boolean.hashCode(attackingPlayer);
            return result;
        }

        @Override
        public String toString() {
            return "Mob[index=" + index + ", kind=" + kind + ", tile=" + tile + ", size=" + size + ", healthRatio=" + healthRatio + ", healthScale=" + healthScale + ", lastAttackTick=" + lastAttackTick + ", lastStyle=" + lastStyle + ", attackingPlayer=" + attackingPlayer + "]";
        }
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class Snapshot {
        private final int tick;
        private final Tile player;
        private final CollisionGrid grid;
        private final List<Mob> mobs;
        private final int runEnergy;
        private final boolean running;
        private final int weaponRange;
        private final Protection jadStyle;
        private final boolean meleeMode;
        private final int wave;

        public Snapshot(int tick, Tile player, CollisionGrid grid, List<Mob> mobs, int runEnergy, boolean running, int weaponRange, Protection jadStyle) {
            this(tick,player,grid,mobs,runEnergy,running,weaponRange,jadStyle,false);
        }
        public Snapshot(int tick, Tile player, CollisionGrid grid, List<Mob> mobs, int runEnergy, boolean running, int weaponRange, Protection jadStyle, boolean meleeMode) {
            this(tick,player,grid,mobs,runEnergy,running,weaponRange,jadStyle,meleeMode,0);
        }
        private Snapshot(int tick, Tile player, CollisionGrid grid, List<Mob> mobs, int runEnergy, boolean running, int weaponRange, Protection jadStyle, boolean meleeMode,int wave) {
            this.wave = wave;
            this.meleeMode = meleeMode;
            this.tick = tick;
            this.player = player;
            this.grid = grid;
            this.mobs = List.copyOf(mobs);
            this.runEnergy = runEnergy;
            this.running = running;
            this.weaponRange = meleeMode ? 1 : weaponRange;
            this.jadStyle = jadStyle;
        }

        public int tick() { return tick; }
        public Tile player() { return player; }
        public CollisionGrid grid() { return grid; }
        public List<Mob> mobs() { return mobs; }
        public int runEnergy() { return runEnergy; }
        public boolean running() { return running; }
        public int weaponRange() { return weaponRange; }
        public Protection jadStyle() { return jadStyle; }
        public boolean meleeMode() { return meleeMode; }
        public int wave() { return wave; }
        public Snapshot atWave(int value){return value==wave?this:new Snapshot(tick,player,grid,mobs,runEnergy,running,weaponRange,jadStyle,meleeMode,value);}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return tick == that.tick
                && java.util.Objects.equals(player, that.player)
                && java.util.Objects.equals(grid, that.grid)
                && java.util.Objects.equals(mobs, that.mobs)
                && runEnergy == that.runEnergy
                && running == that.running
                && meleeMode == that.meleeMode
                && wave == that.wave
                && weaponRange == that.weaponRange
                && java.util.Objects.equals(jadStyle, that.jadStyle);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(tick);
            result = 31 * result + java.util.Objects.hashCode(player);
            result = 31 * result + java.util.Objects.hashCode(grid);
            result = 31 * result + java.util.Objects.hashCode(mobs);
            result = 31 * result + Integer.hashCode(runEnergy);
            result = 31 * result + Boolean.hashCode(running);
            result = 31 * result + Boolean.hashCode(meleeMode);
            result = 31 * result + Integer.hashCode(wave);
            result = 31 * result + Integer.hashCode(weaponRange);
            result = 31 * result + java.util.Objects.hashCode(jadStyle);
            return result;
        }

        @Override
        public String toString() {
            return "Snapshot[tick=" + tick + ", player=" + player + ", grid=" + grid + ", mobs=" + mobs + ", runEnergy=" + runEnergy + ", running=" + running + ", weaponRange=" + weaponRange + ", jadStyle=" + jadStyle + "]";
        }
    }
    /** Java 11 value type; preserves the former record API and value semantics. */
    public static final class Plan {
        private final Tile destination;
        private final Tile nextStep;
        private final Protection protection;
        private final int targetIndex;
        private final boolean safe;
        private final int blockedMobs;
        private final int exposedStyles;
        private final int risk;
        private final String reason;

        public Plan(Tile destination, Tile nextStep, Protection protection, int targetIndex, boolean safe, int blockedMobs, int exposedStyles, int risk, String reason) {
            this.destination = destination;
            this.nextStep = nextStep;
            this.protection = protection;
            this.targetIndex = targetIndex;
            this.safe = safe;
            this.blockedMobs = blockedMobs;
            this.exposedStyles = exposedStyles;
            this.risk = risk;
            this.reason = reason;
        }

        public Tile destination() { return destination; }
        public Tile nextStep() { return nextStep; }
        public Protection protection() { return protection; }
        public int targetIndex() { return targetIndex; }
        public boolean safe() { return safe; }
        public int blockedMobs() { return blockedMobs; }
        public int exposedStyles() { return exposedStyles; }
        public int risk() { return risk; }
        public String reason() { return reason; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Plan)) return false;
            Plan that = (Plan) other;
            return java.util.Objects.equals(destination, that.destination)
                && java.util.Objects.equals(nextStep, that.nextStep)
                && java.util.Objects.equals(protection, that.protection)
                && targetIndex == that.targetIndex
                && safe == that.safe
                && blockedMobs == that.blockedMobs
                && exposedStyles == that.exposedStyles
                && risk == that.risk
                && java.util.Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(destination);
            result = 31 * result + java.util.Objects.hashCode(nextStep);
            result = 31 * result + java.util.Objects.hashCode(protection);
            result = 31 * result + Integer.hashCode(targetIndex);
            result = 31 * result + Boolean.hashCode(safe);
            result = 31 * result + Integer.hashCode(blockedMobs);
            result = 31 * result + Integer.hashCode(exposedStyles);
            result = 31 * result + Integer.hashCode(risk);
            result = 31 * result + java.util.Objects.hashCode(reason);
            return result;
        }

        @Override
        public String toString() {
            return "Plan[destination=" + destination + ", nextStep=" + nextStep + ", protection=" + protection + ", targetIndex=" + targetIndex + ", safe=" + safe + ", blockedMobs=" + blockedMobs + ", exposedStyles=" + exposedStyles + ", risk=" + risk + ", reason=" + reason + "]";
        }
    }
}
