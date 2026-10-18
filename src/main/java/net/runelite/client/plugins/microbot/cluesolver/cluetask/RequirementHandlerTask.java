package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class RequirementHandlerTask extends ClueTask {
    private final List<ItemRequirement> requirements;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private Iterator<ItemRequirement> requirementIterator;
    private volatile CompletableFuture<Boolean> itemReceivedFuture;
    private volatile ItemRequirement currentRequirement;
    private volatile BankLocation targetBank;
    private final AtomicBoolean bankStepRunning = new AtomicBoolean(false);

    @Inject
    private ClientThread clientThread;

    public RequirementHandlerTask(Client client, List<ItemRequirement> requirements, EventBus eventBus, ClueScrollPlugin plugin, ClueSolverPlugin clueSolverPlugin, ExecutorService backgroundExecutor) {
        super(client, plugin, clueSolverPlugin);
        this.eventBus = eventBus;
        this.requirements = requirements;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() {
        eventBus.register(this);
        log.info("Checking for missing items.");
        runBankStep(this::checkForMissingItems);
        return true;
    }

    private void runBankStep(Runnable step) {
        if (!bankStepRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            backgroundExecutor.submit(() -> {
                try {
                    step.run();
                } catch (Exception e) {
                    log.warn("Requirement bank step failed: {}", e.getMessage(), e);
                    completeTask(false);
                } finally {
                    bankStepRunning.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            bankStepRunning.set(false);
            completeTask(false);
        }
    }

    private void checkForMissingItems() {
        List<ItemRequirement> missingItems = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> getMissingItems(requirements))
                .orElse(null);
        if (missingItems == null) {
            log.warn("Unable to read inventory and equipment for item requirements.");
            completeTask(false);
        } else if (missingItems.isEmpty()) {
            completeTask(true);
        } else {
            fetchMissingItemsFromBank(missingItems);
        }
    }

    private String getItemName(ItemRequirement requirement) {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> requirement.getCollectiveName(client))
                .orElse("N/A");
    }

    private List<ItemRequirement> getMissingItems(List<ItemRequirement> requirements) {
        Item[] inventoryItems = client.getItemContainer(InventoryID.INVENTORY) != null ? client.getItemContainer(InventoryID.INVENTORY).getItems() : new Item[0];
        Item[] equippedItems = client.getItemContainer(InventoryID.EQUIPMENT) != null ? client.getItemContainer(InventoryID.EQUIPMENT).getItems() : new Item[0];

        List<Item> allItems = new CopyOnWriteArrayList<>();
        Collections.addAll(allItems, inventoryItems);
        Collections.addAll(allItems, equippedItems);

        return requirements.stream()
                .filter(req -> !req.fulfilledBy(allItems.toArray(new Item[0])))
                .collect(Collectors.toList());
    }

    private void fetchMissingItemsFromBank(List<ItemRequirement> missingItems) {
        this.requirementIterator = missingItems.iterator();
        log.info("Missing items: {}", missingItems.stream().map(this::getItemName).collect(Collectors.joining(", ")));

        if (requirementIterator == null || !requirementIterator.hasNext()) {
            completeTask(true);
            return;
        }
        walkToBank();
    }

    private void walkToBank() {
        if (Rs2Bank.isOpen()) {
            fetchNextItem();
            return;
        }
        // v1.0.4 fix: read player location via the client-thread-safe helper. walkToBank() may
        // be called from off-client-thread code paths (executeTask flow). Direct
        // client.getLocalPlayer().getWorldLocation() throws IllegalStateException, which
        // cascaded into "Failed to open the bank" + walker deadlock.
        WorldPoint playerLocation = getPlayerLocationSafe();
        if (playerLocation == null) {
            log.warn("Player location unavailable in walkToBank.");
            completeTask(false);
            return;
        }
        BankLocation nearestBank = Rs2Bank.getNearestBank();
        if (nearestBank == null) {
            log.warn("No reachable bank found.");
            completeTask(false);
            return;
        }
        targetBank = nearestBank;
        if (playerLocation.distanceTo(nearestBank.getWorldPoint()) > 10) {
            if (!Rs2Bank.walkToBankAndUseBank()) {
                log.warn("Failed to walk to the bank.");
                completeTask(false);
            }
        } else {
            openBank();
        }
    }

    private void openBank() {
        if (!Rs2Bank.openBank()) {
            log.warn("Failed to open the bank.");
            completeTask(false);
        }
    }

    private void fetchNextItem() {
        if (requirementIterator == null || !requirementIterator.hasNext()) {
            Rs2Bank.closeBank();
            completeTask(true);
            return;
        }

        currentRequirement = requirementIterator.next();
        String itemName = getItemName(currentRequirement);

        if (!Rs2Bank.hasItem(itemName)) {
            log.warn("Item {} not found in bank.", itemName);
            Rs2Bank.closeBank();
            completeTask(false);
            return;
        }

        itemReceivedFuture = new CompletableFuture<>();
        Rs2Bank.withdrawAndEquip(itemName);

        backgroundExecutor.submit(() -> {
            try {
                boolean received = itemReceivedFuture.get(5, TimeUnit.SECONDS);

                if (received) {
                    Microbot.log("Item " + itemName + " received: " + received);
                    currentRequirement = null;

                } else {
                    log.warn("Item {} not received in time.", itemName);
                    Rs2Bank.closeBank();
                    completeTask(false);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("Error waiting for item {}: {}", itemName, e.getMessage());
                Rs2Bank.closeBank();
                completeTask(false);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (bankStepRunning.get()) {
            return;
        }
        if (Rs2Bank.isOpen() && currentRequirement == null) {
            runBankStep(this::fetchNextItem);
            return;
        }
        // v1.0.4 fix: defensive client-thread-safe read. onGameTick is @Subscribe-annotated so
        // it normally runs on the client thread, but using the helper is harmless and consistent.
        WorldPoint playerLocation = getPlayerLocationSafe();
        BankLocation bank = targetBank;
        if (playerLocation == null || bank == null) return;
        if (!Rs2Bank.isOpen() && playerLocation.distanceTo(bank.getWorldPoint()) <= 5) {
            runBankStep(this::openBank);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId() && currentRequirement != null && itemReceivedFuture != null && !itemReceivedFuture.isDone()) {
            if (currentRequirement.fulfilledBy(event.getItemContainer().getItems())) {
                itemReceivedFuture.complete(true);
            }
        }
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Requirement handling task completed with status: {}", success ? "Success" : "Failure");
    }
}
