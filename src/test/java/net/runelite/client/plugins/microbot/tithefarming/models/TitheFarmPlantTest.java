package net.runelite.client.plugins.microbot.tithefarming.models;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TitheFarmPlantTest {

    private static final WorldPoint PATCH = new WorldPoint(1792 + 35, 3520 + 25, 0);

    @Test
    void blightedPlantsAreFoundAsPatchesAndReportedDead() {
        assertDead(ObjectID.HOSIDIUS_TITHE_A_1_DEAD);
        assertDead(ObjectID.HOSIDIUS_TITHE_A_2_DEAD);
        assertDead(ObjectID.HOSIDIUS_TITHE_A_3_DEAD);
        assertDead(ObjectID.HOSIDIUS_TITHE_A_4_DEAD);
        assertDead(ObjectID.HOSIDIUS_TITHE_B_2_DEAD);
        assertDead(ObjectID.HOSIDIUS_TITHE_C_3_DEAD);
    }

    @Test
    void growingPlantsAndEmptyPatchesAreNotDead() {
        for (int id : new int[]{ObjectID.HOSIDIUS_TITHE_EMPTY, ObjectID.HOSIDIUS_TITHE_A_2_DRY, ObjectID.HOSIDIUS_TITHE_A_4}) {
            Lookup lookup = lookUp(id);
            assertTrue(lookup.matches, "patch id " + id + " should be found");
            assertFalse(lookup.dead, "patch id " + id + " should not be dead");
        }
    }

    @Test
    void aPlantWithUnknownPlantingTimeIsTreatedAsAboutToGrow() {
        assertTrue(new TitheFarmPlant(35, 25, 1).growsWithin(2, Duration.ofSeconds(30), Instant.EPOCH));
    }

    @Test
    void aDryPlantIsAboutToGrowOnlyWithinTheMarginOfItsNextStage() {
        TitheFarmPlant plant = new TitheFarmPlant(35, 25, 1);
        Instant planted = Instant.parse("2026-11-09T12:00:00Z");
        plant.setPlanted(planted);
        Duration margin = Duration.ofSeconds(30);

        assertFalse(plant.growsWithin(2, margin, planted.plusSeconds(65)));
        assertTrue(plant.growsWithin(2, margin, planted.plusSeconds(90)));
        assertFalse(plant.growsWithin(3, margin, planted.plusSeconds(125)));
        assertTrue(plant.growsWithin(3, margin, planted.plusSeconds(150)));
    }

    @Test
    void aDryPlantStillShownAtItsStageAfterTheEstimatedGrowthStaysUrgent() {
        TitheFarmPlant plant = new TitheFarmPlant(35, 25, 1);
        Instant planted = Instant.parse("2026-11-09T12:00:00Z");
        plant.setPlanted(planted);
        Duration margin = Duration.ofSeconds(30);

        assertTrue(plant.growsWithin(2, margin, planted.plusSeconds(121)));
        assertTrue(plant.growsWithin(3, margin, planted.plusSeconds(185)));
    }

    private static void assertDead(int id) {
        Lookup lookup = lookUp(id);
        assertTrue(lookup.matches, "dead id " + id + " should be found as a tithe patch");
        assertTrue(lookup.dead, "dead id " + id + " should be reported dead");
    }

    @SuppressWarnings("unchecked")
    private static Lookup lookUp(int id) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.getId()).thenReturn(id);
        when(model.getWorldLocation()).thenReturn(PATCH);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        ArgumentCaptor<Predicate<Rs2TileObjectModel>> filter = ArgumentCaptor.forClass(Predicate.class);
        when(query.where(filter.capture())).thenReturn(query);
        when(query.first()).thenAnswer(call -> filter.getValue().test(model) ? model : null);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            TitheFarmPlant plant = new TitheFarmPlant(35, 25, 1);
            return new Lookup(plant.getGameObject() != null, plant.isDead());
        }
    }

    private static final class Lookup {
        final boolean matches;
        final boolean dead;

        Lookup(boolean matches, boolean dead) {
            this.matches = matches;
            this.dead = dead;
        }
    }
}
