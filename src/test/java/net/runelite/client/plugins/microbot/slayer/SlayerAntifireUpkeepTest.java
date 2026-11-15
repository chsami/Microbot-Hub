package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SlayerAntifireUpkeepTest {

    private static final int ANTIFIRE_VARBIT = 3981;
    private static final int SUPER_ANTIFIRE_VARBIT = 6101;

    private final List<Rs2ItemModel> items = new ArrayList<>();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Slayer> slayer;
    private MockedStatic<Rs2Inventory> inventory;
    private SlayerScript script;

    @BeforeEach
    void setUp() throws Exception {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        slayer = mockStatic(Rs2Slayer.class);
        inventory = mockStatic(Rs2Inventory.class);

        Client client = mock(Client.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        inventory.when(() -> Rs2Inventory.get(ArgumentMatchers.<Predicate<Rs2ItemModel>>any()))
                .thenAnswer(invocation -> {
                    Predicate<Rs2ItemModel> predicate = invocation.getArgument(0);
                    return items.stream().filter(predicate).findFirst().orElse(null);
                });
        inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);

        SlayerConfig config = mock(SlayerConfig.class);
        when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.OFF);
        script = new SlayerScript();
        setField(script, "config", config);
    }

    @AfterEach
    void tearDown() {
        inventory.close();
        slayer.close();
        player.close();
        microbot.close();
        SlayerPlugin.setState(SlayerState.IDLE);
    }

    @Test
    void dragonTaskRenewsAntifireWithoutTheProfileFlag() throws Exception {
        Rs2ItemModel antifire = item("Antifire potion(4)");
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        setField(script, "activeJsonProfile", new SlayerTaskProfileJson());

        invoke(script, "handlePotions");

        inventory.verify(() -> Rs2Inventory.interact(antifire, "Drink"), times(1));
    }

    @Test
    void dragonTaskRenewsAntifireWithoutAnyProfile() throws Exception {
        item("Antifire potion(4)");
        Rs2ItemModel extended = item("Extended super antifire(1)");
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");

        invoke(script, "handlePotions");

        inventory.verify(() -> Rs2Inventory.interact(extended, "Drink"), times(1));
    }

    @Test
    void activeProtectionIsNotRenewed() throws Exception {
        item("Antifire potion(4)");
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        microbot.when(() -> Microbot.getVarbitValue(SUPER_ANTIFIRE_VARBIT)).thenReturn(5);

        invoke(script, "handlePotions");

        inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
    }

    @Test
    void otherTasksAndBabyDragonVariantsDoNotDrinkAntifire() throws Exception {
        item("Antifire potion(4)");
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Abyssal Demons");

        invoke(script, "handlePotions");

        SlayerTaskProfileJson babies = new SlayerTaskProfileJson();
        babies.setVariant("baby blue dragon");
        setField(script, "activeJsonProfile", babies);
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Blue Dragons");

        invoke(script, "handlePotions");

        inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
    }

    @Test
    void dragonfireTaskDetection() {
        assertTrue(SlayerScript.isDragonfireTask("Black Dragons", null));
        assertTrue(SlayerScript.isDragonfireTask("Metal dragons", "rune dragon"));
        assertTrue(SlayerScript.isDragonfireTask("Blue Dragons", ""));
        assertFalse(SlayerScript.isDragonfireTask("Blue Dragons", "baby blue dragon"));
        assertFalse(SlayerScript.isDragonfireTask("Abyssal Demons", null));
        assertFalse(SlayerScript.isDragonfireTask(null, null));
    }

    @Test
    void burnMessageDetection() {
        assertTrue(SlayerPlugin.isDragonfireBurnMessage("You're horribly burnt by the dragon fire!"));
        assertTrue(SlayerPlugin.isDragonfireBurnMessage("You are horribly burnt by the dragonfire!"));
        assertFalse(SlayerPlugin.isDragonfireBurnMessage("Your super antifire potion completely protects you from the dragon fire!"));
        assertFalse(SlayerPlugin.isDragonfireBurnMessage("Your super antifire potion has expired."));
        assertFalse(SlayerPlugin.isDragonfireBurnMessage(null));
    }

    @Test
    void unprotectedBurnWithoutDosesRetreatsAndStopsWhenTheBankHasNoAntifire() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        script.onDragonfireBurnMessage();

        assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));
        assertTrue((boolean) invoke(script, "stopIfStillUnprotectedFromDragonfire"));
        microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), times(1));
        assertEquals(SlayerState.IDLE, SlayerPlugin.getState());
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));
    }

    @Test
    void retreatIsDroppedWhenTheTaskIsNoLongerADragonTask() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        script.onDragonfireBurnMessage();
        assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Abyssal Demons");

        assertFalse((boolean) invoke(script, "stopIfStillUnprotectedFromDragonfire"));
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));
        microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
    }

    @Test
    void stickyRetreatIsClearedInTheFightLoopOfAnotherTask() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        script.onDragonfireBurnMessage();
        assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Abyssal Demons");
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        setField(script, "lastDragonfireBurnAt", 0L);
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));
    }

    @Test
    void retreatContinuesAfterRestockingAntifire() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        script.onDragonfireBurnMessage();
        assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        item("Antifire potion(4)");

        assertFalse((boolean) invoke(script, "stopIfStillUnprotectedFromDragonfire"));
        microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));
    }

    @Test
    void burnWithDosesOrProtectionDoesNotRetreat() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        item("Antifire potion(1)");
        script.onDragonfireBurnMessage();
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        items.clear();
        microbot.when(() -> Microbot.getVarbitValue(ANTIFIRE_VARBIT)).thenReturn(3);
        assertFalse((boolean) invoke(script, "shouldRetreatFromDragonfire"));
    }

    @Test
    void retreatWithoutAnInventorySetupWalksToTheBankBeforeStopping() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");
        script.onDragonfireBurnMessage();
        assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));

        try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class)) {
            invoke(script, "handleBankingState");

            bank.verify(Rs2Bank::walkToBank, times(1));
            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
            microbot.verify(() -> Microbot.showMessage(anyString()), never());

            bank.when(Rs2Bank::isOpen).thenReturn(true);
            invoke(script, "handleBankingState");

            bank.verify(Rs2Bank::walkToBank, times(1));
            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), times(1));
            microbot.verify(() -> Microbot.showMessage(ArgumentMatchers.contains("no inventory setup")), times(1));
            assertEquals(SlayerState.IDLE, SlayerPlugin.getState());
        }
    }

    @Test
    void retreatStopsWhenTheBankHasNoAntifireEither() throws Exception {
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Dragons");

        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class)) {
            assertFalse((boolean) invoke(script, "stopIfBankHasNoAntifire"));

            script.onDragonfireBurnMessage();
            assertTrue((boolean) invoke(script, "shouldRetreatFromDragonfire"));

            bank.when(() -> Rs2Bank.hasItem("antifire")).thenReturn(true);
            assertFalse((boolean) invoke(script, "stopIfBankHasNoAntifire"));
            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());

            bank.when(() -> Rs2Bank.hasItem("antifire")).thenReturn(false);
            assertTrue((boolean) invoke(script, "stopIfBankHasNoAntifire"));
            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), times(1));
            microbot.verify(() -> Microbot.showMessage(ArgumentMatchers.contains("no antifire potions either")), times(1));
        }
    }

    private Rs2ItemModel item(String name) {
        Rs2ItemModel model = mock(Rs2ItemModel.class);
        when(model.getName()).thenReturn(name);
        items.add(model);
        return model;
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
