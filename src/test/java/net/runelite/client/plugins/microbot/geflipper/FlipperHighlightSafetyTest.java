package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the live highlight checks that run after movement and before a stationary click. */
class FlipperHighlightSafetyTest {
    private static final int SETUP_ID = 465 << 16 | 26;
    private static final int CONFIRM_ID = 465 << 16 | 30;

    @Test
    void theBuySlotVisualHighlightCanClickItsNativeBuyButtonInEitherSpeedMode() {
        assertAll(() -> assertBuyClick(false), () -> assertBuyClick(true));
    }

    private void assertBuyClick(boolean speed) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareBuy(1, speed);
            assertTrue(fixture.click(), "Native slot Buy, randomized speed=" + speed);
            assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
            assertEquals(1, fixture.backend.movements, "Buy must press without another movement");
            assertEquals(speed ? 1 : 0, fixture.backend.speedSamples);
            assertTrue(fixture.menuReads >= 2, "Buy must be revalidated immediately before pressing");
        }
    }

    @Test
    void everyEmptyBuySlotCanUseItsOwnButtonWithoutDependingOnTheSuggestionsBoxField() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (int slot = 0; slot < 8; slot++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareBuy(slot, speed);
                    // Copilot selects an empty slot locally; BUY's server box field
                    // does not select the visual slot, unlike abort/modify suggestions.
                    ((Suggestion) fixture.manager.suggestion).boxId = (slot + 1) % 8;
                    assertTrue(fixture.click(), "Buy slot=" + slot + ", randomized speed=" + speed);
                    assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
                }
            }
        }
    }

    @Test
    void nativeBuySlotRequiresTheExactCreationOperationAndButtonChild() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareBuy(1, false);
            fixture.menuEntry.option = "  Create  <col=ffffff>BUY</col>\u00a0offer  ";
            fixture.actionNode.actions = new String[]{"CREATE BUY OFFER", "Create Buy offer"};
            assertTrue(fixture.menuMatches(), "Native formatting must not invalidate Buy");
            fixture.menuEntry.type = MenuAction.CC_OP_LOW_PRIORITY;
            assertTrue(fixture.menuMatches());
            fixture.menuEntry.type = MenuAction.CC_OP;
            for (int operation : new int[]{-1, 0, 2, 3}) {
                fixture.menuEntry.identifier = operation;
                assertFalse(fixture.menuMatches(), "Buy must use native operation 1, received=" + operation);
            }
            fixture.menuEntry.identifier = 1;
            for (int child : new int[]{1, 2, 4}) {
                Fixture.Node unrelated = fixture.nodes.get(fixture.buySlotId(1)).child(child);
                unrelated.bounds.setBounds(fixture.actionNode.bounds);
                unrelated.actions = new String[]{"Create Buy offer"};
                fixture.menuEntry.child = child;
                assertFalse(fixture.menuMatches(), "An overlapping child cannot replace Buy child 3: " + child);
            }
            fixture.menuEntry.child = 3;
            fixture.menuEntry.parentId = fixture.buySlotId(2);
            assertFalse(fixture.menuMatches(), "Another empty slot cannot reuse this slot's highlight");
            fixture.menuEntry.parentId = fixture.buySlotId(1);
            for (MenuAction type : new MenuAction[]{MenuAction.RUNELITE, MenuAction.WIDGET_TYPE_1, MenuAction.CANCEL}) {
                fixture.menuEntry.type = type;
                assertFalse(fixture.menuMatches(), type.name());
            }
            fixture.menuEntry.type = MenuAction.CC_OP;
            fixture.menuEntry.option = "Create Sell offer";
            fixture.actionNode.actions = new String[]{"Create Sell offer"};
            assertFalse(fixture.menuMatches(), "A Buy visual cannot authorize a Sell operation");
            fixture.menuEntry.option = "Copilot graph";
            fixture.actionNode.actions = new String[]{"Copilot graph"};
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.option = "Create Buy offer";
            fixture.actionNode.actions = new String[]{null};
            assertFalse(fixture.menuMatches());
            fixture.actionNode.actions = null;
            assertFalse(fixture.menuMatches());
        }
    }

    @Test
    void nativeBuySlotRequiresItsCurrentVisibleVisualButtonAndOverview() throws Exception {
        for (int invalid = 0; invalid < 16; invalid++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, false);
                int slotId = fixture.buySlotId(1);
                Fixture.Node parent = fixture.nodes.get(slotId);
                switch (invalid) {
                    case 0: parent.hidden = true; break;
                    case 1: fixture.actionNode.hidden = true; break;
                    case 2: fixture.clickNode.hidden = true; break;
                    case 3: fixture.actionNode.bounds.translate(60, 0); break;
                    case 4: fixture.clickNode.bounds.translate(60, 0); break;
                    case 5: fixture.nodes.remove(slotId); break;
                    case 6: parent.children.remove(3); break;
                    case 7:
                    case 8:
                        Fixture.Node replacement = fixture.new Node(invalid == 7 ? slotId + 1 : slotId,
                            invalid == 8 ? 4 : 3);
                        replacement.bounds.setBounds(fixture.actionNode.bounds);
                        replacement.actions = new String[]{"Create Buy offer"};
                        parent.children.put(3, replacement);
                        break;
                    case 9: parent.child(0); break;
                    case 10: fixture.nodes.remove(InterfaceID.GeOffers.INDEX); break;
                    case 11: fixture.nodes.get(InterfaceID.GeOffers.INDEX).hidden = true; break;
                    case 12: fixture.nodes.get(SETUP_ID).hidden = false; break;
                    case 13: fixture.node(InterfaceID.GeOffers.DETAILS); break;
                    case 14:
                        fixture.clickNode = parent.child(1);
                        fixture.clickNode.bounds.setBounds(-1, -1, 46, 45);
                        break;
                    case 15: fixture.node(slotId); break;
                    default: throw new AssertionError();
                }
                assertFalse(fixture.menuMatches(), "Invalid live Buy control case=" + invalid);
            }
        }
    }

    @Test
    void nativeBuySlotRequiresKnownEmptyOfferStateInTheTargetSlot() throws Exception {
        for (GrandExchangeOfferState state : GrandExchangeOfferState.values()) {
            if (state == GrandExchangeOfferState.EMPTY) continue;
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, false);
                fixture.offers[1] = fixture.offer(state);
                assertFalse(fixture.menuMatches(), "Buy cannot target offer state=" + state);
            }
        }
        for (int missing = 0; missing < 6; missing++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, false);
                switch (missing) {
                    case 0: fixture.offers = null; break;
                    case 1: fixture.offers = new GrandExchangeOffer[0]; break;
                    case 2: fixture.offers = java.util.Arrays.copyOf(fixture.offers, 7); break;
                    case 3: fixture.offers = java.util.Arrays.copyOf(fixture.offers, 9); break;
                    case 4: fixture.offers[1] = null; break;
                    case 5: fixture.offers[1] = fixture.offer(null); break;
                    default: throw new AssertionError();
                }
                assertFalse(fixture.menuMatches(), "Unknown live offer state case=" + missing);
            }
        }
        try (Fixture fixture = new Fixture()) {
            fixture.prepareBuy(1, false);
            fixture.offers[0] = fixture.offer(GrandExchangeOfferState.BUYING);
            fixture.offers[2] = fixture.offer(GrandExchangeOfferState.SELLING);
            assertTrue(fixture.menuMatches(), "Occupied other slots do not block the highlighted empty slot");
        }
    }

    @Test
    void nativeBuySlotRequiresAFreshBuySuggestionAndCannotBuyDuringFinish() throws Exception {
        for (String type : new String[]{"sell", "modify_buy", "modify_sell", "abort", "", null}) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, false);
                ((Suggestion) fixture.manager.suggestion).type = type;
                assertFalse(fixture.menuMatches(), "Non-Buy suggestion type=" + type);
            }
        }
        for (Object suggestion : new Object[]{null, new Object()}) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, false);
                fixture.manager.suggestion = suggestion;
                assertFalse(fixture.menuMatches(), "Missing or incompatible suggestion must fail closed");
            }
        }
        for (boolean speed : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareBuy(1, speed);
                assertTrue(fixture.script.requestFinish(() -> { }));
                assertFalse(fixture.click(), "Finish must not open a new Buy offer");
                assertTrue(fixture.backend.events.isEmpty());
            }
        }
    }

    @Test
    void changingTheBuyOfferButtonOrRecommendationAfterCaptureCancelsTheCompleteClick() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (int change = 0; change < 25; change++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareBuy(1, speed);
                    final int mutation = change;
                    fixture.script.afterWait = () -> {
                        switch (mutation) {
                            case 0: fixture.offers[1] = fixture.offer(GrandExchangeOfferState.BUYING); break;
                            case 1: fixture.offers = null; break;
                            case 2: fixture.menuEntry.option = "Create Sell offer"; break;
                            case 3: fixture.actionNode.actions = new String[]{"Create Sell offer"}; break;
                            case 4: fixture.nodes.get(fixture.buySlotId(1)).child(0); break;
                            case 5: fixture.actionNode.hidden = true; break;
                            case 6: fixture.actionNode.bounds.translate(60, 0); break;
                            case 7: fixture.nodes.get(fixture.buySlotId(1)).hidden = true; break;
                            case 8: fixture.nodes.get(InterfaceID.GeOffers.INDEX).hidden = true; break;
                            case 9: fixture.nodes.get(SETUP_ID).hidden = false; break;
                            case 10: fixture.node(InterfaceID.GeOffers.DETAILS); break;
                            case 11: fixture.menuEntry.child = 4; break;
                            case 12: fixture.menuEntry.parentId = fixture.buySlotId(2); break;
                            case 13: fixture.menuEntry.identifier = 2; break;
                            case 14: fixture.menuEntry.type = MenuAction.RUNELITE; break;
                            case 15: fixture.menuEntry.itemId = 201; break;
                            case 16: fixture.mouse = new Point(2, 1); break;
                            case 17: fixture.manager.suggestion = null; break;
                            case 18: fixture.manager.suggestion = new Object(); break;
                            case 19: ((Suggestion) fixture.manager.suggestion).itemId++; break;
                            case 20: ((Suggestion) fixture.manager.suggestion).type = "sell"; break;
                            case 21: fixture.menuOpen = true; break;
                            case 22: Microbot.targetMenu = new NewMenuEntry().option("Synthetic pending action"); break;
                            case 23: fixture.controller.highlightOverlays.clear(); break;
                            case 24: fixture.script.requestFinish(() -> { }); break;
                            default: throw new AssertionError();
                        }
                    };
                    assertFalse(fixture.click(), "Buy changed, case=" + change + ", randomized speed=" + speed);
                    assertEquals(1, fixture.script.matchedWaits, "A valid Buy must be captured before the change");
                    assertTrue(fixture.backend.events.isEmpty(), "A changed Buy must not receive a press");
                    assertEquals(1, fixture.backend.movements);
                }
            }
        }
    }

    @Test
    void collectAllVisualCanClickItsNativeInventoryButtonInEitherSpeedMode() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareCollectAll(speed);
                assertTrue(fixture.click(), "Collect-all, randomized speed=" + speed);
                assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
                assertEquals(1, fixture.backend.movements, "Collect must press without moving a second time");
                assertEquals(speed ? 1 : 0, fixture.backend.speedSamples);
                assertTrue(fixture.menuReads >= 2, "Collect must revalidate immediately before pressing");
            }
        }
    }

    @Test
    void collectAllRequiresOnlyItsNativeInventoryOperationAndButtonChild() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareCollectAll(false);
            fixture.menuEntry.option = "  Collect  to <col=ffffff>INVENTORY</col>\u00a0 ";
            fixture.actionNode.actions = new String[]{"COLLECT TO INVENTORY", "Collect to bank"};
            assertTrue(fixture.menuMatches(), "Native formatting must not invalidate Collect-all");
            for (int operation : new int[]{-1, 0, 2, 3}) {
                fixture.menuEntry.identifier = operation;
                assertFalse(fixture.menuMatches(), "Collect must use inventory operation 1: " + operation);
            }
            fixture.menuEntry.identifier = 1;
            for (int child : new int[]{-1, 1, 2, 3}) {
                fixture.menuEntry.child = child;
                assertFalse(fixture.menuMatches(), "Only native Collect-all child 0 is supported: " + child);
            }
            fixture.menuEntry.child = 0;
            fixture.menuEntry.parentId = InterfaceID.GeOffers.INDEX_0;
            assertFalse(fixture.menuMatches(), "An offer slot cannot replace the Collect-all button");
            fixture.menuEntry.parentId = InterfaceID.GeOffers.COLLECTALL;
            for (MenuAction type : new MenuAction[]{MenuAction.CC_OP_LOW_PRIORITY, MenuAction.RUNELITE,
                MenuAction.WIDGET_TYPE_1, MenuAction.CANCEL}) {
                fixture.menuEntry.type = type;
                assertFalse(fixture.menuMatches(), type.name());
            }
            fixture.menuEntry.type = MenuAction.CC_OP;
            for (String option : new String[]{"Collect to bank", "Collect", "Copilot graph", "Confirm", ""}) {
                fixture.menuEntry.option = option;
                fixture.actionNode.actions = new String[]{option};
                assertFalse(fixture.menuMatches(), "Unsupported Collect action=" + option);
            }
            fixture.menuEntry.option = "Collect to inventory";
            fixture.actionNode.actions = new String[]{"Collect to bank", "Collect to inventory"};
            assertFalse(fixture.menuMatches(), "Inventory must remain native operation 1");
            fixture.actionNode.actions = new String[]{null};
            assertFalse(fixture.menuMatches());
            fixture.actionNode.actions = null;
            assertFalse(fixture.menuMatches());
        }
    }

    @Test
    void collectAllRequiresItsCurrentVisibleLabelAndButtonOnTheOverview() throws Exception {
        for (int invalid = 0; invalid < 16; invalid++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareCollectAll(false);
                Fixture.Node parent = fixture.nodes.get(InterfaceID.GeOffers.COLLECTALL);
                switch (invalid) {
                    case 0: parent.hidden = true; break;
                    case 1: fixture.actionNode.hidden = true; break;
                    case 2: fixture.clickNode.hidden = true; break;
                    case 3: fixture.actionNode.bounds.translate(100, 0); break;
                    case 4: fixture.clickNode.bounds.translate(100, 0); break;
                    case 5: fixture.nodes.remove(InterfaceID.GeOffers.COLLECTALL); break;
                    case 6: parent.children.remove(0); break;
                    case 7:
                    case 8:
                        Fixture.Node replacement = fixture.new Node(invalid == 7
                            ? InterfaceID.GeOffers.COLLECTALL + 1 : InterfaceID.GeOffers.COLLECTALL,
                            invalid == 8 ? 1 : 0);
                        replacement.bounds.setBounds(fixture.actionNode.bounds);
                        replacement.actions = new String[]{"Collect to inventory"};
                        parent.children.put(0, replacement);
                        break;
                    case 9: parent.child(2); break;
                    case 10: fixture.nodes.remove(InterfaceID.GeOffers.INDEX); break;
                    case 11: fixture.nodes.get(InterfaceID.GeOffers.INDEX).hidden = true; break;
                    case 12: fixture.nodes.get(SETUP_ID).hidden = false; break;
                    case 13: fixture.node(InterfaceID.GeOffers.DETAILS); break;
                    case 14: parent.children.remove(2); break;
                    case 15: fixture.node(InterfaceID.GeOffers.COLLECTALL); break;
                    default: throw new AssertionError();
                }
                assertFalse(fixture.menuMatches(), "Invalid live Collect-all control case=" + invalid);
            }
        }
    }

    @Test
    void changedCollectControlMenuOrRecommendationCancelsTheCompleteClick() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (int change = 0; change < 20; change++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareCollectAll(speed);
                    final int mutation = change;
                    fixture.script.afterWait = () -> {
                        switch (mutation) {
                            case 0: fixture.actionNode.hidden = true; break;
                            case 1: fixture.actionNode.bounds.translate(100, 0); break;
                            case 2: fixture.actionNode.actions = new String[]{"Collect to bank"}; break;
                            case 3: fixture.menuEntry.option = "Copilot graph"; break;
                            case 4: fixture.menuEntry.option = "Collect to bank"; break;
                            case 5: fixture.menuEntry.identifier = 2; break;
                            case 6: fixture.menuEntry.child = 2; break;
                            case 7: fixture.menuEntry.parentId = InterfaceID.GeOffers.INDEX_0; break;
                            case 8: fixture.menuEntry.type = MenuAction.RUNELITE; break;
                            case 9: fixture.nodes.get(InterfaceID.GeOffers.COLLECTALL).child(2); break;
                            case 10: fixture.nodes.get(InterfaceID.GeOffers.COLLECTALL).hidden = true; break;
                            case 11: fixture.nodes.get(InterfaceID.GeOffers.INDEX).hidden = true; break;
                            case 12: fixture.nodes.get(SETUP_ID).hidden = false; break;
                            case 13: fixture.node(InterfaceID.GeOffers.DETAILS); break;
                            case 14: fixture.mouse = new Point(2, 1); break;
                            case 15: ((Suggestion) fixture.manager.suggestion).itemId++; break;
                            case 16: fixture.manager.suggestion = null; break;
                            case 17: fixture.controller.highlightOverlays.clear(); break;
                            case 18: fixture.menuOpen = true; break;
                            case 19: Microbot.targetMenu = new NewMenuEntry().option("Synthetic pending action"); break;
                            default: throw new AssertionError();
                        }
                    };
                    assertFalse(fixture.click(), "Collect changed, case=" + change + ", randomized speed=" + speed);
                    assertEquals(1, fixture.script.matchedWaits, "A valid Collect must be captured before the change");
                    assertTrue(fixture.backend.events.isEmpty(), "A stale Collect must not receive a press");
                    assertEquals(1, fixture.backend.movements);
                }
            }
        }
    }

    @Test
    void theConfirmVisualHighlightCanClickItsNativeStaticButtonInEitherSpeedMode() {
        assertAll(() -> assertStaticConfirmClick(false), () -> assertStaticConfirmClick(true));
    }

    private void assertStaticConfirmClick(boolean speed) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareStaticConfirm(speed);
            assertTrue(fixture.click(), "Native static Confirm, randomized speed=" + speed);
            assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
            assertEquals(1, fixture.backend.movements, "Confirm must press without another movement");
            assertEquals(speed ? 1 : 0, fixture.backend.speedSamples);
            assertTrue(fixture.menuReads >= 2, "Confirm must be revalidated immediately before pressing");
        }
    }

    @Test
    void nativeStaticConfirmRequiresItsExactWidgetIndexAndIndexedNativeOperation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareStaticConfirm(false);
            fixture.actionNode.actions = new String[]{"Examine", "<col=ffffff>CONFIRM</col>"};
            fixture.menuEntry.option = "  Confirm  ";
            fixture.menuEntry.identifier = 2;
            assertTrue(fixture.menuMatches());
            fixture.menuEntry.type = MenuAction.CC_OP_LOW_PRIORITY;
            assertTrue(fixture.menuMatches());
            for (int operation : new int[]{-1, 0, 1, 3}) {
                fixture.menuEntry.identifier = operation;
                assertFalse(fixture.menuMatches(), "Invalid or different Confirm operation " + operation);
            }
            fixture.menuEntry.identifier = 2;
            fixture.menuEntry.child = 0;
            assertFalse(fixture.menuMatches(), "A static widget cannot accept a dynamic child's index");
            fixture.menuEntry.child = -1;
            fixture.menuEntry.parentId++;
            assertFalse(fixture.menuMatches(), "Another static widget is not the Confirm button");
            fixture.menuEntry.parentId--;
            for (MenuAction type : new MenuAction[]{MenuAction.RUNELITE, MenuAction.WIDGET_TYPE_1, MenuAction.CANCEL}) {
                fixture.menuEntry.type = type;
                assertFalse(fixture.menuMatches(), type.name());
            }
            fixture.menuEntry.type = MenuAction.CC_OP;
            fixture.menuEntry.option = "Copilot graph";
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.option = "Nothing";
            assertFalse(fixture.menuMatches(), "Copilot's disabled confirmation must remain disabled");
            fixture.menuEntry.option = "Confirm";
            fixture.actionNode.actions[1] = null;
            assertFalse(fixture.menuMatches());
            fixture.actionNode.actions = null;
            assertFalse(fixture.menuMatches());
        }
    }

    @Test
    void nativeStaticConfirmRequiresACurrentVisibleConfirmVisualAndOverlappingLiveButton() throws Exception {
        for (int invalid = 0; invalid < 9; invalid++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareStaticConfirm(false);
                Fixture.Node parent = fixture.nodes.get(SETUP_ID);
                switch (invalid) {
                    case 0: parent.hidden = true; break;
                    case 1: fixture.actionNode.hidden = true; break;
                    case 2: fixture.actionNode.bounds.translate(40, 0); break;
                    case 3: fixture.nodes.remove(CONFIRM_ID); break;
                    case 4:
                    case 5:
                        Fixture.Node replacement = fixture.new Node(invalid == 4 ? CONFIRM_ID + 1 : CONFIRM_ID,
                            invalid == 5 ? 0 : -1);
                        replacement.bounds.setBounds(fixture.actionNode.bounds);
                        replacement.actions = new String[]{"Confirm"};
                        fixture.nodes.put(CONFIRM_ID, replacement);
                        break;
                    case 6: parent.child(58); break;
                    case 7: fixture.nodes.remove(SETUP_ID); break;
                    case 8: fixture.clickNode = parent.child(54); break;
                    default: throw new AssertionError();
                }
                assertFalse(fixture.menuMatches(), "Invalid static Confirm control case=" + invalid);
            }
        }
    }

    @Test
    void aChangedStaticConfirmControlOrMenuAfterCaptureCancelsTheCompleteClick() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (int change = 0; change < 13; change++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareStaticConfirm(speed);
                    final int mutation = change;
                    fixture.script.afterWait = () -> {
                        switch (mutation) {
                            case 0: fixture.actionNode.hidden = true; break;
                            case 1: fixture.actionNode.bounds.translate(40, 0); break;
                            case 2: fixture.actionNode.actions = new String[]{"Enter price"}; break;
                            case 3: fixture.menuEntry.option = "Copilot graph"; break;
                            case 4: fixture.menuEntry.parentId++; break;
                            case 5: fixture.menuEntry.child = 0; break;
                            case 6: fixture.nodes.remove(CONFIRM_ID); break;
                            case 7: fixture.nodes.get(SETUP_ID).child(58); break;
                            case 8:
                                Fixture.Node movedReplacement = fixture.node(CONFIRM_ID);
                                movedReplacement.actions = new String[]{"Confirm"};
                                break;
                            case 9: fixture.nodes.get(SETUP_ID).hidden = true; break;
                            case 10: fixture.clickNode.hidden = true; break;
                            case 11: fixture.menuEntry.identifier = 0; break;
                            case 12: fixture.menuEntry.type = MenuAction.RUNELITE; break;
                            default: throw new AssertionError();
                        }
                    };
                    assertFalse(fixture.click(), "Confirm changed, case=" + change + ", randomized speed=" + speed);
                    assertEquals(1, fixture.script.matchedWaits, "A valid Confirm must be captured before the change");
                    assertTrue(fixture.backend.events.isEmpty(), "A changed Confirm must not receive a press");
                    assertEquals(1, fixture.backend.movements);
                }
            }
        }
    }

    @Test
    void finishCanConfirmAFreshSellOrModifiedSaleInEitherSpeedMode() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (String type : new String[]{"sell", "modify_sell"}) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareStaticConfirm(speed);
                    fixture.prepareFinish(type, true);
                    assertTrue(fixture.script.isFinishing());
                    assertTrue(fixture.click(), "Finish must confirm " + type + ", randomized speed=" + speed);
                    assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
                    assertEquals(1, fixture.script.matchedWaits);
                }
            }
        }
    }

    @Test
    void finishStillRejectsBuyAndStaleSellConfirmations() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (boolean fresh : new boolean[]{false, true}) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareStaticConfirm(speed);
                    fixture.prepareFinish(fresh ? "buy" : "sell", fresh);
                    assertFalse(fixture.click(), fresh ? "Finish must reject buying" : "Finish needs a fresh sell suggestion");
                    assertEquals(0, fixture.script.matchedWaits);
                    assertTrue(fixture.backend.events.isEmpty());
                }
            }
        }
    }

    @Test
    void priceAndQuantityVisualHighlightsCanClickTheirLiveActionSiblingsInEitherSpeedMode() throws Exception {
        int[][] controls = {{54, 12}, {51, 7}};
        String[] options = {"Enter price", "Enter quantity"};
        for (boolean speed : new boolean[]{false, true}) {
            for (int index = 0; index < controls.length; index++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareClick(controls[index][0], controls[index][1], options[index], speed);
                    assertTrue(fixture.click(), options[index] + ", randomized speed=" + speed);
                    assertEquals(java.util.Arrays.asList("press", "release", "click"), fixture.backend.events);
                    assertEquals(1, fixture.backend.movements, "Capture must be followed by a stationary press");
                    assertEquals(speed ? 1 : 0, fixture.backend.speedSamples);
                    assertTrue(fixture.menuReads >= 2, "The menu must be read again immediately before pressing");
                }
            }
        }
    }

    @Test
    void aChangedDefaultMenuAfterCaptureCancelsTheCompleteHighlightedClick() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareClick(54, 12, "Enter price", speed);
                fixture.script.afterWait = () -> fixture.menuEntry.option = "Copilot graph";
                assertFalse(fixture.click());
                assertEquals(1, fixture.script.matchedWaits, "Capture must succeed before the mutation");
                assertTrue(fixture.backend.events.isEmpty(), "A changed callback must never receive a press");
                assertEquals(1, fixture.backend.movements);
            }
        }
    }

    @Test
    void aChangedResolvedControlAfterCaptureCancelsTheCompleteHighlightedClick() throws Exception {
        for (boolean speed : new boolean[]{false, true}) {
            for (int change = 0; change < 4; change++) {
                try (Fixture fixture = new Fixture()) {
                    fixture.prepareClick(54, 12, "Enter price", speed);
                    final int mutation = change;
                    fixture.script.afterWait = () -> {
                        if (mutation == 0) fixture.actionNode.hidden = true;
                        else if (mutation == 1) fixture.actionNode.bounds.translate(40, 0);
                        else if (mutation == 2) fixture.actionNode.actions = new String[]{"Enter quantity"};
                        else fixture.nodes.get(SETUP_ID).child(54);
                    };
                    assertFalse(fixture.click(), "Resolved control changed, case=" + change);
                    assertEquals(1, fixture.script.matchedWaits, "Capture must succeed before the mutation");
                    assertTrue(fixture.backend.events.isEmpty());
                    assertEquals(1, fixture.backend.movements);
                }
            }
        }
    }

    @Test
    void allAndConfirmVisualLayersResolveOnlyTheirOwnLiveOperations() throws Exception {
        int[][] controls = {{50, 6}, {58, 29}};
        String[] options = {"All", "Confirm"};
        for (int index = 0; index < controls.length; index++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareClick(controls[index][0], controls[index][1], options[index], false);
                assertTrue(fixture.menuMatches());
                fixture.menuEntry.type = MenuAction.CC_OP_LOW_PRIORITY;
                assertTrue(fixture.menuMatches());
                fixture.menuEntry.option = "Enter price";
                fixture.actionNode.actions = new String[]{"Enter price"};
                assertFalse(fixture.menuMatches(), "An overlapping sibling with another operation is not this control");
            }
        }
    }

    @Test
    void visualSiblingMatchingRequiresTheExpectedActionAndItsOneBasedOperationIndex() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareClick(54, 12, "Enter price", false);
            fixture.actionNode.actions = new String[]{"Enter quantity", "<col=ffffff>ENTER\u00a0PRICE</col>"};
            fixture.menuEntry.option = "  Enter  PRICE  ";
            fixture.menuEntry.identifier = 2;
            assertTrue(fixture.menuMatches(), "Formatting must not invalidate the genuine operation");
            for (int operation : new int[]{-1, 0, 1, 3}) {
                fixture.menuEntry.identifier = operation;
                assertFalse(fixture.menuMatches(), "Invalid or different operation " + operation);
            }
            fixture.menuEntry.identifier = 2;
            fixture.actionNode.actions[1] = null;
            assertFalse(fixture.menuMatches());
            fixture.actionNode.actions = null;
            assertFalse(fixture.menuMatches());
        }
    }

    @Test
    void foreignParentsUnknownVisualLayersAndNonWidgetMenuTypesCannotUseTheSiblingException() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareClick(54, 12, "Enter price", false);
            fixture.menuEntry.parentId++;
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.parentId--;
            for (MenuAction type : new MenuAction[]{MenuAction.RUNELITE, MenuAction.WIDGET_TYPE_1, MenuAction.CANCEL}) {
                fixture.menuEntry.type = type;
                assertFalse(fixture.menuMatches(), type.name());
            }
            fixture.menuEntry.type = MenuAction.CC_OP;
            fixture.clickNode = fixture.nodes.get(SETUP_ID).child(55);
            assertFalse(fixture.menuMatches(), "The exception applies only to known Copilot visual controls");
        }
    }

    @Test
    void staleHiddenMissingAndNonoverlappingLiveControlsCannotAuthorizeASiblingClick() throws Exception {
        for (int invalid = 0; invalid < 6; invalid++) {
            try (Fixture fixture = new Fixture()) {
                fixture.prepareClick(54, 12, "Enter price", false);
                Fixture.Node parent = fixture.nodes.get(SETUP_ID);
                switch (invalid) {
                    case 0: parent.hidden = true; break;
                    case 1: fixture.actionNode.hidden = true; break;
                    case 2: fixture.actionNode.bounds.translate(40, 0); break;
                    case 3: parent.children.remove(12); break;
                    case 4: parent.children.put(12, parent.child(13)); break;
                    case 5: parent.child(54); break;
                    default: throw new AssertionError();
                }
                assertFalse(fixture.menuMatches(), "Invalid live control case=" + invalid);
            }
        }
    }

    @Test
    void thePriceVisualCannotAuthorizeTheAdjacentQuantityOperationOrAGraphCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.prepareClick(54, 7, "Enter quantity", false);
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.option = "Copilot graph";
            fixture.actionNode.actions = new String[]{"Copilot graph"};
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.option = "Enter price";
            fixture.actionNode.actions = new String[]{"Enter price"};
            fixture.menuEntry.child = 54;
            fixture.menuEntry.option = "Copilot graph";
            assertFalse(fixture.menuMatches(), "An exact visual-child match cannot bypass graph rejection");
        }
    }

    @Test
    void aNotedInventorySaleStillRequiresItsExactParentChildAndItem() throws Exception {
        try (Fixture fixture = new Fixture()) {
            int inventoryId = 467 << 16;
            Fixture.Node inventory = fixture.node(inventoryId);
            fixture.clickNode = inventory.child(3);
            fixture.clickNode.itemId = 201;
            fixture.menuEntry = fixture.new MutableMenu(inventoryId, 3, "Sell");
            fixture.menuEntry.itemId = 201;
            assertTrue(fixture.menuMatches());
            fixture.menuEntry.child = 4;
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.child = 3;
            fixture.menuEntry.itemId = 202;
            assertFalse(fixture.menuMatches(), "Another item or dose must not reuse a noted stack's highlight");
            fixture.menuEntry.itemId = 201;
            fixture.menuEntry.parentId++;
            assertFalse(fixture.menuMatches());
            fixture.menuEntry.parentId--;
            fixture.menuEntry.option = "Copilot graph";
            assertFalse(fixture.menuMatches());
        }
    }

    @Test
    void theCurrentVisibleTargetAtTheCapturedBoundsAndPointerRemainsUsable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.matches(null));
            assertTrue(fixture.uiReads > 0, "Highlight widget reads must use the client thread");
        }
    }

    @Test
    void anOldControllerOrManagerCannotAuthorizeAQueuedClick() throws Exception {
        try (Fixture fixture = new Fixture()) {
            field(FlipperScript.class, "highlightController").set(fixture.script, new Controller());
            assertFalse(fixture.matches(null));
            field(FlipperScript.class, "highlightController").set(fixture.script, fixture.controller);
            field(FlipperScript.class, "suggestionManager").set(fixture.script, new Manager());
            assertFalse(fixture.matches(null));
        }
    }

    @Test
    void removedAndReplacedHighlightsInvalidateTheCapturedWidget() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.highlightOverlays.clear();
            assertFalse(fixture.matches(null));
            Widget replacement = fixture.node(fixture.node.id).widget;
            fixture.controller.highlightOverlays.add(new Overlay(replacement, fixture.relativeBounds));
            assertFalse(fixture.matches(null), "The same widget ID cannot substitute a different widget instance");
        }
    }

    @Test
    void widgetMovementAndRelativeHighlightMovementInvalidateTheOldClickBounds() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.node.bounds.translate(1, 0);
            assertFalse(fixture.matches(null));
            fixture.node.bounds.translate(-1, 0);
            assertTrue(fixture.matches(null));
            fixture.relativeBounds.translate(0, 1);
            assertFalse(fixture.matches(null));
        }
    }

    @Test
    void pointerDriftOpenMenusAndAnotherPendingActionCancelTheClick() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mouse = new Point(fixture.point.getX() + 1, fixture.point.getY());
            assertFalse(fixture.matches(null));
            fixture.mouse = fixture.point;
            fixture.menuOpen = true;
            assertFalse(fixture.matches(null));
            fixture.menuOpen = false;
            Microbot.targetMenu = new NewMenuEntry().option("Synthetic pending action");
            assertFalse(fixture.matches(null));
            Microbot.targetMenu = null;
            assertTrue(fixture.matches(null));
        }
    }

    @Test
    void hiddenOrMissingTargetsAndPointsOutsideTheHighlightAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.node.hidden = true;
            assertFalse(fixture.matches(null));
            fixture.node.hidden = false;
            fixture.mouse = new Point(fixture.bounds.x - 1, fixture.bounds.y);
            assertFalse(fixture.matches(fixture.controller, fixture.manager, fixture.node.widget,
                fixture.bounds, null, fixture.mouse));
            assertFalse(fixture.matches(fixture.controller, fixture.manager, null, fixture.bounds, null, fixture.point));
            assertFalse(fixture.matches(fixture.controller, fixture.manager, fixture.node.widget, null, null, fixture.point));
            assertFalse(fixture.matches(fixture.controller, fixture.manager, fixture.node.widget, fixture.bounds, null, null));
        }
    }

    @Test
    void aNewOrChangedSuggestionCannotReuseTheCapturedRecommendation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Suggestion suggestion = new Suggestion();
            fixture.manager.suggestion = suggestion;
            assertFalse(fixture.matches(null), "A recommendation arriving during movement invalidates a null capture");
            String key = fixture.suggestionKey(suggestion);
            assertTrue(fixture.matches(key));
            suggestion.itemId++;
            assertFalse(fixture.matches(key), "The same suggestion object can change in place");
            suggestion.itemId--;
            assertTrue(fixture.matches(key));
            fixture.manager.suggestion = null;
            assertFalse(fixture.matches(key));
            fixture.manager.suggestion = new Object();
            assertFalse(fixture.matches(key), "Missing Copilot accessors must fail closed");
        }
    }

    @Test
    void aFinishRequestInvalidatesAnEarlierNormalHighlight() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.matches(null));
            assertTrue(fixture.script.requestFinish(() -> { }));
            assertFalse(fixture.matches(null));
        }
    }

    public static final class Suggestion {
        private int itemId = 100;
        private int boxId;
        private String type = "buy";
        public String getType() { return type; }
        public int getBoxId() { return boxId; }
        public int getItemId() { return itemId; }
        public String getName() { return "Synthetic item"; }
        public int getPrice() { return 123; }
        public int getQuantity() { return 1; }
        public boolean isAbortSuggestion() { return false; }
        public boolean isModifySuggestion() { return false; }
        public boolean isBuySuggestion() { return "buy".equals(type); }
    }

    private static final class Controller {
        private final List<Object> highlightOverlays = new ArrayList<>();
    }

    private static final class Manager {
        private Object suggestion;
    }

    private static final class Overlay {
        private final Widget widget;
        private final Rectangle relativeBounds;
        private Overlay(Widget widget, Rectangle relativeBounds) {
            this.widget = widget;
            this.relativeBounds = relativeBounds;
        }
    }

    private static final class TestScript extends FlipperScript {
        private Runnable afterWait = () -> { };
        private int matchedWaits;
        @Override public boolean isRunning() { return true; }
        @Override public boolean isSlotActionSwapEnabled() { return false; }
        @Override boolean waitForUi(BooleanSupplier predicate, int timeoutMs) {
            boolean matched = predicate.getAsBoolean();
            if (matched) {
                matchedWaits++;
                afterWait.run();
            }
            return matched;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Field clientField = field(Microbot.class, "client");
        private final Field threadField = field(Microbot.class, "clientThread");
        private final Object previousClient = clientField.get(null);
        private final Object previousThread = threadField.get(null);
        private final MenuEntry previousMenu = Microbot.targetMenu;
        private final boolean previousPause = Microbot.pauseAllScripts.get();
        private final TestScript script = new TestScript();
        private final Controller controller = new Controller();
        private final Manager manager = new Manager();
        private final Map<Integer, Node> nodes = new HashMap<>();
        private final Node node = node(1001);
        private final Rectangle relativeBounds = new Rectangle(5, 7, 60, 40);
        private final Rectangle bounds = new Rectangle(25, 37, 60, 40);
        private final Point point = new Point(35, 45);
        private Point mouse = point;
        private boolean menuOpen, onClientThread;
        private int uiReads, menuReads;
        private Node clickNode, actionNode;
        private MutableMenu menuEntry;
        private GrandExchangeOffer[] offers;
        private ClickBackend backend;
        private final Menu menu = (Menu) Proxy.newProxyInstance(Menu.class.getClassLoader(), new Class<?>[]{Menu.class},
            (proxy, method, args) -> {
                assertTrue(onClientThread, "Menu read outside client thread");
                if (method.getName().equals("getMenuEntries")) {
                    menuReads++;
                    return menuEntry == null ? new MenuEntry[0] : new MenuEntry[]{menuEntry.entry};
                }
                throw new AssertionError("Unexpected menu read: " + method.getName());
            });
        private final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> call) {
                boolean previous = onClientThread;
                assertFalse(backend != null && backend.inGesture, "Client validation cannot hold the input lock");
                onClientThread = true;
                uiReads++;
                try { return Optional.ofNullable(call.call()); }
                catch (Exception error) { throw new AssertionError(error); }
                finally { onClientThread = previous; }
            }
        };

        private Fixture() throws Exception {
            controller.highlightOverlays.add(new Overlay(node.widget, relativeBounds));
            field(FlipperScript.class, "highlightController").set(script, controller);
            field(FlipperScript.class, "suggestionManager").set(script, manager);
            field(FlipperScript.class, "config").set(script, new FlipperConfig() { });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
                    assertTrue(onClientThread, "Highlight client read outside client thread: " + method.getName());
                    switch (method.getName()) {
                        case "isMenuOpen": return menuOpen;
                        case "getMouseCanvasPosition": return mouse;
                        case "getCanvasWidth": return 100;
                        case "getCanvasHeight": return 100;
                        case "getMenu": return menu;
                        case "getGrandExchangeOffers": return offers;
                        case "getWidget": {
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            Node current = nodes.get(id);
                            return current == null ? null : current.widget;
                        }
                        default: throw new AssertionError("Unexpected highlight-client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
            Microbot.targetMenu = null;
            Microbot.pauseAllScripts.set(false);
            net.runelite.client.plugins.microbot.util.input.PointerState.setInside(1, 1);
        }

        private Node node(int id) {
            Node result = new Node(id, -1);
            nodes.put(id, result);
            return result;
        }

        private void prepareClick(int visualChild, int actionChild, String option, boolean speed) throws Exception {
            Node parent = node(SETUP_ID);
            clickNode = parent.child(visualChild);
            actionNode = parent.child(actionChild);
            // A zero-origin click rectangle uses the SDK's deterministic point (1, 1),
            // avoiding any real mouse singleton while exercising the actual click path.
            clickNode.bounds.setBounds(-1, -6, 34, 29);
            relativeBounds.setBounds(1, 6, 33, 23);
            actionNode.bounds.setBounds(0, 0, 33, 23);
            actionNode.actions = new String[]{option};
            controller.highlightOverlays.clear();
            controller.highlightOverlays.add(new Overlay(clickNode.widget, relativeBounds));
            menuEntry = new MutableMenu(SETUP_ID, actionChild, option);
            field(FlipperScript.class, "config").set(script, new FlipperConfig() {
                @Override public boolean randomizeMouseSpeed() { return speed; }
            });
            backend = new ClickBackend();
            field(FlipperScript.class, "mouseMotion").set(script,
                new GeflipperMouseMotion(backend, (minimum, maximum) -> {
                    backend.speedSamples++;
                    return 100;
                }, new Random(17), (nature, random, x, y) -> nature.getSystemCalls().setMousePosition(x, y)));
        }

        private boolean click() throws Exception {
            Method method = FlipperScript.class.getDeclaredMethod("clickHighlightedTarget", FlipperScript.HighlightTarget.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(script, new FlipperScript.HighlightTarget(clickNode.widget, relativeBounds));
        }

        private void prepareBuy(int slot, boolean speed) throws Exception {
            prepareClick(54, 12, "Enter price", speed);
            nodes.get(SETUP_ID).hidden = true;
            node(InterfaceID.GeOffers.INDEX);
            Node parent = node(buySlotId(slot));
            clickNode = parent.child(0);
            actionNode = parent.child(3);
            // The live visual and native button differ by one pixel. Translate
            // both rectangles to the SDK's deterministic zero-origin click area.
            clickNode.bounds.setBounds(-1, -1, 46, 45);
            relativeBounds.setBounds(1, 1, 45, 44);
            actionNode.bounds.setBounds(-2, -2, 46, 45);
            actionNode.actions = new String[]{"Create <col=ff9040>Buy</col> offer"};
            controller.highlightOverlays.clear();
            controller.highlightOverlays.add(new Overlay(clickNode.widget, relativeBounds));
            menuEntry = new MutableMenu(parent.id, 3, "Create <col=ff9040>Buy</col> offer");
            offers = new GrandExchangeOffer[8];
            java.util.Arrays.setAll(offers, index -> offer(GrandExchangeOfferState.EMPTY));
            Suggestion suggestion = new Suggestion();
            suggestion.boxId = slot;
            manager.suggestion = suggestion;
        }

        private void prepareCollectAll(boolean speed) throws Exception {
            prepareClick(54, 12, "Enter price", speed);
            nodes.get(SETUP_ID).hidden = true;
            node(InterfaceID.GeOffers.INDEX);
            Node parent = node(InterfaceID.GeOffers.COLLECTALL);
            clickNode = parent.child(2);
            actionNode = parent.child(0);
            // Preserve the live label/button's one-pixel vertical difference.
            clickNode.bounds.setBounds(-2, -1, 85, 20);
            relativeBounds.setBounds(2, 1, 81, 18);
            actionNode.bounds.setBounds(-2, -2, 85, 22);
            actionNode.actions = new String[]{"Collect to inventory", "Collect to bank"};
            controller.highlightOverlays.clear();
            controller.highlightOverlays.add(new Overlay(clickNode.widget, relativeBounds));
            menuEntry = new MutableMenu(InterfaceID.GeOffers.COLLECTALL, 0, "Collect to inventory");
            // Copilot may recommend a new buy while pending items need collecting.
            manager.suggestion = new Suggestion();
        }

        private int buySlotId(int slot) {
            return new int[]{InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1,
                InterfaceID.GeOffers.INDEX_2, InterfaceID.GeOffers.INDEX_3,
                InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7}[slot];
        }

        private GrandExchangeOffer offer(GrandExchangeOfferState state) {
            return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
                new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                    assertTrue(onClientThread, "Offer read outside client thread");
                    if (method.getName().equals("getState")) return state;
                    throw new AssertionError("Unexpected offer read: " + method.getName());
                });
        }

        private void prepareStaticConfirm(boolean speed) throws Exception {
            prepareClick(58, 29, "Confirm", speed);
            // Copilot draws over SETUP's visual child 58, but the client emits
            // a native operation for the separate static SETUP_CONFIRM widget.
            actionNode = node(CONFIRM_ID);
            actionNode.bounds.setBounds(0, 0, 33, 23);
            actionNode.actions = new String[]{"Confirm"};
            menuEntry = new MutableMenu(CONFIRM_ID, -1, "Confirm");
        }

        private void prepareFinish(String type, boolean fresh) throws Exception {
            FinishSessionTest.Fixture copilot = new FinishSessionTest.Fixture();
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(script);
            assertTrue(script.requestFinish(() -> { }));
            assertTrue(session.begin(null, copilot.controller, copilot.manager));
            if (fresh) copilot.manager.completeRequest();
            Suggestion suggestion = new Suggestion();
            suggestion.type = type;
            manager.suggestion = suggestion;
        }

        private boolean menuMatches() {
            return inline.runOnClientThreadOptional(() -> script.highlightedMenuMatches(menuEntry.entry,
                clickNode.widget, new Point(1, 1))).orElse(false);
        }

        private boolean matches(String key) {
            return matches(controller, manager, node.widget, bounds, key, point);
        }

        private boolean matches(Object capturedController, Object capturedManager, Widget widget,
                                Rectangle capturedBounds, String key, Point capturedPoint) {
            return inline.runOnClientThreadOptional(() -> script.highlightedTargetMatches(capturedController,
                capturedManager, widget, capturedBounds, key, capturedPoint)).orElse(false);
        }

        private String suggestionKey(Object suggestion) throws Exception {
            Method capture = FlipperScript.class.getDeclaredMethod("slotActionKey", Object.class);
            capture.setAccessible(true);
            return (String) capture.invoke(script, suggestion);
        }

        @Override public void close() throws Exception {
            ((FinishSession) field(FlipperScript.class, "finishSession").get(script)).close();
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            Microbot.targetMenu = previousMenu;
            Microbot.pauseAllScripts.set(previousPause);
            net.runelite.client.plugins.microbot.util.input.PointerState.reset();
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }

        private final class Node {
            private final int id;
            private final int index;
            private final Widget widget;
            private final Rectangle bounds = new Rectangle(20, 30, 100, 80);
            private final Map<Integer, Node> children = new HashMap<>();
            private String[] actions;
            private int itemId = -1;
            private boolean hidden;
            private Node(int id, int index) {
                this.id = id;
                this.index = index;
                widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        assertTrue(onClientThread, "Highlight widget read outside client thread: " + method.getName());
                        switch (method.getName()) {
                            case "getId": return id;
                            case "getIndex": return index;
                            case "getItemId": return itemId;
                            case "getActions": return actions;
                            case "getParentId": return index < 0 ? -1 : id;
                            case "getChild": {
                                Node child = children.get((Integer) args[0]);
                                return child == null ? null : child.widget;
                            }
                            case "isHidden": return hidden;
                            case "getBounds": return bounds;
                            default: throw new AssertionError("Unexpected highlight-widget read: " + method.getName());
                        }
                    });
            }

            private Node child(int childIndex) {
                Node child = new Node(id, childIndex);
                children.put(childIndex, child);
                return child;
            }
        }

        private final class MutableMenu {
            private MenuAction type = MenuAction.CC_OP;
            private int parentId;
            private int child, identifier = 1, itemId = -1;
            private String option;
            private final MenuEntry entry;

            private MutableMenu(int parentId, int child, String option) {
                this.parentId = parentId;
                this.child = child;
                this.option = option;
                entry = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[]{MenuEntry.class},
                    (proxy, method, args) -> {
                        assertTrue(onClientThread, "Menu entry read outside client thread");
                        switch (method.getName()) {
                            case "getType": return type;
                            case "getIdentifier": return identifier;
                            case "getParam0": return this.child;
                            case "getParam1": return this.parentId;
                            case "getItemId": return itemId;
                            case "getOption": return this.option;
                            case "getTarget": return "Synthetic setup control";
                            default: throw new AssertionError("Unexpected menu-entry read: " + method.getName());
                        }
                    });
            }
        }

        private final class ClickBackend implements GeflipperMouseMotion.Backend {
            private final MouseMotionNature nature = new MouseMotionNature();
            private final List<String> events = new ArrayList<>();
            private int movements, speedSamples;
            private boolean inGesture;

            private ClickBackend() {
                // Match the SDK's initialized stock policy; a bare MouseMotionNature
                // has zero timing scalars and correctly fails the production guard.
                nature.setTimeToStepsDivider(8);
                nature.setMinSteps(10);
                nature.setEffectFadeSteps(15);
                nature.setReactionTimeBaseMs(0);
                nature.setReactionTimeVariationMs(0);
                nature.setNoiseProvider(new DefaultNoiseProvider(2));
                nature.setDeviationProvider(new SinusoidalDeviationProvider(10));
                DefaultOvershootManager overshoot = new DefaultOvershootManager(new Random(17));
                overshoot.setOvershoots(0);
                nature.setOvershootManager(overshoot);
                nature.setSpeedManager(distance -> new Pair<>(new Flow(new double[]{1, 1}), 10L));
            }
            public boolean worker() { return true; }
            public boolean human() { return false; }
            public GeflipperMouseMotion.Snapshot snapshot() { return new GeflipperMouseMotion.Snapshot(100, 100, nature); }
            public Point position() { return mouse; }
            public boolean gesture(Consumer<GeflipperMouseMotion.Sink> gesture) {
                assertFalse(onClientThread);
                assertFalse(inGesture);
                inGesture = true;
                try {
                    gesture.accept(new GeflipperMouseMotion.Sink() {
                        public void checkpoint() { }
                        public void move(int x, int y) { movements++; mouse = new Point(x, y); }
                        public void press(int x, int y) { events.add("press"); }
                        public void release(int x, int y) { events.add("release"); }
                        public void click(int x, int y) { events.add("click"); }
                    });
                    return true;
                } finally { inGesture = false; }
            }
            public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) {
                assertEquals(GeflipperMouseMotion.Action.MOVE, action);
                movements++;
                mouse = point;
            }
            public MenuEntry pendingMenu() { return Microbot.targetMenu; }
            public void pendingMenu(MenuEntry entry) { Microbot.targetMenu = entry; }
            public void clicked(Point point) { }
            public long millis() { return 0; }
            public void sleep(long millis) { }
        }
    }
}
