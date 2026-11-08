package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AlchItemSlotTest {

    private static final List<String> NAMES = List.of("emerald ring");

    private boolean previousNaturalMouse;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Global> global;

    @BeforeEach
    void setUp() {
        previousNaturalMouse = Rs2AntibanSettings.naturalMouse;
        Rs2AntibanSettings.naturalMouse = true;
        inventory = mockStatic(Rs2Inventory.class);
        player = mockStatic(Rs2Player.class);
        global = mockStatic(Global.class);
        player.when(() -> Rs2Player.getSkillRequirement(Skill.MAGIC, 55)).thenReturn(false);
        global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        global.close();
        player.close();
        inventory.close();
        Rs2AntibanSettings.naturalMouse = previousNaturalMouse;
    }

    private static Rs2ItemModel item(String name, int slot) {
        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getName()).thenReturn(name);
        when(item.getSlot()).thenReturn(slot);
        return item;
    }

    private void firstMatch(Rs2ItemModel item) {
        inventory.when(() -> Rs2Inventory.hasItem("emerald ring")).thenReturn(item != null);
        inventory.when(() -> Rs2Inventory.get("emerald ring")).thenReturn(item);
    }

    @Test
    void usesIdenticalAlchItemAlreadyInTargetSlotWithoutMoving() {
        Rs2ItemModel first = item("Emerald ring", 0);
        Rs2ItemModel inSlot = item("Emerald ring", 4);
        firstMatch(first);
        inventory.when(() -> Rs2Inventory.getItemInSlot(4)).thenReturn(inSlot);

        assertSame(inSlot, AlchItemSlot.resolve(NAMES));
        inventory.verify(() -> Rs2Inventory.moveItemToSlot(any(), anyInt()), never());
    }

    @Test
    void usesHighAlchSlotAtFiftyFiveMagic() {
        player.when(() -> Rs2Player.getSkillRequirement(Skill.MAGIC, 55)).thenReturn(true);
        Rs2ItemModel first = item("Emerald ring", 0);
        Rs2ItemModel inSlot = item("Emerald ring", 12);
        firstMatch(first);
        inventory.when(() -> Rs2Inventory.getItemInSlot(12)).thenReturn(inSlot);

        assertSame(inSlot, AlchItemSlot.resolve(NAMES));
        inventory.verify(() -> Rs2Inventory.moveItemToSlot(any(), anyInt()), never());
    }

    @Test
    void movesAlchItemIntoTargetSlotAndUsesItAfterTheMove() {
        Rs2ItemModel first = item("Emerald ring", 0);
        Rs2ItemModel other = item("Nature rune", 4);
        Rs2ItemModel moved = item("Emerald ring", 4);
        firstMatch(first);
        inventory.when(() -> Rs2Inventory.getItemInSlot(4)).thenReturn(other, moved);
        inventory.when(() -> Rs2Inventory.moveItemToSlot(first, 4)).thenReturn(true);

        assertSame(moved, AlchItemSlot.resolve(NAMES));
        inventory.verify(() -> Rs2Inventory.moveItemToSlot(first, 4));
    }

    @Test
    void fallsBackToFirstAlchItemWhenTheMoveHasNoEffect() {
        Rs2ItemModel first = item("Emerald ring", 0);
        Rs2ItemModel other = item("Nature rune", 4);
        firstMatch(first);
        inventory.when(() -> Rs2Inventory.getItemInSlot(4)).thenReturn(other);
        inventory.when(() -> Rs2Inventory.moveItemToSlot(first, 4)).thenReturn(true);
        global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenReturn(false);

        assertSame(first, AlchItemSlot.resolve(NAMES));
    }

    @Test
    void fallsBackToFirstAlchItemWhenTheMoveIsRejected() {
        Rs2ItemModel first = item("Emerald ring", 0);
        firstMatch(first);
        inventory.when(() -> Rs2Inventory.moveItemToSlot(first, 4)).thenReturn(false);

        assertSame(first, AlchItemSlot.resolve(NAMES));
    }

    @Test
    void skipsSlotHandlingWithoutNaturalMouse() {
        Rs2AntibanSettings.naturalMouse = false;
        Rs2ItemModel first = item("Emerald ring", 0);
        firstMatch(first);

        assertSame(first, AlchItemSlot.resolve(NAMES));
        inventory.verify(() -> Rs2Inventory.getItemInSlot(anyInt()), never());
        inventory.verify(() -> Rs2Inventory.moveItemToSlot(any(), anyInt()), never());
    }

    @Test
    void returnsNullWhenNoAlchItemIsInTheInventory() {
        firstMatch(null);

        assertNull(AlchItemSlot.resolve(NAMES));
    }
}
