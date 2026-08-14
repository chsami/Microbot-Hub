package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BankSellerScript extends Script {

    /** Coins (995) and platinum tokens (13204) are never sold. */
    private static final Set<Integer> IGNORED_ITEM_IDS = new HashSet<>(Arrays.asList(995, 13204));

    private static final int SETUP_WAIT_TIMEOUT_MS = 5_000;
    private static final int BANK_OPEN_TIMEOUT_MS = 10_000;
    private static final int BANK_LOAD_GRACE_MS = 3_000;
    private static final int EXCHANGE_OPEN_TIMEOUT_MS = 10_000;
    private static final int CONFIRM_WAIT_TIMEOUT_MS = 10_000;
    private static final int MAX_OFFER_ATTEMPTS = 2;

    /** An unsold leftover offer gets this long to fill before it is re-listed at 1gp. */
    private static final int LEFTOVER_OFFER_TIMEOUT_MS = 90_000;

    /** After the 1gp re-list the plugin stops anyway when nothing sold within this window. */
    private static final int LEFTOVER_GIVE_UP_MS = 60_000;

    /**
     * Items the Grand Exchange refused to sell this session, e.g. items on the
     * F2P new-account trade restriction list. They are put back in the bank
     * and are not withdrawn again.
     */
    private final Set<Integer> unsellableItemIds = new HashSet<>();

    private BankSellerPlugin plugin;

    /** Whether the GE offer setup opened for the current attempt (drives retry). */
    private boolean offerSetupSeen;

    /** Set when the current item's setup shows the red trade-restriction notice. */
    private boolean tradeRestrictionSeen;
    private String tradeRestrictionText;

    /**
     * Set once a bank scan finds nothing sellable and the inventory is empty.
     * The main loop then stops opening the bank/GE every cycle and only
     * watches the remaining offers (no mouse movement while waiting).
     */
    private boolean bankDrained;

    /** When set (leftover-offer liquidation), every offer is placed at this price. */
    private Integer forcedSellPrice;

    /** Last time the drained watch saw an offer sell or finish. */
    private long lastOfferProgressAt;

    /** True once leftover offers have been aborted and re-listed at 1gp. */
    private boolean leftoverLiquidated;

    /** Session tallies driving the final chatbox verdict when nothing is left to sell. */
    private int sessionOffersPlaced;
    private int sessionStacksRefused;

    public boolean run(BankSellerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        this.plugin = plugin;
        unsellableItemIds.clear();
        offerSetupSeen = false;
        tradeRestrictionSeen = false;
        tradeRestrictionText = null;
        bankDrained = false;
        forcedSellPrice = null;
        lastOfferProgressAt = System.currentTimeMillis();
        leftoverLiquidated = false;
        sessionOffersPlaced = 0;
        sessionStacksRefused = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                collectSoldOffers();

                if (bankDrained) {
                    // Bank and inventory are both drained of sellables - just watch
                    // the remaining offers. No bank/GE opening, no mouse movement.
                    if (Rs2GrandExchange.hasSoldOffer()) {
                        collectPendingOffers();
                        lastOfferProgressAt = System.currentTimeMillis();
                    }
                    if (Rs2GrandExchange.isAllSlotsEmpty()) {
                        announce(finalVerdictMessage());
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                    long watchIdleMs = System.currentTimeMillis() - lastOfferProgressAt;
                    if (!leftoverLiquidated && watchIdleMs > LEFTOVER_OFFER_TIMEOUT_MS) {
                        // An offer that will not fill at the instant-sell price gets
                        // one final attempt at 1gp so the run can actually finish
                        leftoverLiquidated = liquidateLeftoverOffers();
                        // On failure retry in ~15s instead of waiting the full timeout again
                        lastOfferProgressAt = System.currentTimeMillis()
                                - (leftoverLiquidated ? 0 : LEFTOVER_OFFER_TIMEOUT_MS - 15_000);
                    } else if (leftoverLiquidated && watchIdleMs > LEFTOVER_GIVE_UP_MS) {
                        announce("1gp leftover offer(s) are still open - stopping anyway; "
                                + "collect them at the Grand Exchange later");
                        Microbot.stopPlugin(plugin);
                    }
                    return;
                }

                // Always bank first: deposit everything, then pull out every sellable item as notes
                boolean bankHasSellables = bankAndWithdrawAll();

                if (!hasSellableInventoryItems()) {
                    if (bankHasSellables) {
                        // Withdrawing failed (e.g. bank unreachable) - retry next cycle
                        return;
                    }
                    // The bank scan found nothing sellable - switch to the idle
                    // drained watch from the next cycle on
                    bankDrained = true;
                    lastOfferProgressAt = System.currentTimeMillis();
                    return;
                }

                sellInventory();
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isSellable(Rs2ItemModel item) {
        return item.isTradeable()
                && !IGNORED_ITEM_IDS.contains(item.getUnNotedId())
                && !unsellableItemIds.contains(item.getUnNotedId());
    }

    private boolean hasSellableInventoryItems() {
        return Rs2Inventory.all(this::isSellable).size() > 0;
    }

    private void collectSoldOffers() {
        if (!Rs2GrandExchange.hasSoldOffer()) {
            return;
        }
        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return;
        }
        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer());
        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
    }

    /**
     * Collects finished offers and checks if any offers are still pending.
     * Only a positively opened exchange counts - a failed open means "unknown",
     * never "done".
     *
     * @return true when every Grand Exchange slot is empty and the plugin can stop
     */
    private boolean collectPendingOffers() {
        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return false;
        }
        Rs2GrandExchange.collectAllToBank();
        sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer());
        boolean allSlotsEmpty = Rs2GrandExchange.isAllSlotsEmpty();
        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
        return allSlotsEmpty;
    }

    /**
     * Endgame sweep for offers that never filled: aborts them, takes the items
     * back into the inventory and re-lists each stack at 1gp so the run finishes.
     *
     * @return true when the leftovers were re-listed (or nothing came back)
     */
    private boolean liquidateLeftoverOffers() {
        announce("Leftover offer(s) not selling - re-listing at 1gp");
        if (!Rs2GrandExchange.openExchange()
                || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return false;
        }
        // abortAllOffers(false) aborts every open offer and collects to the inventory
        Rs2GrandExchange.abortAllOffers(false);
        sleepUntil(() -> !Rs2GrandExchange.hasOffer(), SETUP_WAIT_TIMEOUT_MS);
        sleep(600, 1000);
        if (!hasSellableInventoryItems()) {
            return true;
        }
        forcedSellPrice = 1;
        try {
            sellInventory();
        } finally {
            forcedSellPrice = null;
        }
        return true;
    }

    /**
     * Opens the bank, deposits the whole inventory and withdraws every sellable
     * item (as notes) until the inventory is full.
     *
     * @return true when the bank still holds sellable items after withdrawing,
     *         or when the bank could not be opened/read (so the cycle retries
     *         instead of wrongly concluding there is nothing left to sell)
     */
    private boolean bankAndWithdrawAll() {
        if (!Rs2Bank.openBank()) {
            return true;
        }
        if (!sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS)) {
            // Bank never opened (e.g. bank pin) - retry next cycle
            return true;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty);

        Rs2Bank.setWithdrawAsNote();

        // Give the bank contents a moment to load before trusting an empty scan
        sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), BANK_LOAD_GRACE_MS);

        boolean bankHasSellables = false;
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            if (Thread.currentThread().isInterrupted()) {
                // Plugin was stopped mid-pass - stop clicking items
                break;
            }
            if (!isSellable(item)) {
                continue;
            }
            bankHasSellables = true;
            if (Rs2Inventory.isFull()) {
                break;
            }
            Rs2Bank.withdrawAll(item.getId());
            boolean got = sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true));
            if (!got) {
                // Items without a noted form ("This item cannot be withdrawn as
                // a note") still need to be sold - withdraw them unnoted
                Rs2Bank.setWithdrawAsItem();
                Rs2Bank.withdrawAll(item.getId());
                sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true));
                Rs2Bank.setWithdrawAsNote();
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        return bankHasSellables;
    }

    private void sellInventory() {
        // Group by item so the whole stack of an item is sold in a single offer
        Map<Integer, List<Rs2ItemModel>> itemsById = Rs2Inventory.all(this::isSellable).stream()
                .collect(Collectors.groupingBy(Rs2ItemModel::getUnNotedId, LinkedHashMap::new, Collectors.toList()));
        if (itemsById.isEmpty()) {
            return;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return;
        }

        List<Rs2ItemModel> failedItems = new ArrayList<>();

        for (Map.Entry<Integer, List<Rs2ItemModel>> entry : itemsById.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                // Plugin was stopped mid-pass - a set interrupt flag makes every
                // sleepUntil return instantly, which would mass-flag good items
                break;
            }
            Rs2ItemModel item = entry.getValue().get(0);
            int quantity = entry.getValue().stream().mapToInt(Rs2ItemModel::getQuantity).sum();

            // A failed offer flow can leave the GE window closed (ESC abort) -
            // clicking "Offer" then hits the plain inventory and uses/drops items
            if (!Rs2GrandExchange.isOpen()) {
                if (!Rs2GrandExchange.openExchange()
                        || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
                    break;
                }
            }

            waitForAvailableSlot();
            if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                break;
            }

            int guidePrice = Rs2GrandExchange.getPrice(item.getUnNotedId());
            // Instant-sell pricing: half the active price so the offer fills
            // immediately (the endgame leftover sweep forces 1gp instead)
            int price = forcedSellPrice != null ? forcedSellPrice : Math.max(1, guidePrice / 2);

            boolean offerPlaced = placeSellOffer(item, quantity, price);
            int remaining = inventoryTradeQuantity(item.getId());

            if (!offerPlaced || remaining > 0) {
                // The GE refused the item (e.g. F2P trade-restricted) - put it back in the bank
                unsellableItemIds.add(item.getUnNotedId());
                failedItems.addAll(entry.getValue());
                sessionStacksRefused++;
            } else {
                sessionOffersPlaced++;
            }
        }

        // A refused last item leaves its offer setup open - back out of it
        // before closing the GE so the window state is clean for the bank
        if (isSetupVisible()) {
            abortOfferSetup();
            sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
        }

        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen());
        }

        depositUnsellableItems(failedItems);
    }

    /**
     * Places a single sell offer for the full stack of an item using the same
     * Grand Exchange widget flow as GodsFlipper: open the setup with "Offer",
     * enter the price through the chatbox input, set the quantity with the
     * "All" button and confirm.
     *
     * @return true when the offer was confirmed and the items left the inventory
     */
    private boolean placeSellOffer(Rs2ItemModel item, int quantity, int price) {
        for (int attempt = 1; attempt <= MAX_OFFER_ATTEMPTS; attempt++) {
            offerSetupSeen = false;
            tradeRestrictionSeen = false;
            try {
                boolean placed = doPlaceSellOffer(item, quantity, price);
                // A setup that never opened, or a trade-restriction notice, is
                // a genuine refusal - retrying would just waste another timeout
                if (placed || tradeRestrictionSeen || !offerSetupSeen || attempt == MAX_OFFER_ATTEMPTS) {
                    return placed;
                }
                abortOfferSetup();
                sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
                if (!Rs2GrandExchange.isOpen()) {
                    if (!Rs2GrandExchange.openExchange()
                            || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
                        return false;
                    }
                }
            } catch (RuntimeException ex) {
                // Transient UI errors (e.g. a widget without bounds on a slow client)
                // must not kill the whole sell pass - treat this item as refused
                Microbot.log("Sell offer failed for " + item.getName() + ": " + ex.getMessage());
                abortOfferSetup();
                return false;
            }
        }
        return false;
    }

    private boolean doPlaceSellOffer(Rs2ItemModel item, int quantity, int price) {
        // A setup left open by a trade-restricted item is reused instead of
        // backing out: clicking 'Offer' on the next item switches the setup
        // straight over to it, so refused items never close the GE flow
        if (!Rs2Inventory.interact(item.getId(), "Offer")) {
            return false;
        }

        // A reused setup stays visible the whole time - the wait must hold
        // out until the setup actually shows THIS item, not the previous one
        if (!sleepUntil(() -> isSetupVisible() && sameTradeItem(resolveSetupItemId(item.getId()), item.getId()),
                SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        offerSetupSeen = true;

        // A red "restricted for trading" notice in the setup means Confirm is
        // dead for this item - fail fast, no retry. The setup is LEFT OPEN so
        // the next item's 'Offer' click switches straight over to it.
        String restrictionNotice = findTradeRestrictionNotice();
        if (restrictionNotice != null) {
            tradeRestrictionSeen = true;
            tradeRestrictionText = restrictionNotice;
            return false;
        }

        if (!enterPrice(price)) {
            abortOfferSetup();
            return false;
        }

        if (!enterFullQuantity(quantity)) {
            abortOfferSetup();
            return false;
        }

        Widget confirmButton = findConfirmButton();
        if (!clickWidget(confirmButton)) {
            abortOfferSetup();
            return false;
        }

        // The GE warns when the price is far from the guide price (often, at
        // instant-sell prices) - keep accepting it until the setup closes
        long confirmDeadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < confirmDeadline) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            boolean warningUp = isGePriceWarningVisible();
            if (!isSetupVisible() && !warningUp) {
                return true;
            }
            if (warningUp) {
                acceptGePriceWarning();
            }
            sleep(200, 350);
        }

        // The offer can go through despite a lingering setup - trust the inventory
        return inventoryTradeQuantity(item.getId()) == 0;
    }

    /**
     * The GE price warning text varies by game version and side (buy/sell), so
     * match every known variant plus a generic chatbox "Select an Option" menu
     * (which is exactly what the Yes/No warning renders as).
     */
    private boolean isGePriceWarningVisible() {
        return Rs2Widget.hasWidget("Your offer is much")
                || Rs2Widget.hasWidget("much lower than")
                || Rs2Widget.hasWidget("much higher than")
                || Rs2Widget.hasWidget("Select an Option");
    }

    private void acceptGePriceWarning() {
        if (!Rs2Widget.clickWidget("Yes")) {
            // Chatbox option menus also accept number keys: 1 = first option (Yes)
            Rs2Keyboard.keyPress(KeyEvent.VK_1);
        }
    }

    /**
     * Types the price into the GE "Enter price" chatbox input.
     */
    private boolean enterPrice(int price) {
        if (!clickWidget(findCustomPriceButton())) {
            return false;
        }
        if (!sleepUntil(this::isChatboxInputVisible, SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        if (!setChatboxInputValue(price)) {
            return false;
        }
        sleep(250, 400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), SETUP_WAIT_TIMEOUT_MS);
    }

    /**
     * Sets the offer quantity to the whole stack with the "All" button,
     * falling back to the custom quantity chatbox input.
     */
    private boolean enterFullQuantity(int quantity) {
        Widget allButton = findAllQuantityButton();
        if (allButton != null && clickWidget(allButton)) {
            sleep(300, 600);
            return true;
        }

        if (!clickWidget(findQuantityButton())) {
            return false;
        }
        if (!sleepUntil(this::isChatboxInputVisible, SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        if (!setChatboxInputValue(quantity)) {
            return false;
        }
        sleep(250, 400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), SETUP_WAIT_TIMEOUT_MS);
    }

    /**
     * Backs out of a half-finished offer setup so the next item starts clean.
     * Uses the setup's Back arrow (returning to the GE overview) - a plain
     * ESC would close the whole Grand Exchange window and force a reopen.
     */
    private void abortOfferSetup() {
        if (isChatboxInputVisible()) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(300, 600);
        }
        if (isSetupVisible()) {
            if (clickWidget(findBackButton()) && sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS)) {
                return;
            }
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
        }
    }

    private Widget findBackButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (!isVisible(setup)) {
                return null;
            }
            // The back arrow is a sibling of the setup panel, not inside it
            Widget parent = setup.getParent();
            Widget match = findWidgetRecursive(parent, this::isBackWidget, 0);
            return match != null ? match : findWidgetRecursive(setup, this::isBackWidget, 0);
        }, null);
    }

    private boolean isBackWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (normalize(action).equals("back")) {
                return true;
            }
        }
        return false;
    }

    private void waitForAvailableSlot() {
        while (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
            if (Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectAllToBank();
            }
            sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0
                    || Rs2GrandExchange.hasSoldOffer());
        }
    }

    private void depositUnsellableItems(List<Rs2ItemModel> failedItems) {
        if (failedItems.isEmpty()) {
            return;
        }
        if (!Rs2Bank.openBank()) {
            return;
        }
        sleepUntil(Rs2Bank::isOpen);
        for (Rs2ItemModel item : failedItems) {
            Rs2Bank.depositAll(item.getId());
            sleepUntil(() -> !Rs2Inventory.hasItem(item.getId()));
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    // ------------------------------------------------------------------
    // Grand Exchange widget helpers (mirrors GodsFlipper's sell setup flow)
    // ------------------------------------------------------------------

    private int inventoryTradeQuantity(int itemId) {
        int quantity = 0;
        for (Rs2ItemModel inventoryItem : Rs2Inventory.all()) {
            if (inventoryItem != null && sameTradeItem(inventoryItem.getId(), itemId)) {
                quantity += Math.max(0, inventoryItem.getQuantity());
            }
        }
        return quantity;
    }

    private int canonicalTradeItemId(int itemId) {
        if (itemId <= 0) {
            return itemId;
        }
        int unnotedId = Rs2ItemModel.getUnNotedId(itemId);
        return unnotedId > 0 ? unnotedId : itemId;
    }

    private boolean sameTradeItem(int leftItemId, int rightItemId) {
        return leftItemId > 0
                && rightItemId > 0
                && canonicalTradeItemId(leftItemId) == canonicalTradeItemId(rightItemId);
    }

    private boolean isSetupVisible() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return isVisible(setup);
        }, false);
    }

    private boolean isChatboxInputVisible() {
        return clientValue(() -> {
            Widget input = Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT);
            return isVisible(input);
        }, false);
    }

    private boolean setChatboxInputValue(long value) {
        if (value <= 0) {
            return false;
        }
        return clientValue(() -> {
            Widget input = Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT);
            if (!isVisible(input)) {
                return false;
            }
            input.setText(value + "*");
            Microbot.getClient().setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(value));
            return true;
        }, false);
    }

    /**
     * Returns the item id shown in the open offer setup when it matches the
     * expected item (noted or unnoted), otherwise -1.
     */
    private int resolveSetupItemId(int expectedItemId) {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (!isVisible(setup)) {
                return -1;
            }
            return containsItemId(setup, expectedItemId, 0) ? expectedItemId : -1;
        }, -1);
    }

    /**
     * Returns the red trade-restriction notice shown inside the offer setup on
     * restricted accounts ("Your account will be restricted for trading until
     * ..."), or null when the setup is clear.
     */
    private String findTradeRestrictionNotice() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget match = findWidgetRecursive(setup, widget -> {
                String value = normalize(widget == null ? null : widget.getText());
                return value.contains("restricted for trading") || value.contains("restrictions will lift");
            }, 0);
            return match == null ? null : match.getText().replaceAll("<[^>]*>", "").trim();
        }, null);
    }

    private boolean containsItemId(Widget root, int expectedItemId, int depth) {
        if (root == null || expectedItemId <= 0 || depth > 14) {
            return false;
        }
        if (root.getItemId() > 0 && sameTradeItem(root.getItemId(), expectedItemId)) {
            return true;
        }
        Widget[] dynamicChildren = root.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                if (containsItemId(child, expectedItemId, depth + 1)) {
                    return true;
                }
            }
        }
        Widget[] staticChildren = root.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                if (containsItemId(child, expectedItemId, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Widget findCustomPriceButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isCustomPriceWidget, 0);
        }, null);
    }

    private Widget findQuantityButton() {
        return clientValue(() -> {
            Widget offerContainer = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(offerContainer)) {
                Widget exactButton = offerContainer.getChild(7);
                if (isClickable(exactButton)) {
                    return exactButton;
                }
            }
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isQuantityWidget, 0);
        }, null);
    }

    private Widget findAllQuantityButton() {
        return clientValue(() -> {
            Widget offerContainer = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(offerContainer)) {
                Widget exactButton = offerContainer.getChild(50);
                if (isClickable(exactButton) && isAllQuantityWidget(exactButton)) {
                    return exactButton;
                }
                Widget legacyButton = offerContainer.getChild(6);
                if (isClickable(legacyButton) && isAllQuantityWidget(legacyButton)) {
                    return legacyButton;
                }
                return findWidgetRecursive(offerContainer, this::isAllQuantityWidget, 0);
            }
            return null;
        }, null);
    }

    private Widget findConfirmButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isConfirmWidget, 0);
        }, null);
    }

    private Widget findWidgetRecursive(Widget root, Predicate<Widget> predicate, int depth) {
        if (!isVisible(root) || predicate == null || depth > 14) {
            return null;
        }
        if (predicate.test(root) && root.getBounds() != null) {
            return root;
        }
        Widget[] dynamicChildren = root.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) {
                    return match;
                }
            }
        }
        Widget[] staticChildren = root.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private boolean isCustomPriceWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            String value = normalize(action);
            if (value.equals("enter price")
                    || value.contains("custom price")
                    || value.equals("set price")) {
                return true;
            }
        }
        return false;
    }

    private boolean isQuantityWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String text = normalize(widget.getText());
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                String value = normalize(action);
                if (value.equals("enter quantity")
                        || value.equals("set quantity")
                        || value.contains("custom quantity")) {
                    return true;
                }
            }
        }
        return text.contains("quantity") && hasAnyAction(widget);
    }

    private boolean isAllQuantityWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        if ("all".equals(normalize(widget.getText()))) {
            return true;
        }
        if (!hasAnyAction(widget)) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                String value = normalize(action);
                if ("all".equals(value)
                        || value.contains("set all")
                        || (value.contains("all") && value.contains("quantity"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConfirmWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                if (normalize(action).contains("confirm")) {
                    return true;
                }
            }
        }
        return normalize(widget.getText()).contains("confirm") && hasAnyAction(widget);
    }

    private boolean clickWidget(Widget widget) {
        // Widget state (isHidden etc.) asserts the client thread on newer
        // clients, so every check must happen inside clientValue - a plain
        // null check is the only thing safe to do on the script thread
        if (widget == null) {
            return false;
        }
        try {
            return clientValue(() -> {
                if (!isClickable(widget)) {
                    return false;
                }
                return Rs2Widget.clickWidget(widget);
            }, false);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isClickable(Widget widget) {
        return isVisible(widget) && widget.getBounds() != null;
    }

    private boolean isVisible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    private boolean hasAnyAction(Widget widget) {
        String[] actions = widget == null ? null : widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && !action.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("<[^>]*>", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // User-facing chatbox messages (final verdict, 1gp leftover sweep)
    // ------------------------------------------------------------------

    /**
     * Posts a single user-facing line to the in-game chatbox and the client log.
     */
    private void announce(String message) {
        String line = "[BankSeller] " + message;
        Microbot.log(line);
        clientValue(() -> {
            Microbot.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null, false);
            return true;
        }, false);
    }

    /**
     * The single chatbox verdict when the bank is fully processed:
     * an error when the GE refused everything, a summary otherwise.
     */
    private String finalVerdictMessage() {
        if (sessionStacksRefused > 0 && sessionOffersPlaced == 0) {
            String message = "ERROR: the Grand Exchange refused every item (" + sessionStacksRefused
                    + " stack(s)) - nothing can be sold. Stopping.";
            if (tradeRestrictionText != null) {
                message += " " + tradeRestrictionText;
            }
            return message;
        }
        if (sessionStacksRefused > 0) {
            return "Nothing left to sell - " + sessionStacksRefused
                    + " stack(s) were refused by the GE. Stopping.";
        }
        return "Nothing left to sell and all Grand Exchange slots are empty - stopping";
    }

    private <T> T clientValue(Supplier<T> supplier, T fallback) {
        if (supplier == null) {
            return fallback;
        }
        try {
            T value = Microbot.getClientThread().invoke(supplier);
            return value == null ? fallback : value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
