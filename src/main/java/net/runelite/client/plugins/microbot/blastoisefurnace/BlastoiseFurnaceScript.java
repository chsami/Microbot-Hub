package net.runelite.client.plugins.microbot.blastoisefurnace;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.ObjectID.*;
import static net.runelite.api.gameval.VarbitID.*;

@Slf4j
public class BlastoiseFurnaceScript extends Script {
    private static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");
    static final int[] COAL_BAGS = {ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN};
    static final Integer[] KEEP_ITEMS = {ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE};
    private static final int MAX_ORE_PER_INTERACTION = 27;
    private static final int MAX_ORE_PER_HYBRID_INTERACTION = 26;
    private static final int MAX_TAKE_ATTEMPTS = 3;
    static final int CONVEYOR_POLL_MS = 300;
    static final int CONVEYOR_IDLE_TIMEOUT_MS = 5_000;
    static final int CONVEYOR_MAX_WALK_MS = 30_000;
    static final int MAX_FAILED_LOOT_CYCLES = 3;
    static final int DISPENSER_READY_POLL_MS = 3_000;
    static final int DISPENSER_READY_TIMEOUT_MS = 120_000;
    static final int CONVEYOR_PROMPT_TIMEOUT_MS = 5_000;
    static final int MAX_FAILED_BELT_TRIPS = 3;
    private static final String FOREMAN_PERMISSION_TEXT = "You must ask the foreman's";
    private static final int[] DISPENSER_BAR_VARBITS = {
            BLAST_FURNACE_IRON_BARS,
            BLAST_FURNACE_STEEL_BARS,
            BLAST_FURNACE_GOLD_BARS,
            BLAST_FURNACE_MITHRIL_BARS,
            BLAST_FURNACE_ADAMANTITE_BARS,
            BLAST_FURNACE_RUNITE_BARS
    };
    public static State state = State.BANKING;
    private int failedLootCycles = 0;
    private long dispenserWaitMs = 0;
    private int failedBeltTrips = 0;
    private String lastBeltPrompt;
    private String lastBeltQuestion;
    private String lastBeltOptions;
    private String lastBeltAnswer;
    private String lastBeltUnclicked;
    static boolean coalBagEmpty;
    static boolean primaryOreEmpty;
    static boolean secondaryOreEmpty;
    private boolean timerStarted = false;
    private volatile boolean timeIsUp;
    private boolean init = false;

    private final BlastoiseFurnacePlugin plugin;
    private final BlastoiseFurnaceConfig config;

    private boolean hasRequiredOresForSmithing() {
        int primaryOre = config.getBars().getPrimaryOre();
        int secondaryOre = config.getBars().getSecondaryOre() == null ? -1 : config.getBars().getSecondaryOre();
        boolean hasPrimaryOre = Rs2Bank.hasItem(primaryOre);
        boolean hasSecondaryOre = secondaryOre != -1 && Rs2Bank.hasItem(secondaryOre);
        return hasPrimaryOre && hasSecondaryOre;
    }

    @Inject
    public BlastoiseFurnaceScript(BlastoiseFurnacePlugin plugin, BlastoiseFurnaceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        state = State.BANKING;
        failedLootCycles = 0;
        dispenserWaitMs = 0;
        failedBeltTrips = 0;
        clearBeltPrompt();
        primaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
        secondaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getSecondaryOre());
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (!objectExists(BLAST_FURNACE_DISPENSER)) {
                    if (Rs2Player.isAnimating()) {
                        return;
                    }
                    Rs2Walker.walkTo(new WorldPoint(2931, 10197, 0));
                    Microbot.getRs2TileObjectCache().query().interact(DWARF_KELDAGRIM_FACTORY_STAIRS);
                    return;
                }

                if (!init) {
                    int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
                    int req = evaluateCofferDeposit();
                    if (inCoffer >= req) {
                        init = true;
                        return;
                    }
                    checkAndTopOffCoffer();
                    init = true;
                    return;
                }

