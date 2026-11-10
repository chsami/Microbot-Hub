package net.runelite.client.plugins.microbot.droslayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.slayer.Task;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerWildernessTasksTest
{
    private static final Set<Task> ORDINARY = EnumSet.of(
        Task.ABYSSAL_DEMONS, Task.ANKOU, Task.AVIANSIES, Task.BANDITS, Task.BEARS,
        Task.BLACK_DEMONS, Task.BLACK_DRAGONS, Task.BLACK_KNIGHTS, Task.BLOODVELD,
        Task.CHAOS_DRUIDS, Task.DARK_WARRIORS, Task.DUST_DEVILS, Task.EARTH_WARRIORS,
        Task.ENTS, Task.FIRE_GIANTS, Task.GREATER_DEMONS, Task.GREEN_DRAGONS,
        Task.HELLHOUNDS, Task.HILL_GIANTS, Task.ICE_GIANTS, Task.ICE_WARRIORS,
        Task.JELLIES, Task.LAVA_DRAGONS, Task.LESSER_DEMONS, Task.MAGIC_AXES,
        Task.MAMMOTHS, Task.MOSS_GIANTS, Task.NECHRYAEL, Task.PIRATES, Task.REVENANTS,
        Task.ROGUES, Task.SCORPIONS, Task.SKELETONS, Task.SPIDERS,
        Task.SPIRITUAL_CREATURES, Task.ZOMBIES);

    private static final Set<Task> BOSSES = EnumSet.of(
        Task.CALLISTO, Task.CHAOS_ELEMENTAL, Task.CHAOS_FANATIC,
        Task.CRAZY_ARCHAEOLOGIST, Task.SCORPIA, Task.VENENATIS, Task.VETION);

    @Test
    public void coversEveryKrystiliaAssignmentWithAnOrdinaryDestination()
    {
        assertEquals(36, DroSlayerWildernessTasks.all().size());
        Set<Task> supported = DroSlayerWildernessTasks.all().stream()
            .filter(DroSlayerWildernessTasks.Assignment::isSupported)
            .map(DroSlayerWildernessTasks.Assignment::getTask).collect(Collectors.toSet());
        assertEquals(ORDINARY, supported);
        for (Task task : ORDINARY)
        {
            DroSlayerWildernessTasks.Assignment assignment = DroSlayerWildernessTasks.forAssignedTask(task.getName());
            assertNotNull(assignment, task.getName());
            assertNotNull(assignment.getDestination(), task.getName());
            assertFalse(assignment.getTargetNames().isEmpty(), task.getName());
            assertFalse(assignment.isVerified(), task.getName());
        }
    }

    @Test
    public void bossAssignmentsAreOutsideTheOrdinaryCatalog()
    {
        assertTrue(DroSlayerWildernessTasks.all().stream().allMatch(DroSlayerWildernessTasks.Assignment::isSupported));
        for (Task task : BOSSES)
        {
            assertNull(DroSlayerWildernessTasks.forAssignedTask(task.getName()), task.getName());
        }
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Bosses"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Boss"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Wilderness bosses"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Artio"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Spindel"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Calvar'ion"));
    }

    @Test
    public void handlesTaskNameVariantsWithoutAdmittingNonWildernessTasks()
    {
        assertSame(assignment("Aviansies"), assignment(" aviansie "));
        assertSame(assignment("Jellies"), assignment("Jelly"));
        assertSame(assignment("Bloodveld"), assignment("Bloodvelds"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Crazy archaeologist"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Vet’ion"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("The Chaos Fanatic"));
        assertSame(assignment("Spiritual creatures"), assignment("Spiritual   creature"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("Suqahs"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask("King Black Dragon"));
        assertNull(DroSlayerWildernessTasks.forAssignedTask(null));
    }

    @Test
    public void everyCatalogCoordinateMatchesItsCitedSource()
    {
        InputStream source = getClass().getResourceAsStream("/npc/npcs_locations.json");
        assertNotNull(source, "Shipped NPC location data must be on the test classpath");
        JsonObject npcData = new JsonParser().parse(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
        for (DroSlayerWildernessTasks.Assignment assignment : DroSlayerWildernessTasks.all())
        {
            for (DroSlayerWildernessTasks.Location location : assignment.getLocations())
            {
                assertFalse(location.isVerified(), location.getName());
                if (location.getDestination() == null)
                {
                    assertFalse(location.getNotes().isEmpty(), location.getName());
                    continue;
                }
                if(location.getEvidence().equals("User-verified cannon tile from screenshot, 2026-10-11: 3044,10318,0")){
                    assertEquals(new WorldPoint(3044,10318,0),location.getDestination());assertEquals(location.getDestination(),location.getCannonSpot());continue;
                }
                if (location.getEvidence().startsWith("User-observed God Wars combat tile:"))
                {
                    assertEquals(location.getName().endsWith("Bloodveld")
                            ? new WorldPoint(3043, 10139, 0) : new WorldPoint(3030, 10158, 0), location.getDestination());
                    assertTrue(location.getName().contains("Wilderness God Wars"));
                    continue;
                }
                if ("Wilderness Edgeville Dungeon - Chaos druids".equals(location.getName()))
                {
                    assertEquals("User-verified cannon tile: 2026-10-05 screenshot", location.getEvidence());
                    assertEquals(new WorldPoint(3111, 9933, 0), location.getDestination());
                    assertEquals(location.getDestination(), location.getCannonSpot());
                    continue;
                }
                if ("Magic axe hut".equals(location.getName()))
                {
                    assertEquals("User-verified Magic axe hut tile 2026-10-05", location.getEvidence());
                    assertEquals(new WorldPoint(3191, 3960, 0), location.getDestination());
                    continue;
                }
                if ("Giant spiders north-west of Lava Dragon Isle".equals(location.getName()))
                {
                    assertEquals("User-verified spider cannon tile 2026-10-05; RuneLite CannonSpots.SPIDER", location.getEvidence());
                    assertEquals(new WorldPoint(3169, 3886, 0), location.getDestination());
                    continue;
                }
                if ("Pirates' Hideout".equals(location.getName()))
                {
                    assertEquals("Adjacent walk tile to npc/npcs_locations.json: Pirate / Pirates' Hideout (3041,3953,0)", location.getEvidence());
                    assertEquals(new WorldPoint(3041, 3954, 0), location.getDestination());
                    JsonArray pirateRows = npcData.getAsJsonArray("Pirate");
                    assertNotNull(pirateRows);
                    boolean adjacentSpawn = false;
                    for (JsonElement row : pirateRows)
                    {
                        JsonObject object = row.getAsJsonObject();
                        if (!"Pirates' Hideout".equals(object.get("location_name").getAsString())) continue;
                        for (JsonElement value : object.getAsJsonArray("coords"))
                        {
                            JsonArray coordinate = value.getAsJsonArray();
                            adjacentSpawn |= coordinate.get(0).getAsInt() == 3041
                                && coordinate.get(1).getAsInt() == 3953 && coordinate.get(2).getAsInt() == 0;
                        }
                    }
                    assertTrue(adjacentSpawn, "Adjacent original pirate spawn must still exist");
                    continue;
                }
                String[] citation = location.getEvidence().substring("npc/npcs_locations.json: ".length()).split(" / ", 2);
                JsonArray rows = npcData.getAsJsonArray(citation[0]);
                assertNotNull(rows, location.getEvidence());
                boolean found = false;
                for (JsonElement row : rows)
                {
                    JsonObject object = row.getAsJsonObject();
                    if (!object.get("location_name").getAsString().equals(citation[1])) continue;
                    for (JsonElement value : object.getAsJsonArray("coords"))
                    {
                        JsonArray coordinate = value.getAsJsonArray();
                        WorldPoint spawn = new WorldPoint(coordinate.get(0).getAsInt(),
                            coordinate.get(1).getAsInt(), coordinate.get(2).getAsInt());
                        found |= spawn.equals(location.getDestination());
                    }
                }
                assertTrue(found, location.getName() + " has an invented or stale coordinate");
            }
        }
    }

    @Test
    public void greenDragonAnchorStaysInOneSpawnCluster()
    {
        WorldPoint green = assignment("Green dragons").getDestination();
        assertTrue(green.getY() < 10090, "Preferred southern cluster must not average north and south across a wall");
        assertTrue(assignment("Green dragons").getLocations().stream()
            .anyMatch(location -> location.getDestination() != null && location.getDestination().getY() > 10100
                && location.getName().startsWith("Wilderness Slayer Cave")));
    }

    @Test
    public void cannonPolicyHonorsDungeonRestrictions()
    {
        assertEquals(new WorldPoint(3363, 10120, 0), assignment("Black demons").getCannonSpot());
        String[] caveTasks = {"Abyssal demons", "Ankou", "Black demons", "Black dragons", "Dust devils",
            "Greater demons", "Green dragons", "Hellhounds", "Ice giants", "Jellies", "Lesser demons", "Nechryael"};
        for (String task : caveTasks)
        {
            assertTrue(assignment(task).isCannonAllowed(), task);
            assertTrue(assignment(task).getLocationName().startsWith("Wilderness Slayer Cave"), task);
        }
        for (String task : new String[]{"Aviansies", "Bloodveld", "Spiritual creatures", "Revenants"})
            assertFalse(assignment(task).isCannonAllowed(), task);
        assertTrue(assignment("Fire giants").isCannonAllowed(), "Single combat alone does not forbid a cannon");
    }

    @Test public void lavaDragonsUseNormalCombatWithoutCannonPreparation()
    {
        assertFalse(assignment("Lava dragons").isCannonAllowed());
        assertNull(assignment("Lava dragons").getCannonSpot());
        assertTrue(assignment("Lava dragons").getRequirements().contains(
            DroSlayerWildernessTasks.Requirement.DRAGONFIRE_PROTECTION));
    }

    @Test
    public void requiredProtectionAndAccessItemsAreExplicit()
    {
        assertTrue(assignment("Dust devils").getRequirements().contains(DroSlayerWildernessTasks.Requirement.DUST_PROTECTION));
        for (String task : new String[]{"Black dragons", "Green dragons", "Lava dragons"})
            assertTrue(assignment(task).getRequirements().contains(DroSlayerWildernessTasks.Requirement.DRAGONFIRE_PROTECTION), task);
        assertTrue(assignment("Magic axes").getRequirements().contains(DroSlayerWildernessTasks.Requirement.LOCKPICK));
        assertTrue(assignment("Pirates").getRequirements().contains(DroSlayerWildernessTasks.Requirement.LOCKPICK));
        assertTrue(assignment("Revenants").getRequirements().contains(DroSlayerWildernessTasks.Requirement.REVENANT_ENTRY_FEE));
        assertTrue(assignment("Aviansies").getRequirements().contains(DroSlayerWildernessTasks.Requirement.RANGED_OR_MAGIC));
    }

    @Test
    public void ordinaryPreferredTargetsAvoidBossesAndWrongDragonVariants()
    {
        assertFalse(assignment("Black dragons").getTargetNames().contains("King Black Dragon"));
        assertFalse(assignment("Black dragons").getTargetNames().contains("Lava dragon"));
        assertFalse(assignment("Bears").getTargetNames().contains("Callisto"));
        assertFalse(assignment("Spiders").getTargetNames().contains("Venenatis"));
        assertFalse(assignment("Scorpions").getTargetNames().contains("Scorpia"));
        assertFalse(assignment("Nechryael").getTargetNames().contains("Death spawn"));
        assertTrue(assignment("Nechryael").getTargetNames().contains("Greater Nechryael"));
    }

    @Test
    public void preferredAnchorsAndCannonSpotsAreInsideActualWilderness()
    {
        for (DroSlayerWildernessTasks.Assignment entry : DroSlayerWildernessTasks.all())
        {
            if (!entry.isSupported()) continue;
            assertTrue(Rs2Pvp.getWildernessLevelFrom(entry.getDestination()) > 0, entry.getTaskName());
            if (entry.getCannonSpot() != null)
                assertTrue(Rs2Pvp.getWildernessLevelFrom(entry.getCannonSpot()) > 0, entry.getTaskName());
        }
        assertEquals(new WorldPoint(3043, 10139, 0), assignment("Bloodveld").getDestination());
        assertEquals(new WorldPoint(3030, 10158, 0), assignment("Spiritual creatures").getDestination());
    }

    @Test
    public void cannonRoutesUseExactSelectedOrNativeTilesWithoutJumpingToAnotherCluster() throws Exception
    {
        Class<?> source = Class.forName("net.runelite.client.plugins.cannon.CannonSpots");
        Method getter = source.getDeclaredMethod("getCannonSpots");
        getter.setAccessible(true);
        List<?> nativeSpots = (List<?>) getter.invoke(null);
        for (DroSlayerWildernessTasks.Assignment entry : DroSlayerWildernessTasks.all())
        {
            WorldPoint spot = entry.getCannonSpot();
            if (spot == null) continue;
            assertTrue(entry.isCannonAllowed(), entry.getTaskName());
            if (entry.getTask() == Task.BLACK_DEMONS)
                assertEquals(new WorldPoint(3363, 10120, 0), spot);
            else if (entry.getTask() == Task.CHAOS_DRUIDS)
                assertEquals(new WorldPoint(3111, 9933, 0), spot);
            else if (entry.getTask() == Task.JELLIES)
                assertEquals(new WorldPoint(3430, 10103, 0), spot);
            else if (entry.getTask() == Task.ROGUES)
                assertEquals(new WorldPoint(3287, 3944, 0), spot);
            else assertTrue(nativeSpots.contains(spot), entry.getTaskName());
            assertEquals(entry.getDestination().getPlane(), spot.getPlane(), entry.getTaskName());
            assertTrue(entry.getDestination().distanceTo(spot) <= 15, entry.getTaskName());
        }
        assertEquals(new WorldPoint(3360, 10077, 0), assignment("Ankou").getCannonSpot());
        assertEquals(new WorldPoint(3362, 10156, 0), assignment("Black dragons").getCannonSpot());
        assertEquals(new WorldPoint(3427, 10149, 0), assignment("Greater demons").getCannonSpot());
        assertNull(assignment("Green dragons").getCannonSpot(), "Native northern green spot cannot move the southern task to a different room");
        assertNull(assignment("Revenants").getCannonSpot(), "No cannon in Revenant Caves");
        assertNull(assignment("Hellhounds").getCannonSpot(), "Historical Revenant-area hellhound spot is not the new Slayer Cave room");
    }

    private static DroSlayerWildernessTasks.Assignment assignment(String name)
    {
        DroSlayerWildernessTasks.Assignment assignment = DroSlayerWildernessTasks.forAssignedTask(name);
        assertNotNull(assignment, name);
        return assignment;
    }
}
