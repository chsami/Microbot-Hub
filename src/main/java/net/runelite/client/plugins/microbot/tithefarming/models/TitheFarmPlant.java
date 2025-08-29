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
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.tithefarm.TitheFarmPlantState;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static net.runelite.api.coords.WorldPoint.fromRegion;

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

    @Getter
    @Setter
    private TileObject gameObject;

    public int regionX;
    public int regionY;

    public TitheFarmPlant(int regionX, int regionY, int index) {
        this.planted = Instant.now();
        this.state = TitheFarmPlantState.UNWATERED;
        this.gameObject = Rs2GameObject.findGameObjectByLocation(fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(), regionX, regionY, 0));
        this.regionX = regionX;
        this.regionY = regionY;
        this.index = index;
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
        return gameObject.getId() == ObjectID.HOSIDIUS_TITHE_EMPTY;
    }

    public boolean isEmptyPatchOrSeedling() {
        return Arrays.stream(expectedPatchGameObject()).anyMatch(id -> id == gameObject.getId());
    }

    public boolean isValidToWater() {
        return Arrays.stream(expectedWateredObject()).anyMatch(id -> id == gameObject.getId()) || isStage1() || isStage2();
    }

    public boolean isValidToHarvest() {
        return gameObject.getId() == expectedHarvestObject();
    }

    public boolean isStage1() {
        return getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_A_2_DRY
                || getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_B_2_DRY
                || getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_C_2_DRY;
    }

    public boolean isStage2() {
        return getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_A_3_DRY
                || getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_B_3_DRY
                || getGameObject().getId() == ObjectID.HOSIDIUS_TITHE_C_3_DRY;
    }

    public boolean isWatered() {
        var id = getGameObject().getId();
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