                if (!fullCoffer()) {
                    checkAndTopOffCoffer();
                }

                boolean hasGauntlets;
                switch (state) {
                    case BANKING:
                        Microbot.status = "Banking";
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                            sleepUntil(Rs2Bank::isOpen, 20000);
                        }

                        if (config.getBars().isRequiresCoalBag() && !ensureCoalBag()) {
                            return;
                        }

                        if (config.getBars().isRequiresGoldsmithGloves()) {
                            hasGauntlets = Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING) || Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING);
                            if (!hasGauntlets) {
                                if (!Rs2Bank.hasItem(GAUNTLETS_OF_GOLDSMITHING)) {
                                    Microbot.showMessage("No goldsmith gauntlets found.");
                                    Microbot.stopPlugin(plugin);
                                    return;
                                }

                                Rs2Bank.withdrawItem(GAUNTLETS_OF_GOLDSMITHING);
                            }
                        } else Rs2Bank.depositAll(GAUNTLETS_OF_GOLDSMITHING);

                        if (Rs2Inventory.hasItem("bar")) {
                            Rs2Bank.depositAllExcept(KEEP_ITEMS);
                            if (Rs2Inventory.isFull()) {
                                // Wait so our player avoids drinking potions with a full inventory
                                if (!Rs2Inventory.waitForInventoryChanges(1800)) {
                                    sleepUntil(() -> !Rs2Inventory.isFull(), 1200);
                                }
                            }
                        }

                        if (!hasRequiredOresForSmithing()) {
                            log.warn("Out of ores. Walking you out for coffer safety");
                            Rs2Walker.walkTo(new WorldPoint(2930, 10196, 0));
                            Rs2Player.logout();
                            Microbot.stopPlugin(plugin);
                        }

                        if (Microbot.getClient().getEnergy() < 8100) {
                            useStaminaPotions();
                        }

                        // Check here if dispenser contains bars. If so we need to clean-up
                        if (dispenserContainsBars()) {
                            Rs2Bank.depositAllExcept(KEEP_ITEMS);
                            handleDispenserLooting();
                            return;
                        } else {
                            dispenserWaitMs = 0;
                            retrieveItemsForCurrentFurnaceInteraction();
                            state = State.SMITHING;
                        }
                        break;
                    case SMITHING:
                        log.info("clicking conveyor");
                        if (barsInDispenser(config.getBars()) > 0 && !handleDispenserLooting()) {
                            return;
                        }

                        state = State.BANKING;
                        break;
                }
            } catch (Exception ex) {
                log.trace("Error in main loop: {} - ", ex.getMessage(), ex);
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    boolean ensureCoalBag() {
        if (Rs2Inventory.contains(COAL_BAGS)) {
            return true;
        }

        int bankedBag = Arrays.stream(COAL_BAGS).filter(Rs2Bank::hasItem).findFirst().orElse(-1);
        if (bankedBag == -1) {
            Microbot.showMessage("No coal bag found in inventory and bank.");
            Microbot.stopPlugin(plugin);
            return false;
        }

        Rs2Bank.withdrawItem(bankedBag);
        return true;
    }

    private void handleTax() {
        log.info("Paying noob smithing tax");
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
        }
        Rs2Bank.depositOne(config.getBars().getPrimaryOre());
        sleep(500, 1200);
        Rs2Bank.depositOne(COAL);
        sleep(500, 1200);
        Rs2Bank.withdrawX(COINS, 2500);
        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        var blastie = Microbot.getRs2NpcCache().query().withName("Blast Furnace Foreman").nearestOnClientThread();
        if (blastie != null) blastie.click("Pay");
        sleepUntil(Rs2Dialogue::isInDialogue, 10000);
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Yes");
            sleep(1000, 1850);
            Rs2Dialogue.clickContinue();
            sleepUntil(()-> !Rs2Dialogue.isInDialogue(), Rs2Random.between(750,1500));
            if(!Rs2Dialogue.isInDialogue()){
                setTenMinuteTimer();
            }
        }
    }

    private boolean handleDispenserLooting() {
        if (failedLootCycles > 0) {
            Microbot.status = "Couldn't take bars, retrying";
        } else {
            Microbot.status = dispenserWaitMs > 0 ? "Waiting for the Bar dispenser" : "Taking bars from dispenser";
        }
        if (!Rs2Inventory.isFull()) {
            if (!dispenserContainsBars()) {
                sleepUntil(this::dispenserContainsBars, Rs2Random.between(3000, 5000));
            }

            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = Rs2Inventory.interact(ICE_GLOVES, "Wear")
                        || Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return false;
                }
            }

            if (!super.run()) {
                return false;
            }

            if (!awaitDispenserTake()) {
                return false;
            }

            if (!takeBarsFromDispenser()) {
                recordFailedLootCycle();
                return false;
            }
            failedLootCycles = 0;

            Rs2Bank.openBank();
            equipGoldSmithGauntlets();
        }

        state = State.BANKING;
        return true;
    }

    boolean awaitDispenserTake() {
        long started = System.currentTimeMillis();
        if (isTakePromptOpen() || sleepUntil(this::dispenserOffersTake, DISPENSER_READY_POLL_MS)) {
            dispenserWaitMs = 0;
            return true;
        }
        recordDispenserNotReady(System.currentTimeMillis() - started);
        return false;
    }

    boolean recordDispenserNotReady(long waitedMs) {
        dispenserWaitMs += waitedMs;
        if (dispenserWaitMs >= DISPENSER_READY_TIMEOUT_MS) {
            log.warn("Bar dispenser did not offer Take for {} s, stopping ({})", dispenserWaitMs / 1000, dispenserDiagnostics());
            dispenserWaitMs = 0;
            Microbot.status = "Bar dispenser not ready, stopped";
            Microbot.showMessage("The Bar dispenser holds bars but did not offer Take for " + DISPENSER_READY_TIMEOUT_MS / 1000 + " seconds. Take them manually, then start the script again.");
            Rs2Player.logout();
            Microbot.stopPlugin(plugin);
            return true;
        }
        log.info("Bar dispenser does not offer Take yet, waited {} s of {} s ({})", dispenserWaitMs / 1000, DISPENSER_READY_TIMEOUT_MS / 1000, dispenserDiagnostics());
        Microbot.status = "Waiting for the Bar dispenser";
        return false;
    }

    String dispenserDiagnostics() {
        Rs2TileObjectModel dispenser = Microbot.getRs2TileObjectCache().query().withId(BLAST_FURNACE_DISPENSER).first();
        ObjectComposition composition = dispenser == null ? null : dispenser.getObjectComposition();
        return String.format("dispenser state varbit %d, bar varbits %s, dispenser %s, actions %s, player tile %s",
                Microbot.getVarbitValue(BLAST_FURNACE_BARS_HOT),
                Arrays.toString(Arrays.stream(DISPENSER_BAR_VARBITS).map(Microbot::getVarbitValue).toArray()),
                dispenser == null ? "not found" : "found",
                composition == null ? "unknown" : Arrays.toString(composition.getActions()),
                Rs2Player.getWorldLocation());
    }

    boolean recordFailedLootCycle() {
        failedLootCycles++;
        if (failedLootCycles >= MAX_FAILED_LOOT_CYCLES) {
            log.warn("Couldn't take bars from the dispenser (try {} of {}), stopping ({})", failedLootCycles, MAX_FAILED_LOOT_CYCLES, dispenserDiagnostics());
            Microbot.status = "Couldn't take bars, stopped";
            Microbot.showMessage("Couldn't take bars from the Bar dispenser after several tries. Take them manually, then start the script again.");
            Rs2Player.logout();
            Microbot.stopPlugin(plugin);
            return true;
        }
        log.warn("Couldn't take bars from the dispenser (try {} of {}), retrying ({})", failedLootCycles, MAX_FAILED_LOOT_CYCLES, dispenserDiagnostics());
        Microbot.status = "Couldn't take bars, retrying";
        return false;
    }

    boolean takeBarsFromDispenser() {
        int barsBefore = Rs2Inventory.count("bar");
        for (int attempt = 0; attempt < MAX_TAKE_ATTEMPTS; attempt++) {
            if (!isTakePromptOpen()) {
                if (!sleepUntil(this::dispenserOffersTake, 3000)) {
                    continue;
                }
                if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_DISPENSER, "Take")) {
                    continue;
                }
                if (!sleepUntil(this::isTakePromptOpen, 5000)) {
                    continue;
                }
            }

            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            if (sleepUntil(() -> Rs2Inventory.count("bar") > barsBefore, 3000)) {
                return true;
            }
        }
        return Rs2Inventory.count("bar") > barsBefore;
    }

    private boolean isTakePromptOpen() {
        return Rs2Widget.hasWidget("What would you like to take?") || Rs2Widget.hasWidget("How many would you like");
    }

    private boolean dispenserOffersTake() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(BLAST_FURNACE_DISPENSER)
                .withAction("Take")
                .first() != null;
    }

    private void retrievePrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            Rs2Bank.withdrawAll(ore);
            return;
        }
        doOreRun(false, false);
    }

    private void retrieveDoubleCoal() {
        if (!Rs2Inventory.hasItem(COAL)) {
            Rs2Bank.withdrawAll(COAL);
            return;
        }
        if (!Rs2Inventory.interact(COAL_BAGS, "Fill"))
            return;
        depositOre();
    }

    private void retrieveCoalAndPrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            Rs2Bank.withdrawAll(ore);
            sleep(500, 1200);
            return;
        }
        if (!Rs2Inventory.interact(COAL_BAGS, "Fill"))
            return;

        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        if (depositOre()) {
            doOreRun(false, false);
        }
    }

    private void retrieveCoalAndGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        if (!Rs2Inventory.interact(COAL_BAGS, "Fill"))
            return;

        sleep(500, 1200);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        if (depositOre()) {
            doOreRun(true, true);
        }
    }

    private void retrieveGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        if (depositOre()) {
            doOreRun(true, true);
        }
    }

    private void doOreRun(boolean useIceGloves, boolean waitInventoryChange) {
        Rs2Walker.walkFastCanvas(new WorldPoint(1940, 4962, 0));
        sleep(3400);
        sleepUntil(() -> barsInDispenser(config.getBars()) > 0, 10000);

        if (useIceGloves) {
            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = Rs2Inventory.interact(ICE_GLOVES, "Wear")
                        || Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }
            }
            if (waitInventoryChange)
                Rs2Inventory.waitForInventoryChanges(2000);
        } else {
            sleep(400, 700);
        }
    }

    private void retrieveItemsForCurrentFurnaceInteraction() {
        final Bars bar = config.getBars();
        final int coal = Microbot.getVarbitValue(
                bar == Bars.GOLD_BAR ? BLAST_FURNACE_GOLD_ORE : BLAST_FURNACE_COAL
        );
        final int divisor = isHybrid(bar) ? MAX_ORE_PER_HYBRID_INTERACTION : MAX_ORE_PER_INTERACTION;
        final int batch = coal / divisor;

        switch (bar) {
            case GOLD_BAR:
                retrieveGold();
                break;

            case STEEL_BAR:
            case MITHRIL_BAR:
                dispatchStandard(batch, 0, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case ADAMANTITE_BAR:
            case RUNITE_BAR:
                dispatchStandard(batch, 2, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case HYBRID_MITHRIL_BAR:
                dispatchHybrid(batch, 0, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_ADAMANTITE_BAR:
                dispatchHybrid(batch, 2, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_RUNITE_BAR:
                dispatchHybrid(batch, 3, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            default:
                assert false : "unhandled bar type: " + bar;
        }
    }

    private boolean isHybrid(Bars bar) {
        return bar.name().startsWith("HYBRID");
    }

    private final Runnable retrievePrimary = this::retrievePrimary;
    private final Runnable retrieveCoalAndPrimary = this::retrieveCoalAndPrimary;
    private final Runnable retrieveCoalAndGold = this::retrieveCoalAndGold;
    private final Runnable retrieveDoubleCoal = this::retrieveDoubleCoal;

    private void dispatchStandard(int batch, int doubleCoalMax, Runnable doubleCoal, Runnable coalAndPrimary, Runnable primary) {
        if (batch <= doubleCoalMax) {
            doubleCoal.run();
        } else if (batch <= 6) {
            coalAndPrimary.run();
        } else {
            primary.run();
        }
    }

    private void dispatchHybrid(int batch, int goldThreshold, Runnable coalAndGold, Runnable coalAndPrimary) {
        if (batch <= goldThreshold) {
            coalAndGold.run();
        } else {
            coalAndPrimary.run();
        }
    }

    private void useStaminaPotions() {
        if (!Rs2Bank.isOpen()) return;

        boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
        boolean hasEnergyPotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());


        if (!Rs2Player.hasStaminaBuffActive() && hasStaminaPotion) {
            String potionName = getLowestDosePotionName(List.of(Rs2Potion.getStaminaPotion()));
            if (potionName != null) {
                withdrawAndDrink(potionName);
                return;
            }
        }
        if (hasEnergyPotion) {
            String potionName = getLowestDosePotionName(Rs2Potion.getRestoreEnergyPotionsVariants());
            if (potionName != null) {
                withdrawAndDrink(potionName);
            }
        }
    }

    private String getLowestDosePotionName(List<String> variants) {
        return Rs2Bank.getAll(item -> variants.stream().anyMatch(variant -> item.getName().toLowerCase().contains(variant.toLowerCase())))
                .min(Comparator.comparingInt(item -> getDoseFromName(item.getName())))
                .map(Rs2ItemModel::getName)
                .orElse(null);
    }

    private int getDoseFromName(String potionItemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(potionItemName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }

    private String getBaseName(String itemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return itemName;
    }

    private void withdrawAndDrink(String potionItemName) {
        String baseName = getBaseName(potionItemName);
        boolean withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
        if (!withdrewPotion) {
            if (Rs2Inventory.isFull()) {
                // Wait a full tick for safety
                if (!Rs2Inventory.waitForInventoryChanges(600) && Rs2Inventory.isFull()) {
                    log.debug("Inventory remained full while attempting to withdraw {}", potionItemName);
                    return;
                }
                withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
            }
            if (!withdrewPotion) {
                log.debug("Failed to withdraw potion {} from the bank", potionItemName);
                return;
            }
        }
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Inventory.interact(potionItemName, "drink");
        Rs2Inventory.waitForInventoryChanges(1800);
        if (Rs2Inventory.hasItem(baseName)) {
            Rs2Bank.depositOne(baseName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }

    private int getInventoryOreCount() {
        return Rs2Inventory.count("ore") + Rs2Inventory.count("coal");
    }

    boolean putOreOnConveyorBelt() {
        final int oreCount = getInventoryOreCount();
        if (oreCount <= 0) {
            log.debug("No ore in Inventory");
            return false;
        }
        Microbot.status = "Putting ore on conveyor belt";
        if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")) {
            log.error("Failed to interact with conveyor belt");
            return false;
        }
        final AtomicInteger polls = new AtomicInteger();
        sleepUntilTrue(() -> Rs2Dialogue.isInDialogue() || getInventoryOreCount() < oreCount,
                () -> polls.incrementAndGet() < CONVEYOR_MAX_WALK_MS / CONVEYOR_POLL_MS && Rs2Player.isMoving(),
                CONVEYOR_POLL_MS, CONVEYOR_IDLE_TIMEOUT_MS);
        if (Rs2Dialogue.hasSelectAnOption() && answerConveyorPrompt()) {
            sleepUntil(() -> getInventoryOreCount() < oreCount || Rs2Widget.hasWidget(FOREMAN_PERMISSION_TEXT), CONVEYOR_PROMPT_TIMEOUT_MS);
        }
        if (Rs2Widget.hasWidget(FOREMAN_PERMISSION_TEXT)) {
            log.info("Need to pay the noob tax");
            if(timerStarted && !timeIsUp){
                return putOreOnConveyorBelt();
            }

            handleTax();
            return putOreOnConveyorBelt();
        }
        return getInventoryOreCount() < oreCount;
    }

    boolean answerConveyorPrompt() {
        final String rawQuestion = Rs2Dialogue.getQuestion();
        final List<String> rawOptions = Rs2Dialogue.getDialogueOptions().stream().map(Widget::getText).collect(Collectors.toList());
        final List<String> options = rawOptions.stream().map(BlastoiseFurnaceScript::cleanPromptText).collect(Collectors.toList());
        final String question = cleanPromptText(rawQuestion);
        final int chosenIndex = IntStream.range(0, options.size())
                .filter(index -> options.get(index).toLowerCase().contains("yes")).findFirst().orElse(-1);
        clearBeltPrompt();
        lastBeltPrompt = String.format("\"%s\" with options %s", rawQuestion, rawOptions);
        lastBeltQuestion = question;
        lastBeltOptions = String.join(", ", options);
        if (chosenIndex < 0) {
            log.warn("Conveyor belt asked {} and there is no Yes option to choose, leaving it unanswered", lastBeltPrompt);
            return false;
        }
        final String chosen = options.get(chosenIndex);
        if (!Rs2Dialogue.keyPressForDialogueOption(chosenIndex + 1)) {
            lastBeltUnclicked = chosen;
            log.warn("Conveyor belt asked {} but the \"{}\" option could not be clicked", lastBeltPrompt, chosen);
            return false;
        }
        lastBeltAnswer = chosen;
        Microbot.log(question.isEmpty()
                ? "The conveyor belt showed a prompt. Chose \"" + chosen + "\"."
                : "The conveyor belt asked \"" + question + "\". Chose \"" + chosen + "\".");
        Microbot.status = "Answering the conveyor belt prompt";
        return true;
    }

    static String cleanPromptText(String text) {
        return text == null ? "" : Text.removeTags(text.replace("<br>", " ")).replace(' ', ' ').trim();
    }

    private void clearBeltPrompt() {
        lastBeltPrompt = null;
        lastBeltQuestion = null;
        lastBeltOptions = null;
        lastBeltAnswer = null;
        lastBeltUnclicked = null;
    }

    static String beltStopMessage(boolean promptSeen, String question, String options, String answer, String unclicked) {
        final String start = "Ore did not go onto the conveyor belt " + MAX_FAILED_BELT_TRIPS + " trips in a row.";
        if (!promptSeen) {
            return start + " Put the ore on the belt by hand, then start the script again.";
        }
        final boolean hasQuestion = question != null && !question.isEmpty();
        final boolean hasOptions = options != null && !options.isEmpty();
        final String asked = (hasQuestion ? "The belt asked \"" + question + "\"" : "The belt showed a prompt")
                + (answer == null && hasOptions ? " (" + options + ")" : "");
        final String outcome;
        if (answer != null) {
            outcome = " and the script answered \"" + answer + "\", but the ore stayed in your inventory.";
        } else if (unclicked != null) {
            outcome = " but the script could not click \"" + unclicked + "\".";
        } else {
            outcome = " and the script does not know which answer to pick.";
        }
        return start + "\n" + asked + outcome
                + "\nPut the ore on the belt by hand and start the script again."
                + "\nIf it stops here again, report it on the Microbot Discord and include the log.";
    }

    boolean recordFailedBeltTrip() {
        failedBeltTrips++;
        if (failedBeltTrips < MAX_FAILED_BELT_TRIPS) {
            return false;
        }
        log.warn("Ore did not go onto the conveyor belt {} trips in a row, stopping (last prompt: {}, answered: {}, could not click: {})", failedBeltTrips, lastBeltPrompt, lastBeltAnswer, lastBeltUnclicked);
        failedBeltTrips = 0;
        Microbot.status = "Ore didn't go on the belt, stopped";
        Rs2Player.logout();
        Microbot.showMessage(beltStopMessage(lastBeltPrompt != null, lastBeltQuestion, lastBeltOptions, lastBeltAnswer, lastBeltUnclicked));
        Microbot.stopPlugin(plugin);
        return true;
    }

    public void setTenMinuteTimer(){
        if(timerStarted) return;

        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Microbot.log("Time's up! 10 minutes have passed.");
                timeIsUp = true;
                timerStarted = false;
                timer.cancel();
            }
        };

        long delay = 10 * 60 * 1000; // 10 minutes in ms
        Microbot.log("Timer started for 10 minutes...");

        timer.schedule(task, delay);

        timerStarted = true;
    }

    boolean depositOre() {
        final boolean usesCoalBag = config.getBars().isRequiresCoalBag();
        clearBeltPrompt();
        if (!putOreOnConveyorBelt()) {
            if (getInventoryOreCount() > 0 && recordFailedBeltTrip()) {
                return false;
            }
            log.warn(usesCoalBag
                    ? "Ore did not go onto the conveyor belt. Leaving the coal bag full and trying again next trip."
                    : "Ore did not go onto the conveyor belt. Trying again next trip.");
            if (lastBeltUnclicked != null) {
                Microbot.status = "Couldn't click the belt prompt answer, retrying";
            } else {
                Microbot.status = lastBeltPrompt != null && lastBeltAnswer == null
                        ? "Belt asked a question the script can't answer, retrying"
                        : "Ore didn't go on the belt, retrying";
            }
            return false;
        }
        failedBeltTrips = 0;
        if (usesCoalBag) {
            if (!Rs2Inventory.interact(COAL_BAGS, "Empty") || !Rs2Inventory.waitForInventoryChanges(3_000)) {
                log.warn("Coal bag did not empty. Trying again next trip.");
                return true;
            }
            if (!putOreOnConveyorBelt() && getInventoryOreCount() > 0 && !putOreOnConveyorBelt()) {
                log.warn("Coal from the coal bag did not go onto the conveyor belt, stopping");
                Microbot.status = "Coal didn't go on the belt, stopped";
                Microbot.showMessage("Coal from the coal bag did not go onto the conveyor belt. Put it on the belt or bank it, then start the script again.");
                Rs2Player.logout();
                Microbot.stopPlugin(plugin);
                return false;
            }
        }
        if (usesCoalBag &&
                (Rs2Inventory.hasItem(SMITHING_UNIFORM_GLOVES_ICE)
                        || Rs2Inventory.hasItem(GAUNTLETS_OF_GOLDSMITHING)
                        || Rs2Inventory.hasItem(ICE_GLOVES))) {
            if (!Rs2Inventory.interact(COAL_BAGS, "Empty")) {
                log.warn("Coal bag did not empty a second time. Trying again next trip.");
            } else if (Rs2Inventory.waitForInventoryChanges(3_000)) {
                putOreOnConveyorBelt();
            }
        }
        return true;
    }


    public int barsInDispenser(Bars bar) {
        switch (bar) {
            case GOLD_BAR:
                return getBars(BLAST_FURNACE_GOLD_BARS);
            case STEEL_BAR:
                return getBars(BLAST_FURNACE_STEEL_BARS);
            case MITHRIL_BAR:
                return getBars(BLAST_FURNACE_MITHRIL_BARS);
            case ADAMANTITE_BAR:
                return getBars(BLAST_FURNACE_ADAMANTITE_BARS);
            case RUNITE_BAR:
                return getBars(BLAST_FURNACE_RUNITE_BARS);
            case HYBRID_MITHRIL_BAR:
                return fallbackBar(
                        BLAST_FURNACE_MITHRIL_BARS
                );
            case HYBRID_ADAMANTITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_ADAMANTITE_BARS
                );
            case HYBRID_RUNITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_RUNITE_BARS
                );
            default:
                return -1;
        }
    }

    private int fallbackBar(int primary) {
        int value = Microbot.getVarbitValue(primary);
        return value > 0 ? value : Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.BLAST_FURNACE_GOLD_BARS);
    }

    private int getBars(int varbitId) {
        return Microbot.getVarbitValue(varbitId);
    }

    public boolean dispenserContainsBars() {
        return Arrays.stream(DISPENSER_BAR_VARBITS).anyMatch(id -> Microbot.getVarbitValue(id) > 0);
    }

    private void equipGoldSmithGauntlets() {
        if (config.getBars().isRequiresGoldsmithGloves()) {
            Rs2Inventory.interact(GAUNTLETS_OF_GOLDSMITHING, "Wear");
        }
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.devDebug = true;
    }

    public void shutdown() {
        init = false;
        state = State.BANKING;
        failedLootCycles = 0;
        dispenserWaitMs = 0;
        failedBeltTrips = 0;
        clearBeltPrompt();
        primaryOreEmpty = false;
        secondaryOreEmpty = false;
        super.shutdown();
    }

    public int evaluateCofferDeposit() {
        if (BreakHandlerScript.breakIn > 0) {
            return BreakHandlerScript.breakIn * 20;
        }

        return 72000;
    }

    public void checkAndTopOffCoffer() {
        int lvl = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        if (lvl < 60) return;

        int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
        int required = evaluateCofferDeposit();
        int delta = required - inCoffer;

        if (delta == 0) return;

        boolean underfilled = delta > 0;
        int amount = Math.abs(delta);

        if (Rs2Inventory.isFull()) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                Rs2Bank.depositAllExcept(KEEP_ITEMS);
            }
        }

        if (underfilled) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
            }

            if (!Rs2Bank.hasBankItem(COINS, amount)) {
                Microbot.stopPlugin(plugin);
                return;
            }

            Rs2Bank.withdrawX(COINS, amount);
            sleep(600, 900);
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }

        Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_AUTOMATA_COFFER, "use");
        Rs2Player.waitForWalking(2400);

        if (underfilled && Rs2Dialogue.hasDialogueOption("deposit", false)) {
            Rs2Widget.clickWidget("deposit");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Deposit how much?"), 2400);
            Rs2Keyboard.typeString(String.valueOf(amount));
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
        } else if (!underfilled && Rs2Dialogue.hasDialogueOption("withdraw", false)) {
            Rs2Widget.clickWidget("withdraw");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Withdraw how much?"), 2400);
            Rs2Keyboard.typeString(String.valueOf(amount));
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                Rs2Bank.depositAllExcept(KEEP_ITEMS);
            }
        } else {
            log.warn("Unexpected coffer dialogue state. delta = {}", delta);
            Rs2Dialogue.clickContinue();
        }
    }

    public boolean fullCoffer() {
        int coffer = Microbot.getVarbitValue(BLAST_FURNACE_COINSINCOFFER);
        return coffer == 1;
    }

    private static boolean objectExists(int objectId) {
        return Microbot.getRs2TileObjectCache().query().fromWorldView().withId(objectId).first() != null;
    }
}
