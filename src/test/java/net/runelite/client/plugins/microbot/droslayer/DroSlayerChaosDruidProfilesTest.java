package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerProfileManager;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerChaosDruidProfilesTest {
    @Test public void addsIndependentElderEntryWithoutChangingOrdinarySettings() throws Exception {
        Path path = Files.createTempDirectory("slayer-druid-profiles").resolve("profiles.json");
        Files.writeString(path, "{\"chaos druids\":{\"prayer\":\"pmelee\",\"offensivePrayer\":\"PIETY\",\"wildernessSlaughterBracelet\":true}}");
        DroSlayerProfileManager manager = new DroSlayerProfileManager();
        set(manager,"profilePath",path);
        manager.loadProfiles();
        DroSlayerTaskProfileJson ordinary = manager.findProfile("Chaos druids");
        DroSlayerTaskProfileJson elder = manager.findProfile("elder chaos druid");
        assertNotSame(ordinary, elder);
        assertEquals(DroSlayerPrayer.PROTECT_MELEE,ordinary.getParsedPrayer());
        assertEquals(DroSlayerPrayer.PROTECT_MAGIC,elder.getParsedPrayer());
        assertTrue(elder.isWildernessSlaughterBracelet());
        assertEquals("PIETY",elder.getOffensivePrayer());
        manager.saveTaskPrayer("elder chaos druid","NONE","NONE");
        manager.loadProfiles();
        assertEquals(DroSlayerPrayer.NONE,manager.findProfile("elder chaos druid").getParsedPrayer());
        assertEquals(DroSlayerPrayer.PROTECT_MELEE,manager.findProfile("Chaos druids").getParsedPrayer());
        assertEquals("chaos druid",DroSlayerPrayerEditor.displayTaskName("chaos druids"));
        assertEquals("chaos druid elder",DroSlayerPrayerEditor.displayTaskName("elder chaos druid"));
    }

    @Test public void elderRouteHonorsItsOwnEditsIncludingNoProtectionPrayer() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.selectedProtectionPrayer()).thenReturn(DroSlayerPrayer.TASK_PROFILE);
        set(script,"config",config);
        set(script,"wildernessAssignment",DroSlayerWildernessTasks.forChaosDruidPreference(
                DroSlayerWildernessTasks.forAssignedTask("Chaos druids"),true));
        assertEquals("elder chaos druid",call(script,"currentTaskProfileName"));
        set(script,"editedTaskPrayers",Map.of("chaos druids",DroSlayerPrayer.PROTECT_MELEE,
                "elder chaos druid",DroSlayerPrayer.NONE));
        assertEquals(DroSlayerPrayer.NONE,call(script,"getActiveProtectionPrayer"));
        set(script,"editedTaskPrayers",Map.of("elder chaos druid",DroSlayerPrayer.PROTECT_RANGED));
        assertEquals(DroSlayerPrayer.PROTECT_RANGED,call(script,"getActiveProtectionPrayer"));
    }

    private static void set(Object object,String name,Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(object,value);
    }
    private static Object call(Object object,String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true); return method.invoke(object);
    }
}
