package net.runelite.client.plugins.microbot.tithefarming.models;

import net.runelite.api.Client;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitheFarmPlantTest {

    private static final WorldPoint PATCH = new WorldPoint(1792 + 35, 3520 + 25, 0);
    private static final WorldPoint OTHER_PATCH = new WorldPoint(1792 + 40, 3520 + 25, 0);
    private static final AtomicInteger TICK = new AtomicInteger(1_000_000);

    @Test
    void lookupsInTheSameTickScanTheSceneOnceAndNewTicksRescan() {
        Rs2TileObjectModel first = patchModel(ObjectID.HOSIDIUS_TITHE_A_2_DRY, PATCH);
        Rs2TileObjectModel second = patchModel(ObjectID.HOSIDIUS_TITHE_EMPTY, OTHER_PATCH);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.where(any())).thenReturn(query);
        when(query.toList()).thenReturn(List.of(first, second));
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        Client client = mock(Client.class);
        int tick = TICK.incrementAndGet();
        when(client.getTickCount()).thenReturn(tick);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            microbot.when(Microbot::getClient).thenReturn(client);
            TitheFarmPlant a = new TitheFarmPlant(35, 25, 1);
            TitheFarmPlant b = new TitheFarmPlant(40, 25, 2);

            assertTrue(a.isStage1());
            assertFalse(a.isStage2());
            assertTrue(b.isEmptyPatch());
            assertEquals(second, b.getGameObject());
            verify(cache, times(1)).query();

            when(client.getTickCount()).thenReturn(TICK.incrementAndGet());
            assertTrue(a.isStage1());
            verify(cache, times(2)).query();
        }
    }

    private static Rs2TileObjectModel patchModel(int id, WorldPoint location) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.getId()).thenReturn(id);
        when(model.getWorldLocation()).thenReturn(location);
        return model;
    }

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
    void aPlantWithUnknownPlantingTimeHasNoGrowthEstimate() {
        assertNull(new TitheFarmPlant(35, 25, 1).timeUntilGrowth(2, Instant.EPOCH));
    }

    @Test
    void timeUntilGrowthCountsDownToTheNextStageOneMinuteApartFromPlanting() {
        TitheFarmPlant plant = new TitheFarmPlant(35, 25, 1);
        Instant planted = Instant.parse("2026-11-09T12:00:00Z");
        plant.setPlanted(planted);

        assertEquals(Duration.ofSeconds(50), plant.timeUntilGrowth(1, planted.plusSeconds(10)));
        assertEquals(Duration.ofSeconds(55), plant.timeUntilGrowth(2, planted.plusSeconds(65)));
        assertEquals(Duration.ofSeconds(30), plant.timeUntilGrowth(3, planted.plusSeconds(150)));
        assertTrue(plant.timeUntilGrowth(2, planted.plusSeconds(121)).isNegative());
    }

    private static void assertDead(int id) {
        Lookup lookup = lookUp(id);
        assertTrue(lookup.matches, "dead id " + id + " should be found as a tithe patch");
        assertTrue(lookup.dead, "dead id " + id + " should be reported dead");
    }

    @SuppressWarnings("unchecked")
    private static Lookup lookUp(int id) {
        Rs2TileObjectModel model = patchModel(id, PATCH);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        ArgumentCaptor<Predicate<Rs2TileObjectModel>> filter = ArgumentCaptor.forClass(Predicate.class);
        when(query.where(filter.capture())).thenReturn(query);
        when(query.toList()).thenAnswer(call -> filter.getValue().test(model) ? List.of(model) : List.of());
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        Client client = mock(Client.class);
        int tick = TICK.incrementAndGet();
        when(client.getTickCount()).thenReturn(tick);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            microbot.when(Microbot::getClient).thenReturn(client);
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
