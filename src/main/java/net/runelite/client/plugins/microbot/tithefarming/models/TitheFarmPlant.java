/*
 * Copyright (c) 2023, Mocrosoft <https://github.com/chsami>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.tithefarming.models;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.tithefarm.TitheFarmPlantState;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public class TitheFarmPlant {
    private static final Duration PLANT_TIME = Duration.ofMinutes(1);

    @Getter
    @Setter
    private int index;

    @Getter
    @Setter
    private Instant planted;

    @Getter
    private final TitheFarmPlantState state;

    public int regionX;
    public int regionY;

    public TitheFarmPlant(int regionX, int regionY, int index) {
        this.planted = Instant.now();
        this.state = TitheFarmPlantState.UNWATERED;
        this.regionX = regionX;
        this.regionY = regionY;
        this.index = index;
    }

    public Rs2TileObjectModel getGameObject() {
        // The hardcoded lane definitions are off by 1 from the actual instance patch
        // tiles (verified via the agent server: lane defines (35,25) but the cache has
        // a tithe patch at local (34,24), and so on for every patch). Allow a 1-tile
        // tolerance match so the lookup is robust to this without rewriting all four
        // lane lists. Patches are 3 tiles apart so the tolerance can't match the
        // wrong neighbour.
        return Microbot.getRs2TileObjectCache().query()
                .where(o -> {
                    if (!isTithePatchId(o.getId())) return false;
                    WorldPoint wp = o.getWorldLocation();
                    if (wp == null) return false;
                    int dx = Math.abs(wp.getRegionX() - regionX);
                    int dy = Math.abs(wp.getRegionY() - regionY);
                    return dx <= 1 && dy <= 1;
                })
                .first();
    }

    private static boolean isTithePatchId(int id) {
        switch (id) {
            case ObjectID.HOSIDIUS_TITHE_EMPTY:
            case ObjectID.HOSIDIUS_TITHE_A_1_DRY:
            case ObjectID.HOSIDIUS_TITHE_A_2_DRY:
            case ObjectID.HOSIDIUS_TITHE_A_3_DRY:
            case ObjectID.HOSIDIUS_TITHE_A_4:
            case ObjectID.HOSIDIUS_TITHE_A_1_WET:
            case ObjectID.HOSIDIUS_TITHE_A_2_WET:
            case ObjectID.HOSIDIUS_TITHE_A_3_WET:
            case ObjectID.HOSIDIUS_TITHE_B_1_DRY:
            case ObjectID.HOSIDIUS_TITHE_B_2_DRY:
            case ObjectID.HOSIDIUS_TITHE_B_3_DRY:
            case ObjectID.HOSIDIUS_TITHE_B_4:
            case ObjectID.HOSIDIUS_TITHE_B_1_WET:
            case ObjectID.HOSIDIUS_TITHE_B_2_WET:
            case ObjectID.HOSIDIUS_TITHE_B_3_WET:
            case ObjectID.HOSIDIUS_TITHE_C_1_DRY:
            case ObjectID.HOSIDIUS_TITHE_C_2_DRY:
            case ObjectID.HOSIDIUS_TITHE_C_3_DRY:
            case ObjectID.HOSIDIUS_TITHE_C_4:
            case ObjectID.HOSIDIUS_TITHE_C_1_WET:
            case ObjectID.HOSIDIUS_TITHE_C_2_WET:
            case ObjectID.HOSIDIUS_TITHE_C_3_WET:
                return true;
            default:
                return false;
        }
    }

    public int[] expectedPatchGameObject() {
        if (Objects.requireNonNull(TitheFarmingScript.state) == TitheFarmState.PLANTING_SEEDS) {
            return new int[]{ObjectID.HOSIDIUS_TITHE_EMPTY, ObjectID.HOSIDIUS_TITHE_A_1_DRY, ObjectID.HOSIDIUS_TITHE_B_1_DRY, ObjectID.HOSIDIUS_TITHE_C_1_DRY};
        }
        return new int[]{};
    }

    public int[] expectedWateredObject() {
        switch (TitheFarmingScript.state) {
            case PLANTING_SEEDS:
                if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.BOLOGANO_SEED) {
                    return new int[]{ObjectID.HOSIDIUS_TITHE_B_1_DRY, ObjectID.HOSIDIUS_TITHE_B_2_DRY, ObjectID.HOSIDIUS_TITHE_B_3_DRY};
                } else if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.LOGAVANO_SEED) {
                    return new int[]{ObjectID.HOSIDIUS_TITHE_C_1_DRY, ObjectID.HOSIDIUS_TITHE_C_2_DRY, ObjectID.HOSIDIUS_TITHE_C_3_DRY};
                } else if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.GOLOVANOVA_SEED) {
                    return new int[]{ObjectID.HOSIDIUS_TITHE_A_1_DRY, ObjectID.HOSIDIUS_TITHE_A_2_DRY, ObjectID.HOSIDIUS_TITHE_A_3_DRY};
                }
            case HARVEST:
                //does not apply
                break;
        }
        return new int[]{};
    }

    public int expectedHarvestObject() {
        if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.BOLOGANO_SEED) {
            return ObjectID.HOSIDIUS_TITHE_B_4;
        } else if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.LOGAVANO_SEED) {
            return ObjectID.HOSIDIUS_TITHE_C_4;
        } else if (TitheFarmMaterial.getSeedForLevel() == TitheFarmMaterial.GOLOVANOVA_SEED) {
            return ObjectID.HOSIDIUS_TITHE_A_4;
        }
        return -1;
    }

    public boolean isEmptyPatch() {
        Rs2TileObjectModel obj = getGameObject();
        return obj != null && obj.getId() == ObjectID.HOSIDIUS_TITHE_EMPTY;
    }

    public boolean isEmptyPatchOrSeedling() {
        Rs2TileObjectModel obj = getGameObject();
        if (obj == null) return false;
        int objId = obj.getId();
        return Arrays.stream(expectedPatchGameObject()).anyMatch(id -> id == objId);
    }

    public boolean isValidToWater() {
        Rs2TileObjectModel obj = getGameObject();
        if (obj == null) return false;
        int objId = obj.getId();
        return Arrays.stream(expectedWateredObject()).anyMatch(id -> id == objId) || isStage1() || isStage2();
    }

    public boolean isValidToHarvest() {
        Rs2TileObjectModel obj = getGameObject();
        return obj != null && obj.getId() == expectedHarvestObject();
    }

    public boolean isStage1() {
        Rs2TileObjectModel obj = getGameObject();
        if (obj == null) return false;
        int id = obj.getId();
        return id == ObjectID.HOSIDIUS_TITHE_A_2_DRY
                || id == ObjectID.HOSIDIUS_TITHE_B_2_DRY
                || id == ObjectID.HOSIDIUS_TITHE_C_2_DRY;
    }

    public boolean isStage2() {
        Rs2TileObjectModel obj = getGameObject();
        if (obj == null) return false;
        int id = obj.getId();
        return id == ObjectID.HOSIDIUS_TITHE_A_3_DRY
                || id == ObjectID.HOSIDIUS_TITHE_B_3_DRY
                || id == ObjectID.HOSIDIUS_TITHE_C_3_DRY;
    }

    public boolean isWatered() {
        Rs2TileObjectModel obj = getGameObject();
        if (obj == null) return false;
        var id = obj.getId();
        switch (id) {
            case ObjectID.HOSIDIUS_TITHE_B_1_WET:
            case ObjectID.HOSIDIUS_TITHE_B_2_WET:
            case ObjectID.HOSIDIUS_TITHE_B_3_WET:
            case ObjectID.HOSIDIUS_TITHE_A_1_WET:
            case ObjectID.HOSIDIUS_TITHE_A_2_WET:
            case ObjectID.HOSIDIUS_TITHE_A_3_WET:
            case ObjectID.HOSIDIUS_TITHE_C_1_WET:
            case ObjectID.HOSIDIUS_TITHE_C_2_WET:
            case ObjectID.HOSIDIUS_TITHE_C_3_WET:
                return true;
            default:
                return false;
        }
    }

    public double getPlantTimeRelative() {
        Duration duration = Duration.between(planted, Instant.now());
        return duration.compareTo(PLANT_TIME) < 0 ? (double) duration.toMillis() / PLANT_TIME.toMillis() : 2;
    }
}
