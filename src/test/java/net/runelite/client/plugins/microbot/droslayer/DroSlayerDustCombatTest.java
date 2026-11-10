package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerDustCombatTest {
    @Test public void wildernessNeverFallsBackToRegularBurstProfileOrDefault() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        InventorySetup regular = mock(InventorySetup.class); when(regular.getName()).thenReturn("slayerdefault");
        InventorySetup burst = mock(InventorySetup.class); when(burst.getName()).thenReturn("burst");
        when(config.defaultInventorySetup()).thenReturn(regular);
        DroSlayerScript script = script(config);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Dust devils");
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(regular, burst)));
            assertNull(call(script,"getActiveInventorySetup"));
            when(config.wildernessBurstDustDevils()).thenReturn(true);
            assertNull(call(script,"getActiveInventorySetup"));
            set(script,"dustBurstFallback",true);
            assertNull(call(script,"getActiveInventorySetup"));
            verify(config, never()).defaultInventorySetup();
        }
    }
    @Test public void jellyBurstIsIndependentAndOffNeverSwapsBooks() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessBurstDustDevils()).thenReturn(true);
        InventorySetup wild = mock(InventorySetup.class); when(wild.getName()).thenReturn("wild");
        InventorySetup burst = mock(InventorySetup.class); when(burst.getName()).thenReturn("burstwildy");
        when(config.wildernessInventorySetup()).thenReturn(wild);
        DroSlayerScript script = script(config);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class);
             var magic = mockStatic(Rs2Magic.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Jellies");
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild, burst)));
            assertSame(wild, call(script, "getActiveInventorySetup"));
            assertEquals(false, call(script, "wildernessBurstSelected"));
            assertEquals(false, call(script, "needsSpellbookSwap"));
            call(script, "setupAutoCastIfNeeded");
            magic.verifyNoInteractions();
            when(config.wildernessBurstJellies()).thenReturn(true);
            assertSame(burst, call(script, "getActiveInventorySetup"));
            assertEquals(true, call(script, "needsSpellbookSwap"));
            magic.when(() -> Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)).thenReturn(true);
            assertEquals(false, call(script, "needsSpellbookSwap"));
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild)));
            assertSame(wild, call(script, "getActiveInventorySetup"));
            assertEquals(false, call(script, "needsSpellbookSwap"));
        }
    }

    @Test public void bloodveldsNeverBurstAndAviansiesRequireRangeSetup() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessBurstDustDevils()).thenReturn(true);
        when(config.wildernessBurstJellies()).thenReturn(true);
        InventorySetup wild = mock(InventorySetup.class); when(wild.getName()).thenReturn("wild");
        InventorySetup ranged = mock(InventorySetup.class); when(ranged.getName()).thenReturn("rangewildy");
        when(config.wildernessInventorySetup()).thenReturn(wild);
        DroSlayerScript script = script(config);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class);
             var magic = mockStatic(Rs2Magic.class)) {
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild, ranged)));
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Bloodveld");
            assertSame(wild, call(script, "getActiveInventorySetup"));
            assertEquals(false, call(script, "shouldUseAoeCombat"));
            assertEquals(false, call(script, "needsSpellbookSwap"));
            call(script, "setupAutoCastIfNeeded");
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Aviansies");
            assertSame(ranged, call(script, "getActiveInventorySetup"));
            assertEquals(false, call(script, "shouldUseAoeCombat"));
            assertEquals(false, call(script, "needsSpellbookSwap"));
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild)));
            assertNull(call(script, "getActiveInventorySetup"));
            magic.verifyNoInteractions();
        }
    }

    @Test public void aviansieLoadoutRejectsMeleeAndAcceptsRange() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        DroSlayerScript script = script(config);
        set(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Aviansies"));
        Rs2ItemModel weapon = mock(Rs2ItemModel.class);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var equipment = mockStatic(Rs2Equipment.class);
             var magic = mockStatic(Rs2Magic.class);
             var plugin = mockStatic(DroSlayerPlugin.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Aviansies");
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(weapon);
            when(weapon.getName()).thenReturn("Viggora's chainmace");
            assertEquals(false, call(script, "hasWildernessCombatLoadout"));
            when(weapon.getName()).thenReturn("Ancient staff");
            assertEquals(false, call(script, "hasWildernessCombatLoadout"));
            when(weapon.getName()).thenReturn("Craw's bow");
            assertEquals(true, call(script, "hasWildernessCombatLoadout"));
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object call(DroSlayerScript script, String name) throws Exception {
        Method method = DroSlayerScript.class.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(script);
    }
    private static DroSlayerScript script(DroSlayerConfig config) throws Exception {
        DroSlayerScript script = new DroSlayerScript(); set(script, "config", config);
        DroSlayerTaskProfileJson profile = new DroSlayerTaskProfileJson();
        profile.setStyle("burst"); profile.setSetup("burst"); set(script, "activeJsonProfile", profile);
        return script;
    }
    @Test public void wildernessBurstIsOptInAndMeleeNeverEntersAutocastLoop() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        DroSlayerScript script = script(config);
        Rs2ItemModel weapon = mock(Rs2ItemModel.class);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var equipment = mockStatic(Rs2Equipment.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Dust devils");
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(weapon);
            when(weapon.getName()).thenReturn("Ancient staff");
            assertEquals(false, call(script, "shouldUseAoeCombat"));
            when(config.wildernessBurstDustDevils()).thenReturn(true);
            assertEquals(true, call(script, "shouldUseAoeCombat"));
            when(weapon.getName()).thenReturn("Viggora's chainmace");
            assertEquals(false, call(script, "shouldUseAoeCombat"));
            when(weapon.getName()).thenReturn("Craw's bow");
            assertEquals(false, call(script, "shouldUseAoeCombat"));
        }
    }
    @Test public void regularBurstProfilesStillWorkAndWrongBookIsHeldWithoutAutocastClicks() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
        DroSlayerScript script = script(config);
        Rs2ItemModel weapon = mock(Rs2ItemModel.class); when(weapon.getName()).thenReturn("Ancient staff");
        try (var equipment = mockStatic(Rs2Equipment.class);
             var magic = mockStatic(Rs2Magic.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(weapon);
            assertEquals(true, call(script, "shouldUseAoeCombat"));
            call(script, "handleAoeCombat");
            call(script, "handleAoeCombat");
            runtime.verifyNoInteractions();
            magic.when(() -> Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)).thenReturn(true);
            assertEquals(true, call(script, "ancientAutocastReady"));
        }
    }
    @Test public void selectedDustBurstSetupOverridesWildernessSetupAndRequiresAncient() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        InventorySetup wild = mock(InventorySetup.class); when(wild.getName()).thenReturn("wild");
        InventorySetup burst = mock(InventorySetup.class); when(burst.getName()).thenReturn("burstwildy");
        when(config.wildernessInventorySetup()).thenReturn(wild);
        DroSlayerScript script = script(config);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Dust devils");
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild, burst)));
            assertSame(wild, call(script, "getActiveInventorySetup"));
            when(config.wildernessBurstDustDevils()).thenReturn(true);
            assertSame(burst, call(script, "getActiveInventorySetup"));
            assertEquals(Rs2Spellbook.ANCIENT, call(script, "getRequiredSpellbook"));
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(wild)));
            assertEquals(false, call(script, "needsSpellbookSwap"));
            assertSame(wild, call(script, "getActiveInventorySetup"));
            assertEquals(false, call(script, "wildernessBurstSelected"));
        }
    }
    @Test public void entTransitionClearsOldDisabledDustCannonTrip() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessPreferCannon()).thenReturn(true);
        DroSlayerScript script = script(config);
        set(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Dust devils"));
        Field field = DroSlayerScript.class.getDeclaredField("cannonPolicy"); field.setAccessible(true);
        DroSlayerCannonPolicy policy = (DroSlayerCannonPolicy) field.get(script);
        Method cannon = DroSlayerScript.class.getDeclaredMethod("shouldUseCannon", String.class); cannon.setAccessible(true);
        assertEquals(true, cannon.invoke(script, "Dust devils"));
        policy.disable();
        assertEquals(false, cannon.invoke(script, "Dust devils"));
        assertEquals(false, cannon.invoke(script, "Ents"));
        assertEquals(true, cannon.invoke(script, "Dust devils"));
    }
    @Test public void selectedBurstRequiresAncientEvenWhenSavedSetupHasNoBookRequirement() throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessBurstDustDevils()).thenReturn(true);
        DroSlayerScript script = script(config);
        InventorySetup burst = mock(InventorySetup.class);
        when(burst.getName()).thenReturn("burstwildy"); when(burst.getSpellBook()).thenReturn(4);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class);
             var magic = mockStatic(Rs2Magic.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Dust devils");
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(Arrays.asList(burst)));
            assertEquals(true, call(script, "needsSpellbookSwap"));
            magic.when(() -> Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)).thenReturn(true);
            assertEquals(false, call(script, "needsSpellbookSwap"));
        }
    }
    @Test @org.junit.jupiter.api.Timeout(value = 3000, unit = java.util.concurrent.TimeUnit.MILLISECONDS) public void failedSwapReloadsNormalSetupWithoutReenablingBurstOrChangingCannon() throws Exception {
        verifySwapFallback(true);
    }
    @Test @org.junit.jupiter.api.Timeout(value = 3000, unit = java.util.concurrent.TimeUnit.MILLISECONDS) public void absentNormalSetupHoldsInsteadOfSilentlyKeepingBurstGear() throws Exception {
        verifySwapFallback(false);
    }
    private void verifySwapFallback(boolean normalAvailable) throws Exception {
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessBurstDustDevils()).thenReturn(true);
        InventorySetup normal = mock(InventorySetup.class); when(normal.getName()).thenReturn("normal");
        when(config.wildernessInventorySetup()).thenReturn(normal);
        InventorySetup burst = mock(InventorySetup.class); when(burst.getName()).thenReturn("burstwildy");
        DroSlayerScript script = script(config);
        set(script, "dustBurstSwapStarted", System.currentTimeMillis() - 31000L);
        set(script, "isUsingCannon", true);
        set(script, "initialSetupDone", true);
        set(script, "baseBankLoadComplete", true);
        var cache = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache.class);
        var query = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var setups = mockStatic(MInventorySetupsPlugin.class);
             var magic = mockStatic(Rs2Magic.class);
             var dro = mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             var runtime = mockStatic(DroSlayerRuntime.class);
             var plugin = mockStatic(DroSlayerPlugin.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Dust devils");
            dro.when(net.runelite.client.plugins.microbot.Microbot::getRs2TileObjectCache).thenReturn(cache);
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(new ArrayList<>(normalAvailable
                    ? Arrays.asList(burst, normal) : Arrays.asList(burst)));
            call(script, "handleSwappingSpellbookState");
            assertEquals(false, call(script, "wildernessBurstSelected"));
            assertEquals(false, call(script, "needsSpellbookSwap"));
            assertSame(normalAvailable ? normal : null, call(script, "getActiveInventorySetup"));
            assertTrue(config.wildernessBurstDustDevils());
            for (String flag : new String[]{"initialSetupDone", "baseBankLoadComplete"}) {
                Field field = DroSlayerScript.class.getDeclaredField(flag); field.setAccessible(true);
                assertFalse((boolean) field.get(script));
            }
            Field cannon = DroSlayerScript.class.getDeclaredField("isUsingCannon"); cannon.setAccessible(true);
            assertTrue((boolean) cannon.get(script));
            plugin.verify(() -> DroSlayerPlugin.setState(normalAvailable ? DroSlayerState.BANKING : DroSlayerState.IDLE));
        }
    }
}
