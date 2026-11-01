package net.runelite.client.plugins.microbot.blastoisefurnace;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.api.gameval.ObjectID.BLAST_FURNACE_DISPENSER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlastoiseFurnaceTakeBarsTest {

    private Object previousClient;
    private Object previousCache;
    private Rs2TileObjectQueryable query;
    private MockedStatic<Rs2Widget> widgets;
    private MockedStatic<Rs2Keyboard> keyboard;
    private MockedStatic<Rs2Inventory> inventory;
    private final AtomicBoolean promptOpen = new AtomicBoolean();
    private final AtomicInteger bars = new AtomicInteger();
    private final AtomicInteger presses = new AtomicInteger();
    private int pressesDropped;

    @BeforeEach
    void setUp() throws Exception {
        previousClient = swapMicrobotField("client", mock(Client.class));
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        previousCache = swapMicrobotField("rs2TileObjectCache", cache);

        widgets = mockStatic(Rs2Widget.class);
        widgets.when(() -> Rs2Widget.hasWidget(anyString())).thenAnswer(invocation -> promptOpen.get());
        keyboard = mockStatic(Rs2Keyboard.class);
        keyboard.when(() -> Rs2Keyboard.keyPress(KeyEvent.VK_SPACE)).thenAnswer(invocation -> {
            if (presses.incrementAndGet() > pressesDropped) {
                promptOpen.set(false);
                bars.set(27);
            }
            return null;
        });
        inventory = mockStatic(Rs2Inventory.class);
        inventory.when(() -> Rs2Inventory.count("bar")).thenAnswer(invocation -> bars.get());
    }

    @AfterEach
    void tearDown() throws Exception {
        inventory.close();
        keyboard.close();
        widgets.close();
        swapMicrobotField("client", previousClient);
        swapMicrobotField("rs2TileObjectCache", previousCache);
    }

    @Test
    void pressesSpaceAgainWhenThePromptStaysOpenAfterADroppedPress() {
        dispenserOffersTake();
        when(query.interact(BLAST_FURNACE_DISPENSER, "Take")).thenAnswer(invocation -> {
            promptOpen.set(true);
            return true;
        });
        pressesDropped = 1;

        assertTrue(script().takeBarsFromDispenser());

        assertEquals(2, presses.get());
        verify(query, times(1)).interact(BLAST_FURNACE_DISPENSER, "Take");
    }

    @Test
    void confirmsAPromptThatIsAlreadyOpenWithoutClickingTheDispenser() {
        promptOpen.set(true);

        assertTrue(script().takeBarsFromDispenser());

        assertEquals(1, presses.get());
        verify(query, never()).interact(BLAST_FURNACE_DISPENSER, "Take");
    }

    @Test
    void reportsFailureWhenTheTakeClickIsRefused() {
        dispenserOffersTake();
        when(query.interact(BLAST_FURNACE_DISPENSER, "Take")).thenReturn(false);

        assertFalse(script().takeBarsFromDispenser());

        assertEquals(0, presses.get());
        verify(query, times(3)).interact(BLAST_FURNACE_DISPENSER, "Take");
    }

    @Test
    void clicksTheDispenserOnceItOffersTake() {
        AtomicInteger checks = new AtomicInteger();
        when(query.first()).thenAnswer(invocation -> checks.incrementAndGet() > 2 ? mock(Rs2TileObjectModel.class) : null);
        when(query.interact(BLAST_FURNACE_DISPENSER, "Take")).thenAnswer(invocation -> {
            promptOpen.set(true);
            return true;
        });

        assertTrue(script().takeBarsFromDispenser());

        assertEquals(1, presses.get());
        verify(query, atLeastOnce()).withAction("Take");
    }

    private void dispenserOffersTake() {
        when(query.first()).thenReturn(mock(Rs2TileObjectModel.class));
    }

    private static BlastoiseFurnaceScript script() {
        return new BlastoiseFurnaceScript(null, null);
    }

    private static Object swapMicrobotField(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
