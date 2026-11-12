package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerRewardsTest {
    @Test public void cancellationAcceptsSkipWordingButNeverBlockOrUnrelatedPurchases() {
        assertTrue(DroSlayerRewards.matchesConfirmation(false,
                "Are you sure you want to spend 30 points to <col=ff0000>skip</col> your current task?"));
        assertTrue(DroSlayerRewards.matchesConfirmation(false, "Cancelling this assignment costs 30 points."));
        assertFalse(DroSlayerRewards.matchesConfirmation(false, "Block this assignment for 100 points?"));
        assertFalse(DroSlayerRewards.matchesConfirmation(false, "Buy this reward for 30 points?"));
        assertFalse(DroSlayerRewards.matchesConfirmation(true, "Skip this assignment?"));
    }

    @Test public void cancelAndBlockUseTaskControlsThenMatchingConfirmationButton() {
        for (boolean block : new boolean[]{false, true}) {
            Client client = mock(Client.class);
            ClientThread thread = mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                    Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            Widget tabs = mock(Widget.class), tasks = button(), upper = mock(Widget.class);
            Widget action = button(), confirmation = mock(Widget.class), confirmButton = button(), info = mock(Widget.class);
            when(client.getWidget(InterfaceID.SlayerRewards.TABS)).thenReturn(tabs);
            when(client.getWidget(InterfaceID.SlayerRewards.TASKS_CONTENT_UPPER)).thenReturn(upper);
            when(upper.isHidden()).thenReturn(true);
            try (var bot = mockStatic(Microbot.class); var widgets = mockStatic(Rs2Widget.class);
                 var runtime = mockStatic(DroSlayerRuntime.class)) {
                bot.when(Microbot::getClient).thenReturn(client);
                bot.when(Microbot::getClientThread).thenReturn(thread);
                runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(BooleanSupplier.class)))
                        .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
                widgets.when(() -> Rs2Widget.findWidget("Tasks", Collections.singletonList(tabs), true)).thenReturn(tasks);
                widgets.when(() -> Rs2Widget.findWidget(block ? "Block" : "Cancel", Collections.singletonList(upper), true)).thenReturn(action);
                widgets.when(() -> Rs2Widget.clickWidget(any(Widget.class))).thenReturn(true);
                DroSlayerRewards rewards = new DroSlayerRewards();
                assertTrue(rewards.clickNext(block, "Hill Giants"));
                widgets.verify(() -> Rs2Widget.clickWidget(tasks));
                when(upper.isHidden()).thenReturn(false);
                assertTrue(rewards.clickNext(block, "Hill Giants"));
                widgets.verify(() -> Rs2Widget.clickWidget(action));
                when(client.getWidget(InterfaceID.SlayerRewards.CONFIRM)).thenReturn(confirmation);
                when(client.getWidget(InterfaceID.SlayerRewards.CONFIRM_INFO)).thenReturn(info);
                when(client.getWidget(InterfaceID.SlayerRewards.CONFIRM_BUTTON)).thenReturn(confirmButton);
                when(info.getText()).thenReturn("Would you like to " + (block ? "cancel" : "block") + " this task?");
                assertFalse(rewards.clickNext(block, "Hill Giants"));
                widgets.verify(() -> Rs2Widget.clickWidget(confirmButton), never());
                when(info.getText()).thenReturn("Would you like to <col=ff0000>" + (block ? "block" : "cancel") + "</col> this task?");
                assertFalse(rewards.clickNext(block, "Bears")); // The assignment changed.
                assertTrue(rewards.clickNext(block, "Hill Giants"));
                widgets.verify(() -> Rs2Widget.clickWidget(confirmButton));
                if (!block) {
                    // The info container may hold its prompt in a visible child.
                    Widget prompt = mock(Widget.class);
                    when(info.getText()).thenReturn("");
                    when(info.getDynamicChildren()).thenReturn(new Widget[]{prompt});
                    when(prompt.getText()).thenReturn("Skip your current task for 30 points?");
                    assertTrue(rewards.clickNext(false, "Hill Giants"));
                    widgets.verify(() -> Rs2Widget.clickWidget(confirmButton), times(2));
                }
                rewards.reset();
                assertFalse(rewards.clickNext(block, "Hill Giants")); // No unowned confirmation after reset.
                widgets.verify(() -> Rs2Widget.clickWidget(confirmation), never());
            }
        }
    }

    @Test public void failedTaskClickDoesNotAuthorizeConfirmationAndEmptyBoundsAreIgnored() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        Widget upper = mock(Widget.class), action = button(), confirmation = mock(Widget.class), info = mock(Widget.class);
        when(client.getWidget(InterfaceID.SlayerRewards.TASKS_CONTENT_UPPER)).thenReturn(upper);
        try (var bot = mockStatic(Microbot.class); var widgets = mockStatic(Rs2Widget.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bot.when(Microbot::getClient).thenReturn(client);
            bot.when(Microbot::getClientThread).thenReturn(thread);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            widgets.when(() -> Rs2Widget.findWidget("Cancel", Collections.singletonList(upper), true)).thenReturn(action);
            DroSlayerRewards rewards = new DroSlayerRewards();
            when(action.getBounds()).thenReturn(new Rectangle());
            assertFalse(rewards.clickNext(false, "Hill Giants"));
            widgets.verify(() -> Rs2Widget.clickWidget(action), never());
            when(action.getBounds()).thenReturn(new Rectangle(10, 10, 30, 20));
            assertFalse(rewards.clickNext(false, "Hill Giants"));
            when(client.getWidget(InterfaceID.SlayerRewards.CONFIRM)).thenReturn(confirmation);
            when(client.getWidget(InterfaceID.SlayerRewards.CONFIRM_INFO)).thenReturn(info);
            when(info.getText()).thenReturn("Cancel task?");
            assertFalse(rewards.clickNext(false, "Hill Giants"));
        }
    }

    @Test public void completedBlockExitsEvenWhileRewardsMenuRemainsOpen() throws Exception {
        try (var slayer = mockStatic(Rs2Slayer.class); var widgets = mockStatic(Rs2Widget.class);
             var dialogue = mockStatic(Rs2Dialogue.class)) {
            widgets.when(() -> Rs2Widget.isWidgetVisible(426, 0)).thenReturn(true);
            DroSlayerScript script = new DroSlayerScript();
            Method method = DroSlayerScript.class.getDeclaredMethod("handleBlockingTaskState");
            method.setAccessible(true);
            method.invoke(script);
            assertEquals(DroSlayerState.GETTING_TASK, DroSlayerPlugin.getState());
            widgets.verifyNoInteractions(); // Completion is checked before attempting another block.
        }
    }

    private static Widget button() {
        Widget widget = mock(Widget.class);
        when(widget.getBounds()).thenReturn(new Rectangle(10, 10, 30, 20));
        return widget;
    }
}
