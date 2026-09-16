package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporossSnapshotBoundaryTest
{
    @Test
    void executorPublishedFieldsUseSnapshotTypes() throws Exception
    {
        assertTrue(TemporossScript.class.getDeclaredField("sortedFires").getGenericType()
                .getTypeName().contains("TemporossNpcSnapshot"));
        assertTrue(TemporossScript.class.getDeclaredField("fishSpots").getGenericType()
                .getTypeName().contains("TemporossNpcSnapshot"));
        assertTrue(TemporossScript.class.getDeclaredField("sortedClouds").getGenericType()
                .getTypeName().contains("TemporossCloudSnapshot"));
        assertTrue(TemporossScript.class.getDeclaredField("temporossPool").getType()
                .equals(TemporossNpcSnapshot.class));
        assertTrue(TemporossScript.class.getDeclaredField("lockedTether").getType()
                .equals(TemporossObjectSnapshot.class));
    }

    @Test
    void snapshotsContainOnlyFinalPlainData()
    {
        assertPlainSnapshot(TemporossNpcSnapshot.class);
        assertPlainSnapshot(TemporossCloudSnapshot.class);
        assertPlainSnapshot(TemporossObjectSnapshot.class);
    }

    @Test
    void executorOwnersRetainNoLiveEntityFields()
    {
        assertNoLiveEntityFields(TemporossScript.class);
        assertNoLiveEntityFields(TemporossWorkArea.class);
        assertNoLiveEntityFields(TemporossOverlay.class);
    }

    @Test
    void crossThreadSignalsAreVolatile() throws Exception
    {
        assertVolatile(TemporossPlugin.class, "incomingWave", "isTethered", "waves", "fireClouds");
        assertVolatile(TemporossScript.class, "ENERGY", "INTENSITY", "ESSENCE", "temporossConfig",
                "state", "workArea", "sortedFires", "sortedClouds", "fishSpots",
                "energyDrainPerTick", "lastDrainSample", "cachedInMinigame");
        assertVolatile(TemporossOverlay.class, "npcList", "fishList", "cloudList", "ammoList",
                "lastWalkPath");
    }

    private static void assertPlainSnapshot(Class<?> snapshotType)
    {
        for (Field field : snapshotType.getDeclaredFields())
        {
            assertTrue(Modifier.isFinal(field.getModifiers()), field + " must be final");
            Class<?> type = field.getType();
            assertFalse(NPC.class.isAssignableFrom(type), field + " retains an NPC");
            assertFalse(GameObject.class.isAssignableFrom(type), field + " retains a GameObject");
            assertFalse(TileObject.class.isAssignableFrom(type), field + " retains a TileObject");
            assertFalse(Rs2NpcModel.class.isAssignableFrom(type), field + " retains an NPC model");
            assertFalse(Rs2TileObjectModel.class.isAssignableFrom(type), field + " retains a tile-object model");
        }
    }

    private static void assertNoLiveEntityFields(Class<?> owner)
    {
        for (Field field : owner.getDeclaredFields())
        {
            Class<?> type = field.getType();
            assertFalse(NPC.class.isAssignableFrom(type), field + " retains an NPC");
            assertFalse(GameObject.class.isAssignableFrom(type), field + " retains a GameObject");
            assertFalse(TileObject.class.isAssignableFrom(type), field + " retains a TileObject");
            assertFalse(Rs2NpcModel.class.isAssignableFrom(type), field + " retains an NPC model");
            assertFalse(Rs2TileObjectModel.class.isAssignableFrom(type), field + " retains a tile-object model");
        }
    }

    private static void assertVolatile(Class<?> owner, String... fieldNames) throws Exception
    {
        for (String fieldName : fieldNames)
        {
            Field field = owner.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()), field + " must be volatile");
        }
    }
}
