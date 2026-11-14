package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerDragonProtectionTest {
    private static final String SPIDER_LOCATION = "Giant spiders north-west of Lava Dragon Isle";

    @Test void knownNonDragonTasksDoNotInheritNearbyDragonLandmarks() {
        for (String task : List.of("Spiders", "Bears", "Skeletons", "Hellhounds", "Magic axes")) {
            assertFalse(DroSlayerTaskRequirements.requiresDragonfireProtection(task, SPIDER_LOCATION), task);
            assertFalse(DroSlayerBankSupplies.needsAntifire(DroSlayerTaskRequirements.forTask(task, SPIDER_LOCATION)), task);
        }
        assertTrue(DroSlayerTaskRequirements.forTask("Spiders", SPIDER_LOCATION).stream()
                .noneMatch(row -> row.getLabel().equals("dragonfire shield protection")));
    }

    @Test void realDragonsKeepProtectionAndCustomDragonRoomsStillHaveFallback() {
        for (String task : List.of("Green dragons", "Lava dragons", "Black dragons", "Adamant dragons", "Rune dragons", "Drakes")) {
            assertTrue(DroSlayerTaskRequirements.requiresDragonfireProtection(task, ""), task);
            assertTrue(DroSlayerBankSupplies.needsAntifire(DroSlayerTaskRequirements.forTask(task, "")), task);
        }
        assertTrue(DroSlayerTaskRequirements.requiresDragonfireProtection("Custom monster", "Lithkren vault"));
        assertTrue(DroSlayerTaskRequirements.requiresDragonfireProtection("Custom monster", "Metal dragon room"));
    }

    @Test void babiesAndWyvernsRetainTheirDistinctProtectionRules() {
        for (String task : List.of("Baby dragons", "Skeletal wyverns", "Fossil Island wyverns", "Wyrms", "Hydras"))
            assertFalse(DroSlayerTaskRequirements.requiresDragonfireProtection(task, "Dragon cave"), task);
        assertTrue(DroSlayerTaskRequirements.forTask("Skeletal wyverns", "").stream()
                .anyMatch(row -> row.isEquipped() && row.getLabel().equals("wyvern breath protection")));
    }

    @Test void anExplicitProfileAntifireRequestIsStillRespected() {
        assertTrue(DroSlayerBankSupplies.needsAntifire(DroSlayerTaskRequirements.forTask("Spiders", SPIDER_LOCATION, false, false, true)));
    }

    @Test void obsoleteAntifireRowsAreRemovedOnlyFromTheRuntimeCopy() {
        InventorySetup saved = saved();
        saved.getInventory().add(row(2452,"Antifire(4)"));
        saved.getInventory().add(row(12697,"Super combat potion(3)"));
        saved.getAdditionalFilteredItems().put(21978,row(21978,"Extended super antifire(3)"));
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved);
        DroSlayerLoadout.removeUnneededAntifire(copy,DroSlayerTaskRequirements.forTask("Spiders",SPIDER_LOCATION));
        assertTrue(InventorySetupsItem.itemIsDummy(copy.getInventory().get(0)));
        assertEquals(12697,copy.getInventory().get(1).getId());
        assertTrue(copy.getAdditionalFilteredItems().isEmpty());
        assertEquals(2452,saved.getInventory().get(0).getId());
        assertEquals(1,saved.getAdditionalFilteredItems().size());
        assertFalse(DroSlayerBankSupplies.keep("Extended antifire(3)",true,false,null,
                DroSlayerTaskRequirements.forTask("Spiders",SPIDER_LOCATION)));
    }

    @Test void dragonLoadoutsRetainAllSupportedAntifireDoses() {
        var requirements = DroSlayerTaskRequirements.forTask("Green dragons", "");
        for (String family : List.of("Antifire", "Extended antifire", "Super antifire", "Extended super antifire"))
            for (int dose=1;dose<=4;dose++) {
                String name=family+"("+dose+")";
                assertTrue(DroSlayerBankSupplies.isAntifire(name),name);
                InventorySetup copy=saved();copy.getInventory().add(row(1,name));
                DroSlayerLoadout.removeUnneededAntifire(copy,requirements);
                assertEquals(1,copy.getInventory().get(0).getId());
            }
        assertFalse(DroSlayerBankSupplies.isAntifire("Dragonfire shield"));
        assertFalse(DroSlayerBankSupplies.isAntifire("Super combat potion(4)"));
    }

    @Test void departureWaitsUntilAntifireDepositIsActuallyObserved() throws Exception {
        var script = new DroSlayerScript();
        Method bank = DroSlayerScript.class.getDeclaredMethod("bankUnneededAntifire",List.class);bank.setAccessible(true);
        Rs2ItemModel potion=mock(Rs2ItemModel.class);when(potion.getId()).thenReturn(2452);when(potion.getName()).thenReturn("Antifire(4)");
        AtomicReference<Rs2ItemModel> carried=new AtomicReference<>(potion);
        try (var inventory=mockStatic(Rs2Inventory.class);var banking=mockStatic(Rs2Bank.class);var runtime=mockStatic(DroSlayerRuntime.class)) {
            inventory.when(() -> Rs2Inventory.get(any(Predicate.class))).thenAnswer(call -> {
                Rs2ItemModel item=carried.get();Predicate<Rs2ItemModel> predicate=call.getArgument(0);
                return item!=null && predicate.test(item)?item:null;
            });
            runtime.when(() -> DroSlayerRuntime.performAction(any(),eq(false),any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier)call.getArgument(2)).getAsBoolean());
            var spider= DroSlayerTaskRequirements.forTask("Spiders",SPIDER_LOCATION);
            banking.when(() -> Rs2Bank.depositAll(2452)).thenReturn(false);
            assertEquals(false,bank.invoke(script,spider));
            banking.when(() -> Rs2Bank.depositAll(2452)).thenReturn(true);
            assertEquals(false,bank.invoke(script,spider));
            carried.set(null);
            assertEquals(true,bank.invoke(script,spider));
            banking.verify(() -> Rs2Bank.depositAll(2452),times(2));
            carried.set(potion);
            assertEquals(true,bank.invoke(script,DroSlayerTaskRequirements.forTask("Green dragons","")));
            banking.verify(() -> Rs2Bank.depositAll(2452),times(2));
        }
    }

    private static InventorySetupsItem row(int id,String name) {
        return new InventorySetupsItem(id,name,1,false,InventorySetupsStackCompareID.None,false,-1);
    }
    private static InventorySetup saved() {
        InventorySetup saved=mock(InventorySetup.class);
        when(saved.getInventory()).thenReturn(new ArrayList<>());
        when(saved.getEquipment()).thenReturn(new ArrayList<>());
        when(saved.getAdditionalFilteredItems()).thenReturn(new HashMap<>());return saved;
    }
}
