package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DroSlayerWildernessIntegrationTest {
    @Test public void southernRevenantRotationCountsOnlyOurGoblinKills() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        var assignment = DroSlayerWildernessTasks.forAssignedTask("Revenants");
        field(script, "wildernessAssignment", assignment);
        field(script, "taskDestination", assignment.getDestination());
        var client = mock(net.runelite.api.Client.class);
        var player = mock(net.runelite.api.Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getWorld()).thenReturn(337);
        var goblin = mock(net.runelite.api.NPC.class);
        when(goblin.getName()).thenReturn("Revenant goblin");
        when(goblin.getWorldLocation()).thenReturn(assignment.getDestination());
        when(goblin.getIndex()).thenReturn(1);
        DroSlayerPlugin.setState(DroSlayerState.FIGHTING);
        Field rotationField = DroSlayerScript.class.getDeclaredField("revenantRotation");
        rotationField.setAccessible(true);
        var rotation = (DroSlayerEntRotation) rotationField.get(script);
        try (var dro = mockStatic(Microbot.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            script.onEntDeath(goblin);
            assertFalse(rotation.pending(337));
            when(goblin.getInteracting()).thenReturn(player);
            script.onEntDeath(goblin);
            assertFalse(rotation.readyToHop(337, 2, true));
            assertTrue(rotation.readyToHop(337, 2, false));
            when(goblin.getIndex()).thenReturn(2);
            script.onEntDeath(goblin);
            assertTrue(rotation.readyToHop(337, 2, true));
        }
    }

    @Test public void preferredWildernessSpiderDestinationMatchesVerifiedCannonTile() {
        var assignment = DroSlayerWildernessTasks.forAssignedTask("Spiders");
        var preferred = assignment.getLocations().get(0);
        assertEquals(new net.runelite.api.coords.WorldPoint(3169,3886,0), preferred.getDestination());
        assertEquals(preferred.getDestination(), preferred.getCannonSpot());
    }

    @Test public void blackKnightsRemainNonCannonEvenWithProfileSelected() throws Exception {
        var assignment = DroSlayerWildernessTasks.forAssignedTask("Black knights");
        assertFalse(assignment.isCannonAllowed());
        assertTrue(assignment.getLocations().stream().noneMatch(DroSlayerWildernessTasks.Location::isCannonAllowed));
        for (DroSlayerMaster master : new DroSlayerMaster[]{DroSlayerMaster.KRYSTILIA, DroSlayerMaster.DURADEL}) {
            DroSlayerScript script = script(master);
            field(script,"wildernessAssignment",assignment);
            var profile = new DroSlayerTaskProfileJson(); profile.setCannon(true);
            field(script,"activeJsonProfile",profile);
            Method method = DroSlayerScript.class.getDeclaredMethod("shouldUseCannon",String.class);
            method.setAccessible(true);
            assertEquals(false,method.invoke(script,"Black knights"));
        }
    }
    @Test public void magicAxesUseVerifiedTileAndOnlyNeedKnifeWithoutSlashWeapon() throws Exception {
        var assignment = DroSlayerWildernessTasks.forAssignedTask("Magic axes");
        assertEquals(new net.runelite.api.coords.WorldPoint(3191,3960,0), assignment.getLocations().get(0).getDestination());
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        field(script,"wildernessAssignment",assignment);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var webs = mockStatic(net.runelite.client.plugins.microbot.droslayer.walker.WebTravel.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Magic axes");
            for (boolean slash : new boolean[]{false,true}) {
                webs.when(net.runelite.client.plugins.microbot.droslayer.walker.WebTravel::hasSlashWeapon).thenReturn(slash);
                @SuppressWarnings("unchecked") List<DroSlayerTaskRequirements.Requirement> requirements =
                    (List<DroSlayerTaskRequirements.Requirement>) call(script,"currentTaskRequirements");
                assertEquals(slash ? 0 : 1, requirements.stream().filter(r -> "Knife".equals(r.getPreferredName())).count());
                assertEquals(1, requirements.stream().filter(r -> "Lockpick".equals(r.getPreferredName())).count());
            }
        }
    }
    @Test public void carriedLockpickAlreadySatisfiesSinglePiratesRequirement() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        field(script,"wildernessAssignment",DroSlayerWildernessTasks.forAssignedTask("Pirates"));
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var inventory = mockStatic(Rs2Inventory.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Pirates");
            @SuppressWarnings("unchecked") List<DroSlayerTaskRequirements.Requirement> requirements =
                    (List<DroSlayerTaskRequirements.Requirement>) call(script,"currentTaskRequirements");
            var lockpicks = requirements.stream().filter(item ->
                    Arrays.asList(item.getAcceptedNames()).contains("Lockpick")).collect(Collectors.toList());
            assertEquals(1,lockpicks.size());
            assertEquals(1,lockpicks.get(0).getQuantity());
            inventory.when(() -> Rs2Inventory.itemQuantity("Lockpick",false)).thenReturn(1);
            Method satisfied = DroSlayerScript.class.getDeclaredMethod("isTaskRequirementSatisfied",
                    DroSlayerTaskRequirements.Requirement.class);
            satisfied.setAccessible(true);
            assertEquals(true,satisfied.invoke(script,lockpicks.get(0)));
        }
    }
    @Test public void cannonRestockReachesBankingInsteadOfDisablingOrBlockingOnRefill() throws Exception {
        for (DroSlayerMaster master : new DroSlayerMaster[]{DroSlayerMaster.KRYSTILIA, DroSlayerMaster.DURADEL}) {
            DroSlayerScript script = script(master);
            Field configField = DroSlayerScript.class.getDeclaredField("config");
            configField.setAccessible(true);
            DroSlayerConfig config = (DroSlayerConfig) configField.get(script);
            when(config.enableCannon()).thenReturn(true);
            when(config.wildernessPreferCannon()).thenReturn(true);
            when(config.bankForCannonballs()).thenReturn(true);
            when(config.enableAutoBanking()).thenReturn(true);
            when(config.cannonballThreshold()).thenReturn(100);
            field(script, "isUsingCannon", true);
            field(script, "cannonPlaced", true);
            try (var inventory = mockStatic(Rs2Inventory.class);
                 var cannon = mockStatic(DroCannon.class);
                 var dro = mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
                 var slayer = mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class)) {
                slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(20);
                assertEquals(true, call(script, "needsBanking"));
                assertEquals(false, call(script, "checkAndDisableCannonIfLow"));
                assertEquals(false, call(script, "handleCannonMaintenance"));
                assertEquals(true, call(script, "shouldRestockCannonballs"));
                when(config.bankForCannonballs()).thenReturn(false);
                assertEquals(false, call(script, "shouldRestockCannonballs"));
            }
        }
    }
    @Test public void entProfileCannotOverrideNoCannonAndAllEntLocationsAreExcluded() throws Exception {
        var assignment = DroSlayerWildernessTasks.forAssignedTask("Ents");
        assertFalse(assignment.isCannonAllowed());
        assertTrue(assignment.getLocations().stream().noneMatch(DroSlayerWildernessTasks.Location::isCannonAllowed));
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        field(script, "wildernessAssignment", assignment);
        DroSlayerTaskProfileJson profile = new DroSlayerTaskProfileJson();
        profile.setCannon(true);
        field(script, "activeJsonProfile", profile);
        Field configField = DroSlayerScript.class.getDeclaredField("config");
        configField.setAccessible(true);
        DroSlayerConfig config = (DroSlayerConfig) configField.get(script);
        when(config.enableCannon()).thenReturn(true);
        when(config.wildernessPreferCannon()).thenReturn(true);
        // Even a permissive assignment/profile cannot enable an Ent cannon.
        field(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Pirates"));
        Method method = DroSlayerScript.class.getDeclaredMethod("shouldUseCannon", String.class);
        method.setAccessible(true);
        assertEquals(false, method.invoke(script, "Ents"));
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static Object call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target);
    }
    private static DroSlayerScript script(DroSlayerMaster master) throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(master); field(script, "config", config);
        return script;
    }
    @Test public void spiritualTargetsRespectRealSlayerLevelInTheMainScript() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        field(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Spiritual creatures"));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(63);
            assertEquals(Arrays.asList("Spiritual ranger"), call(script, "getTargetMonsterNames"));
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(68);
            assertEquals(new java.util.HashSet<>(Arrays.asList("Spiritual ranger", "Spiritual warrior")),
                    new java.util.HashSet<>((List<String>) call(script, "getTargetMonsterNames")));
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(83);
            assertEquals(3, ((List<?>) call(script, "getTargetMonsterNames")).size());
        }
    }
    @Test public void wildernessTargetsOverrideOrdinaryProfileVariants() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        field(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Black dragons"));
        DroSlayerTaskProfileJson profile = new DroSlayerTaskProfileJson(); profile.setVariant("Baby black dragon");
        field(script, "activeJsonProfile", profile);
        try (MockedStatic<Rs2Player> ignored = mockStatic(Rs2Player.class)) {
            assertEquals(Arrays.asList("Black dragon"), call(script, "getTargetMonsterNames"));
        }
    }
    @Test public void ordinaryMasterKeepsSelectedVariant() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.DURADEL);
        DroSlayerTaskProfileJson profile = new DroSlayerTaskProfileJson(); profile.setVariant("Baby black dragon");
        field(script, "activeJsonProfile", profile);
        assertEquals(Arrays.asList("Baby black dragon"), call(script, "getTargetMonsterNames"));
    }
    @Test public void playerAttackPrayerTargetIsHighAndHonorsEarlierConfiguredRestore() {
        assertEquals(70, DroSlayerScript.playerAttackPrayerThreshold(99, 0));
        assertEquals(49, DroSlayerScript.playerAttackPrayerThreshold(70, 15));
        assertEquals(80, DroSlayerScript.playerAttackPrayerThreshold(99, 80));
    }
    @Test public void notedEscapeJewelleryCannotSatisfyDeparture() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        Rs2ItemModel jewellery = mock(Rs2ItemModel.class);
        when(jewellery.getName()).thenReturn("Amulet of glory(6)"); when(jewellery.getQuantity()).thenReturn(1);
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.get(any(Predicate.class))).thenAnswer(invocation ->
                    ((Predicate<Rs2ItemModel>) invocation.getArgument(0)).test(jewellery) ? jewellery : null);
            when(jewellery.isNoted()).thenReturn(true);
            assertEquals(false, call(script, "hasBankEscapeTeleportInInventory"));
            when(jewellery.isNoted()).thenReturn(false);
            assertEquals(true, call(script, "hasBankEscapeTeleportInInventory"));
        }
    }
    @Test public void bankingCountsBlightedFoodWhileConsumptionUsesLiveArea() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        Rs2ItemModel blighted = mock(Rs2ItemModel.class), ordinary = mock(Rs2ItemModel.class);
        for (Rs2ItemModel food : Arrays.asList(blighted, ordinary)) {
            when(food.getQuantity()).thenReturn(1); when(food.isFood()).thenReturn(true);
        }
        when(blighted.getName()).thenReturn("Blighted manta ray"); when(ordinary.getName()).thenReturn("Shark");
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class)) {
            inventory.when(() -> Rs2Inventory.getList(any(Predicate.class))).thenAnswer(invocation ->
                    Arrays.asList(blighted, ordinary).stream().filter((Predicate<Rs2ItemModel>) invocation.getArgument(0)).collect(Collectors.toList()));
            assertEquals(2, ((List<?>) call(script, "taskFood")).size());
            assertEquals(Arrays.asList(ordinary), call(script, "usableFood"));
            pvp.when(Rs2Pvp::isInWilderness).thenReturn(true);
            assertEquals(2, ((List<?>) call(script, "usableFood")).size());
        }
    }
    @Test public void optionalGloveOverlayDoesNotMutateSavedSetup() {
        var saved = mock(net.runelite.client.plugins.microbot.inventorysetups.InventorySetup.class);
        var glove = mock(net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem.class);
        var original = new java.util.ArrayList<net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem>();
        original.add(glove); when(saved.getEquipment()).thenReturn(original);
        var copied = DroSlayerLoadout.copyForFoodOverlay(saved);
        copied.getEquipment().set(0, net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem.getDummyItem());
        assertSame(glove, saved.getEquipment().get(0));
        assertNotSame(saved.getEquipment(), copied.getEquipment());
    }
    @Test public void chargedWeaponOverlayUsesPreparedFamilyWithoutChangingSavedOrOrdinarySetup() throws Exception {
        var saved = mock(net.runelite.client.plugins.microbot.inventorysetups.InventorySetup.class);
        var uncharged = new net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem(
                22542, "Viggora's chainmace (u)", 1, false,
                net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID.None, false, 3);
        var equipment = new java.util.ArrayList<net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem>(
                java.util.Collections.nCopies(14, net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem.getDummyItem()));
        int slot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
        equipment.set(slot, uncharged); when(saved.getEquipment()).thenReturn(equipment);
        var thread = mock(net.runelite.client.callback.ClientThread.class);
        var client = mock(net.runelite.api.Client.class);
        var definition = mock(net.runelite.api.ItemComposition.class);
        when(definition.getName()).thenReturn("Viggora's chainmace");
        when(client.getItemDefinition(22545)).thenReturn(definition);
        when(thread.runOnClientThreadOptional(any(java.util.concurrent.Callable.class))).thenAnswer(invocation ->
                java.util.Optional.ofNullable(((java.util.concurrent.Callable<?>) invocation.getArgument(0)).call()));
        Method copy = DroSlayerScript.class.getDeclaredMethod("withoutSetupFood",
                net.runelite.client.plugins.microbot.inventorysetups.InventorySetup.class);
        copy.setAccessible(true);
        try (MockedStatic<Microbot> dro = mockStatic(Microbot.class)) {
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(Microbot::getClient).thenReturn(client);
            var wild = (net.runelite.client.plugins.microbot.inventorysetups.InventorySetup)
                    copy.invoke(script(DroSlayerMaster.KRYSTILIA), saved);
            assertEquals(22545, wild.getEquipment().get(slot).getId());
            assertEquals(22542, saved.getEquipment().get(slot).getId());
            var ordinary = (net.runelite.client.plugins.microbot.inventorysetups.InventorySetup)
                    copy.invoke(script(DroSlayerMaster.DURADEL), saved);
            assertEquals(22542, ordinary.getEquipment().get(slot).getId());
        }
    }
    @Test public void chargePromptOwnsBankingBeforeGenericDialogueDismissal() throws Exception {
        DroSlayerScript script = script(DroSlayerMaster.KRYSTILIA);
        var charges = mock(DroSlayerWildernessWeaponCharge.class);
        when(charges.isBusy()).thenReturn(true);
        when(charges.ensureAtBank(any(DroSlayerConfig.class), anyInt(), anyInt()))
                .thenReturn(DroSlayerWildernessWeaponCharge.Result.WORKING);
        field(script, "wildernessWeaponCharge", charges);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             MockedStatic<net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue> dialogue =
                    mockStatic(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.class);
             MockedStatic<net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer> task =
                    mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class)) {
            call(script, "handleBankingState");
            dialogue.verifyNoInteractions();
            verify(charges).ensureAtBank(any(DroSlayerConfig.class), anyInt(), anyInt());
        }
    }
    @Test public void wildernessDoesNotSwitchAwayFromItsOwnSuperiorOrAttackAnotherTasksSuperior() {
        assertTrue(DroSlayerScript.allowsRetaliationTarget(true, "Choke devil", Arrays.asList("Dust devil")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Choke devil", Arrays.asList("Greater Nechryael")));
        assertTrue(DroSlayerScript.eligibleWildernessSuperior("Nechryarch", Arrays.asList("Greater Nechryael")));
        assertFalse(DroSlayerScript.eligibleWildernessSuperior("Marble gargoyle", Arrays.asList("Dust devil")));
    }
    public static final class Lease implements DroSlayerWalkerBridge.Lease {
        boolean live = true, combat; int closes;
        public boolean isActive() { return live; }
        public void setCombatActive(boolean active) { combat = active; }
        public void setLogoutWhenOutOfCombat(boolean enabled) { }
        public void close() { if (live) { live = false; closes++; } }
    }
    public static final class FakeWalkerEngine implements DroSlayerWalkerBridge.Engine {
        int acquisitions; Lease lease;
        public boolean available() { return true; }
        public net.runelite.api.coords.WorldPoint resolveDestination(net.runelite.api.coords.WorldPoint target) { return target; }
        public boolean walkTo(net.runelite.api.coords.WorldPoint target) { return true; }
        public String status() { return "IDLE"; }
        public String planningFailure() { return "NONE"; }
        public void cancel() { }
        public Lease acquireWildernessContext(String owner) { acquisitions++; return lease = new Lease(); }
        public boolean isWildernessPlayerAttackActive() { return true; }
        public String wildernessEscapeState() { return "IDLE"; }
        public void acknowledgeWildernessEscape() { }
    }
    @Test public void bridgeReacquiresContextAfterWalkerRestart() throws Exception {
        FakeWalkerEngine engine = new FakeWalkerEngine();
        DroSlayerWalkerBridge bridge = new DroSlayerWalkerBridge(engine);
        try (MockedStatic<Microbot> dro = mockStatic(Microbot.class)) {
            bridge.setWildernessContext(true, true); assertTrue(engine.lease.combat);
            assertTrue(bridge.isWildernessPlayerAttackActive());
            Lease old = engine.lease; old.live = false;
            bridge.setWildernessContext(true, false);
            assertEquals(2, engine.acquisitions); assertNotSame(old, engine.lease); assertFalse(engine.lease.combat);
        }
    }
    @Test public void disablingContextReleasesOnlyItsOwnedLease() throws Exception {
        FakeWalkerEngine engine = new FakeWalkerEngine();
        DroSlayerWalkerBridge bridge = new DroSlayerWalkerBridge(engine);
        try (MockedStatic<Microbot> dro = mockStatic(Microbot.class)) {
            bridge.setWildernessContext(true, true); Lease lease = engine.lease;
            bridge.setWildernessContext(false, false); bridge.releaseWildernessContext();
            assertEquals(1, lease.closes); assertFalse(bridge.isWildernessPlayerAttackActive());
            assertEquals("IDLE", bridge.wildernessEscapeState());
        }
    }
}
