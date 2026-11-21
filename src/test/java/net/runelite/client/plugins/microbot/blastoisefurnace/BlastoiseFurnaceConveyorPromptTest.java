package net.runelite.client.plugins.microbot.blastoisefurnace;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static net.runelite.api.gameval.ObjectID.BLAST_FURNACE_CONVEYER_BELT_CLICKABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlastoiseFurnaceConveyorPromptTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Dialogue> dialogue;
    private MockedStatic<Rs2Widget> widget;
    private MockedStatic<Global> global;
    private Rs2TileObjectQueryable query;
    private final AtomicInteger ore = new AtomicInteger(27);
    private final AtomicBoolean promptOpen = new AtomicBoolean();
    private final AtomicInteger answers = new AtomicInteger();
    private final List<Widget> options = new ArrayList<>();
    private boolean beltPrompts = true;
    private boolean beltIgnoresClick;
    private boolean answerDeposits = true;
    private boolean clickFails;
    private int pressedOption;
    private String question = "Put the ore on the belt?";

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        player = mockStatic(Rs2Player.class);
        dialogue = mockStatic(Rs2Dialogue.class);
        widget = mockStatic(Rs2Widget.class);
        global = mockStatic(Global.class);

        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);

        when(query.interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")).thenAnswer(invocation -> {
            if (beltIgnoresClick) {
                return true;
            }
            if (beltPrompts) {
                promptOpen.set(true);
            } else {
                ore.set(0);
            }
            return true;
        });
        inventory.when(() -> Rs2Inventory.count("ore")).thenAnswer(invocation -> ore.get());
        dialogue.when(Rs2Dialogue::isInDialogue).thenAnswer(invocation -> promptOpen.get());
        dialogue.when(Rs2Dialogue::hasSelectAnOption).thenAnswer(invocation -> promptOpen.get());
        dialogue.when(Rs2Dialogue::getQuestion).thenAnswer(invocation -> question);
        dialogue.when(Rs2Dialogue::getDialogueOptions).thenReturn(options);
        dialogue.when(() -> Rs2Dialogue.keyPressForDialogueOption(anyInt())).thenAnswer(invocation -> {
            if (clickFails) {
                return false;
            }
            pressedOption = invocation.getArgument(0);
            answers.incrementAndGet();
            promptOpen.set(false);
            if (answerDeposits) {
                ore.set(0);
            }
            return true;
        });
        global.when(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), any(BooleanSupplier.class), anyInt(), anyInt()))
                .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(0)).getAsBoolean());
        global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(0)).getAsBoolean());
    }

    @AfterEach
    void tearDown() {
        global.close();
        widget.close();
        dialogue.close();
        player.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void answersTheOptionPromptAndReportsTheDeposit() {
        options.add(option("Yes"));
        options.add(option("No"));

        assertTrue(script().depositOre());

        assertEquals(1, answers.get());
        assertEquals(0, ore.get());
        verify(query, times(1)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void leavesAPromptWithoutAYesOptionUnanswered() {
        options.add(option("Deposit"));
        options.add(option("Cancel"));

        assertFalse(script().depositOre());

        assertEquals(0, answers.get());
        assertEquals(27, ore.get());
        assertEquals("Belt asked a question the script can't answer, retrying", Microbot.status);
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void logsTheQuestionAndTheFullTextOfTheOptionItClicked() {
        options.add(option("No"));
        options.add(option("<col=ff0000>Yes, and don't ask again</col>"));

        assertTrue(script().depositOre());

        microbot.verify(() -> Microbot.log("The conveyor belt asked \"Put the ore on the belt?\". Chose \"Yes, and don't ask again\"."), times(1));
        assertEquals(2, pressedOption);
    }

    @Test
    void saysTheClickFailedWhenAYesOptionCouldNotBeClicked() {
        options.add(option("Yes"));
        options.add(option("No"));
        clickFails = true;
        BlastoiseFurnaceScript script = script();

        assertFalse(script.depositOre());
        assertEquals("Couldn't click the belt prompt answer, retrying", Microbot.status);

        for (int trip = 1; trip < BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            assertFalse(script.depositOre());
        }
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        microbot.verify(() -> Microbot.showMessage(message.capture()), times(1));
        assertEquals("Ore did not go onto the conveyor belt 3 trips in a row.\n"
                + "The belt asked \"Put the ore on the belt?\" (Yes, No) but the script could not click \"Yes\".\n"
                + "Put the ore on the belt by hand and start the script again.\n"
                + "If it stops here again, report it on the Microbot Discord and include the log.", message.getValue());
    }

    @Test
    void stopMessageDropsAnAnswerFromAnEarlierTrip() {
        options.add(option("Yes"));
        options.add(option("No"));
        answerDeposits = false;
        BlastoiseFurnaceScript script = script();
        assertFalse(script.depositOre());

        promptOpen.set(false);
        beltPrompts = false;
        beltIgnoresClick = true;
        for (int trip = 1; trip < BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            assertFalse(script.depositOre());
        }

        assertEquals("Ore did not go onto the conveyor belt 3 trips in a row. Put the ore on the belt by hand, then start the script again.",
                BlastoiseFurnaceScript.beltStopMessage(false, null, null, null, null));
        microbot.verify(() -> Microbot.showMessage(
                "Ore did not go onto the conveyor belt 3 trips in a row. Put the ore on the belt by hand, then start the script again."), times(1));
    }

    @Test
    void keepsThePlainRetryStatusWhenNoPromptWasSeen() {
        beltPrompts = false;
        beltIgnoresClick = true;

        assertFalse(script().depositOre());

        assertEquals(27, ore.get());
        assertEquals("Ore didn't go on the belt, retrying", Microbot.status);
    }

    @Test
    void stopMessageSaysTheScriptCouldNotPickAnAnswer() {
        options.add(option("<col=ff0000>Deposit</col>"));
        options.add(option("Cancel"));

        assertEquals("Ore did not go onto the conveyor belt 3 trips in a row.\n"
                + "The belt asked \"Put the ore on the belt?\" (Deposit, Cancel) and the script does not know which answer to pick.\n"
                + "Put the ore on the belt by hand and start the script again.\n"
                + "If it stops here again, report it on the Microbot Discord and include the log.", stopMessage());
    }

    @Test
    void stopMessageSaysWhichAnswerWasChosenWhenTheOreStayed() {
        options.add(option("Yes"));
        options.add(option("No"));
        answerDeposits = false;

        assertEquals("Ore did not go onto the conveyor belt 3 trips in a row.\n"
                + "The belt asked \"Put the ore on the belt?\" and the script answered \"Yes\", but the ore stayed in your inventory.\n"
                + "Put the ore on the belt by hand and start the script again.\n"
                + "If it stops here again, report it on the Microbot Discord and include the log.", stopMessage());
        assertEquals(BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS, answers.get());
    }

    @Test
    void stopMessageLeavesOutAMissingQuestion() {
        options.add(option("Deposit"));
        options.add(option("Cancel"));
        question = null;

        String message = stopMessage();

        assertTrue(message.contains("The belt showed a prompt (Deposit, Cancel) and the script does not know which answer to pick."));
        assertFalse(message.contains("null"));
    }

    @Test
    void stopMessageKeepsThePlainTextWhenNoPromptWasSeen() {
        beltPrompts = false;
        beltIgnoresClick = true;

        assertEquals("Ore did not go onto the conveyor belt 3 trips in a row. Put the ore on the belt by hand, then start the script again.",
                stopMessage());
    }

    @Test
    void stopsWithOneMessageNamingThePromptAfterRepeatedFailedTrips() {
        options.add(option("Deposit"));
        options.add(option("Cancel"));
        BlastoiseFurnaceScript script = script();

        for (int trip = 1; trip < BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            assertFalse(script.depositOre());
        }
        microbot.verify(() -> Microbot.showMessage(anyString()), never());
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());

        assertFalse(script.depositOre());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        microbot.verify(() -> Microbot.showMessage(message.capture()), times(1));
        assertTrue(message.getValue().contains("Put the ore on the belt?"));
        assertTrue(message.getValue().contains("Deposit"));
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), times(1));
        player.verify(Rs2Player::logout, times(1));
        assertEquals("Ore didn't go on the belt, stopped", Microbot.status);
    }

    @Test
    void forgetsFailedTripsAfterASuccessfulDeposit() {
        options.add(option("Deposit"));
        BlastoiseFurnaceScript script = script();
        for (int trip = 1; trip < BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            assertFalse(script.depositOre());
        }

        promptOpen.set(false);
        beltPrompts = false;
        assertTrue(script.depositOre());

        ore.set(27);
        beltPrompts = true;
        assertFalse(script.depositOre());

        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void doesNotCountATripWithNoOreInTheInventory() {
        ore.set(0);
        BlastoiseFurnaceScript script = script();

        for (int trip = 0; trip <= BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            assertFalse(script.depositOre());
        }

        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    private String stopMessage() {
        BlastoiseFurnaceScript script = script();
        for (int trip = 0; trip < BlastoiseFurnaceScript.MAX_FAILED_BELT_TRIPS; trip++) {
            promptOpen.set(false);
            assertFalse(script.depositOre());
        }
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        microbot.verify(() -> Microbot.showMessage(message.capture()), times(1));
        return message.getValue();
    }

    private static Widget option(String text) {
        Widget option = mock(Widget.class);
        when(option.getText()).thenReturn(text);
        return option;
    }

    private static BlastoiseFurnaceScript script() {
        BlastoiseFurnaceConfig config = mock(BlastoiseFurnaceConfig.class);
        when(config.getBars()).thenReturn(Bars.GOLD_BAR);
        return new BlastoiseFurnaceScript(null, config);
    }
}
