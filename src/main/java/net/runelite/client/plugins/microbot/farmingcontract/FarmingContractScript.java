package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FarmingContractScript extends Script {

    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
        "(?:We need you to grow|Please could you grow) (?:some|a|an) ([a-zA-Z ]+)(?: for us\\?|\\.)"
    );
    private static final String CONFIG_GROUP = "farmingcontract";
    private static final String CONFIG_KEY_CONTRACT = "contract";

    @Getter
    private static String contractName;

    private FarmingContractConfig config;
    private Produce contract;
    private Phase phase;
    private boolean needsDowngrade;
    private CompostStep compostStep;

    enum Phase {
        COMPOSTING,
        GET_CONTRACT,
        BANKING,
        HANDLE_PATCH,
        TURN_IN,
        DONE
    }

    enum CompostStep {
        CHECK_BIN,
        DRAIN_BIN,
        COLLECT_TRIP_1,
        COLLECT_TRIP_2_AND_FILL,
        FILL_FINAL,
        FILL_TRIP_1,
        FILL_TRIP_2
    }

    enum PatchState {
        EMPTY, WEEDS, GROWING, GROWN_CHECK, HARVESTABLE, CHECKED_TREE, DEAD
    }

    @Inject
    public FarmingContractScript() {}

    public boolean run(FarmingContractConfig config) {
        this.config = config;
        this.needsDowngrade = false;
        this.contract = null;
        this.contractName = null;
        this.compostStep = CompostStep.CHECK_BIN;
        phase = config.enableComposting() ? Phase.COMPOSTING : Phase.GET_CONTRACT;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;

            try {
                if (Rs2Player.getWorldLocation().distanceTo(FarmingContractData.JANE_LOCATION) > 20) {
                    FarmingContractPlugin.setStatus("Walking to guild");
                    Rs2Walker.walkTo(FarmingContractData.JANE_LOCATION);
                    return;
                }

                FarmingContractPlugin.setStatus(phase.name());

                switch (phase) {
                    case COMPOSTING:
                        if (handleComposting()) {
                            phase = Phase.GET_CONTRACT;
                        }
                        break;
                    case GET_CONTRACT:
                        if (handleGetContract()) {
                            phase = Phase.BANKING;
                        }
                        break;
                    case BANKING:
                        if (handleBanking()) {
                            phase = Phase.HANDLE_PATCH;
                        }
                        break;
                    case HANDLE_PATCH:
                        Phase next = handlePatch();
                        if (next != null) {
                            phase = next;
                        }
                        break;
                    case TURN_IN:
                        if (handleTurnIn()) {
                            phase = contract != null ? Phase.BANKING : Phase.GET_CONTRACT;
                        }
                        break;
                    case DONE:
                        FarmingContractPlugin.setStatus("Done");
                        Microbot.stopPlugin(Microbot.getPluginManager()
                            .getPlugins().stream()
                            .filter(p -> p instanceof FarmingContractPlugin)
                            .findFirst().orElse(null));
                        break;
                }
            } catch (Exception e) {
                log.error("Error in farming contract loop", e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    // --- Contract Management ---

    private void saveContract() {
        if (contract != null) {
            Microbot.getConfigManager().setRSProfileConfiguration(
                CONFIG_GROUP, CONFIG_KEY_CONTRACT, String.valueOf(contract.getItemID()));
            contractName = contract.getName();
        } else {
            Microbot.getConfigManager().unsetRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_CONTRACT);
            contractName = null;
        }
    }

    private Produce findProduceByContractName(String name) {
        for (Produce p : Produce.values()) {
            if (p.getContractName() != null && p.getContractName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }


    // --- Phase: COMPOSTING ---

    private boolean handleComposting() {
        FarmingContractPlugin.setStatus("Composting: " + compostStep.name());

        switch (compostStep) {
            case CHECK_BIN:
                return checkBinState();
            case DRAIN_BIN:
                return drainBin();
            case COLLECT_TRIP_1:
                return collectTrip1();
            case COLLECT_TRIP_2_AND_FILL:
                return collectTrip2AndFill();
            case FILL_FINAL:
                return fillFinal();
            case FILL_TRIP_1:
                return fillTrip1();
            case FILL_TRIP_2:
                return fillTrip2();
            default:
                return true;
        }
    }

    private boolean checkBinState() {
        TileObject bin = findCompostBin();
        if (bin == null) {
            log.info("Compost bin not found, walking to guild");
            Rs2Walker.walkTo(FarmingContractData.JANE_LOCATION);
            sleepUntil(() -> !Rs2Player.isMoving(), 15000);
            return false;
        }

        if (binHasAction(bin, "Take")) {
            log.info("Bin is already open with compost, banking for ash + buckets");
            compostStep = CompostStep.COLLECT_TRIP_1;
            return false;
        }

        if (binHasAction(bin, "Close")) {
            log.info("Bin is open and empty, filling");
            compostStep = CompostStep.FILL_TRIP_1;
            return false;
        }

        if (!binHasAction(bin, "Open")) {
            log.info("Bin is empty (no actions), filling");
            compostStep = CompostStep.FILL_TRIP_1;
            return false;
        }

        log.info("Bin is closed, banking for supplies then trying to open");
        compostStep = CompostStep.COLLECT_TRIP_1;
        return false;
    }

    private boolean binHasAction(TileObject bin, String action) {
        return Rs2GameObject.hasAction(bin, action, true);
    }

    private boolean drainBin() {
        FarmingContractPlugin.setStatus("Compost: draining bin");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);

        if (!Rs2Bank.hasBankItem(FarmingContractData.BUCKET_EMPTY, 1)) {
            log.warn("No empty buckets, skipping composting");
            Rs2Bank.closeBank();
            return true;
        }

        Rs2Bank.withdrawX(FarmingContractData.BUCKET_EMPTY, 28);
        sleep(600, 900);


        TileObject bin = walkToBin();
        if (bin == null || !super.run()) return false;

        Rs2GameObject.interact(bin, "Take");
        sleepUntil(() -> {
            if (!Rs2Inventory.contains(FarmingContractData.BUCKET_EMPTY)) return true;
            TileObject b = findCompostBin();
            return b == null || !binHasAction(b, "Take");
        }, 60000);
        sleep(600, 1200);

        bin = findCompostBin();
        if (bin == null) return false;

        if (binHasAction(bin, "Take")) {
            log.info("Bin still has compost, draining again");
            return false;
        }

        log.info("Bin fully drained, moving to fill");
        compostStep = CompostStep.FILL_TRIP_1;
        return false;
    }

    private boolean collectTrip1() {
        FarmingContractPlugin.setStatus("Compost: banking for ash + buckets");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);

        if (!Rs2Bank.hasBankItem(FarmingContractData.VOLCANIC_ASH, FarmingContractData.VOLCANIC_ASH_PER_BIN)) {
            log.warn("Not enough volcanic ash (need {}), skipping composting",
                FarmingContractData.VOLCANIC_ASH_PER_BIN);
            Rs2Bank.closeBank();
            return true;
        }
        if (!Rs2Bank.hasBankItem(FarmingContractData.BUCKET_EMPTY, 27)) {
            log.warn("Not enough empty buckets (need 27), skipping composting");
            Rs2Bank.closeBank();
            return true;
        }

        Rs2Bank.withdrawX(FarmingContractData.VOLCANIC_ASH, FarmingContractData.VOLCANIC_ASH_PER_BIN);
        sleep(600, 900);
        Rs2Bank.withdrawX(FarmingContractData.BUCKET_EMPTY, 27);
        sleep(600, 900);

        int bucketCount = Rs2Inventory.count(FarmingContractData.BUCKET_EMPTY);
        log.info("Withdrew {} empty buckets (expected 27), ash in inventory: {}",
            bucketCount, Rs2Inventory.contains(FarmingContractData.VOLCANIC_ASH));

        TileObject bin = walkToBin();
        if (bin == null) return false;

        if (!binHasAction(bin, "Take")) {
            Rs2GameObject.interact(bin, "Open");
            sleepUntil(() -> {
                if (Rs2Dialogue.isInDialogue()) return true;
                TileObject b = findCompostBin();
                if (b == null) return false;
                return binHasAction(b, "Take") || binHasAction(b, "Close");
            }, 10000);

            if (Rs2Dialogue.isInDialogue()) {
                log.info("Bin not ready yet (error dialogue), skipping composting");
                Rs2Dialogue.clickContinue();
                sleep(600, 900);
                return true;
            }

            bin = findCompostBin();
            if (bin != null && !binHasAction(bin, "Take") && binHasAction(bin, "Close")) {
                log.info("Bin opened but empty, switching to fill");
                compostStep = CompostStep.FILL_TRIP_1;
                return false;
            }
        }

        if (!super.run()) return false;

        bin = findCompostBin();
        if (bin == null) return false;
        Rs2Inventory.useItemOnObject(FarmingContractData.VOLCANIC_ASH, bin.getId());
        sleepUntil(() -> !Rs2Player.isMoving(), 10000);
        sleepUntil(() -> Rs2Dialogue.isInDialogue()
            || !Rs2Inventory.contains(FarmingContractData.VOLCANIC_ASH), 10000);

        if (Rs2Dialogue.isInDialogue()) {
            log.info("Bin already ashed, skipping to take");
            Rs2Dialogue.clickContinue();
            sleep(600, 900);
        }

        while (Rs2Inventory.contains(FarmingContractData.BUCKET_EMPTY) && super.run()) {
            bin = findCompostBin();
            if (bin == null || !binHasAction(bin, "Take")) break;
            Rs2GameObject.interact(bin, "Take");
            sleepUntil(() -> {
                if (!Rs2Inventory.contains(FarmingContractData.BUCKET_EMPTY)) return true;
                TileObject b = findCompostBin();
                return b == null || !binHasAction(b, "Take");
            }, 60000);
            sleep(600, 900);
        }

        bin = findCompostBin();
        if (bin != null && binHasAction(bin, "Take")) {
            compostStep = CompostStep.COLLECT_TRIP_2_AND_FILL;
        } else {
            compostStep = CompostStep.FILL_TRIP_1;
        }
        return false;
    }

    private boolean collectTrip2AndFill() {
        FarmingContractPlugin.setStatus("Compost: banking for buckets + pineapples");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);

        Rs2Bank.withdrawX(FarmingContractData.BUCKET_EMPTY, 3);
        sleep(600, 900);
        Rs2Bank.withdrawX(FarmingContractData.PINEAPPLE, 25);
        sleep(600, 900);


        TileObject bin = walkToBin();
        if (bin == null) return false;

        if (Rs2Inventory.contains(FarmingContractData.BUCKET_EMPTY)) {
            Rs2GameObject.interact(bin, "Take");
            sleepUntil(() -> {
                if (!Rs2Inventory.contains(FarmingContractData.BUCKET_EMPTY)) return true;
                TileObject b = findCompostBin();
                return b == null || !binHasAction(b, "Take");
            }, 30000);
            sleep(600, 900);
            if (!super.run()) return false;
        }

        bin = findCompostBin();
        if (bin == null) return false;
        if (!super.run()) return false;

        Rs2Inventory.useItemOnObject(FarmingContractData.PINEAPPLE, bin.getId());
        sleepUntil(() -> !Rs2Inventory.contains(FarmingContractData.PINEAPPLE), 60000);
        sleep(600, 1200);

        compostStep = CompostStep.FILL_FINAL;
        return false;
    }

    private boolean fillFinal() {
        FarmingContractPlugin.setStatus("Compost: final pineapple trip");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);
        Rs2Bank.withdrawX(FarmingContractData.PINEAPPLE, 5);
        sleep(600, 900);


        TileObject bin = walkToBin();
        if (bin == null || !super.run()) return false;

        Rs2Inventory.useItemOnObject(FarmingContractData.PINEAPPLE, bin.getId());
        sleepUntil(() -> !Rs2Inventory.contains(FarmingContractData.PINEAPPLE), 60000);
        sleep(600, 1200);

        bin = findCompostBin();
        if (bin == null) return false;
        Rs2GameObject.interact(bin, "Close");
        sleep(1200, 1800);

        log.info("Compost bin filled and closed");
        return true;
    }

    private boolean fillTrip1() {
        FarmingContractPlugin.setStatus("Compost: filling bin (trip 1)");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);

        if (!Rs2Bank.hasBankItem(FarmingContractData.PINEAPPLE, FarmingContractData.BIN_CAPACITY)) {
            log.warn("Not enough pineapples (need {}), skipping composting",
                FarmingContractData.BIN_CAPACITY);
            Rs2Bank.closeBank();
            return true;
        }

        Rs2Bank.withdrawX(FarmingContractData.PINEAPPLE, 28);
        sleep(600, 900);

        TileObject bin = walkToBin();
        if (bin == null || !super.run()) return false;

        Rs2Inventory.useItemOnObject(FarmingContractData.PINEAPPLE, bin.getId());
        sleepUntil(() -> !Rs2Inventory.contains(FarmingContractData.PINEAPPLE), 60000);
        sleep(600, 1200);

        compostStep = CompostStep.FILL_TRIP_2;
        return false;
    }

    private boolean fillTrip2() {
        FarmingContractPlugin.setStatus("Compost: filling bin (trip 2)");

        if (!openNearestBank()) return false;

        Rs2Bank.depositAll();
        sleep(600, 900);
        Rs2Bank.withdrawX(FarmingContractData.PINEAPPLE, 2);
        sleep(600, 900);


        TileObject bin = walkToBin();
        if (bin == null || !super.run()) return false;

        Rs2Inventory.useItemOnObject(FarmingContractData.PINEAPPLE, bin.getId());
        sleepUntil(() -> !Rs2Inventory.contains(FarmingContractData.PINEAPPLE), 30000);
        sleep(600, 1200);

        bin = findCompostBin();
        if (bin == null) return false;
        Rs2GameObject.interact(bin, "Close");
        sleep(1200, 1800);

        log.info("Compost bin filled and closed");
        return true;
    }

    private TileObject walkToBin() {
        try {
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            TileObject bin = findCompostBin();
            if (bin == null) {
                Rs2Walker.walkTo(FarmingContractData.BIN_LOCATION);
                sleepUntil(() -> !Rs2Player.isMoving(), 15000);
                sleep(600, 900);
                bin = findCompostBin();
            }
            return bin;
        } catch (RuntimeException e) {
            log.debug("walkToBin interrupted, will retry", e);
            return null;
        }
    }

    private TileObject findCompostBin() {
        try {
            for (GameObject obj : Rs2GameObject.getGameObjects(
                    o -> {
                        var name = Rs2GameObject.getCompositionName(o);
                        return name.isPresent() && name.get().equalsIgnoreCase("Big compost bin");
                    }, FarmingContractData.BIN_LOCATION, 5)) {
                return obj;
            }
        } catch (RuntimeException e) {
            log.debug("findCompostBin interrupted, will retry", e);
        }
        return null;
    }

    // --- Phase: GET_CONTRACT ---

    private boolean handleGetContract() {
        FarmingContractPlugin.setStatus(needsDowngrade ? "Downgrading contract" : "Getting contract");

        if (Rs2Npc.getNpc("Guildmaster Jane") == null) {
            Rs2Walker.walkTo(FarmingContractData.JANE_LOCATION);
            sleepUntil(() -> !Rs2Player.isMoving(), 15000);
            return false;
        }
        if (!Rs2Npc.interact("Guildmaster Jane", "Contract")
                && !Rs2Npc.interact("Guildmaster Jane", "Talk-to")) {
            return false;
        }
        sleepUntil(Rs2Dialogue::isInDialogue, 15000);
        if (!Rs2Dialogue.isInDialogue()) return false;

        int idleTicks = 0;
        while (idleTicks < 5 && super.run()) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                idleTicks = 0;
                String tier = getDesiredTier();
                if (Rs2Dialogue.hasDialogueOption(tier)) {
                    Rs2Dialogue.clickOption(tier);
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Yes please")) {
                    Rs2Dialogue.clickOption("Yes please");
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("I'd like a farming contract")) {
                    Rs2Dialogue.clickOption("I'd like a farming contract");
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Do you have any jobs for me?")) {
                    Rs2Dialogue.clickOption("Do you have any jobs for me?");
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Thank you")) {
                    if (shouldDowngradeContract()
                            && Rs2Dialogue.hasDialogueOption("Do you have anything easier?")) {
                        log.info("Auto-downgrading {} (type: {}), asking for easier",
                            contract != null ? contract.getName() : "unknown",
                            contract != null ? contract.getPatchImplementation() : "unknown");
                        contract = null;
                        Rs2Dialogue.clickOption("Do you have anything easier?");
                        sleep(600, 900);
                        continue;
                    }
                    needsDowngrade = false;
                    Rs2Dialogue.clickOption("Thank you");
                    sleep(600, 900);
                    continue;
                }
            }

            if (Rs2Dialogue.isInDialogue()) {
                idleTicks = 0;
                String text = Rs2Dialogue.getDialogueText();
                if (text != null) {
                    Matcher m = CONTRACT_PATTERN.matcher(text);
                    if (m.find()) {
                        String cropName = m.group(1).trim();
                        contract = findProduceByContractName(cropName);
                        if (contract != null) {
                            log.info("Got contract: {}", contract.getName());
                            saveContract();
                        }
                    }
                }
                Rs2Dialogue.clickContinue();
                sleep(600, 900);
                continue;
            }

            Rs2Dialogue.clickContinue();
            sleep(600, 900);
            idleTicks++;
        }

        return contract != null;
    }

    private boolean shouldDowngradeContract() {
        if (needsDowngrade) return true;
        if (contract == null) return false;
        PatchImplementation type = contract.getPatchImplementation();
        if (type == PatchImplementation.TREE && config.downgradeTree()) return true;
        if (type == PatchImplementation.FRUIT_TREE && config.downgradeFruitTree()) return true;
        return false;
    }

    private String getDesiredTier() {
        if (needsDowngrade) {
            String current = getCurrentTierForLevel();
            switch (current) {
                case "Hard": return "Medium";
                case "Medium": return "Easy";
                default: return "Easy";
            }
        }

        FarmingContractConfig.ContractTier tier = config.contractTier();
        if (tier == FarmingContractConfig.ContractTier.AUTO) {
            return getCurrentTierForLevel();
        }
        return tier.getLabel();
    }

    private String getCurrentTierForLevel() {
        int level = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        if (level >= 85) return "Hard";
        if (level >= 65) return "Medium";
        return "Easy";
    }

    // --- Phase: BANKING ---

    private boolean handleBanking() {
        if (contract == null) {
            phase = Phase.GET_CONTRACT;
            return false;
        }

        boolean needsHarvest = false;
        try {
            java.util.List<TileObject> patches = findAllPatchesAt(contract.getPatchImplementation());
            for (TileObject p : patches) {
                if (isWrongCrop(p)) continue;
                PatchState state = detectPatchState(p);
                log.info("Pre-bank patch state: {} at {}", state, p.getWorldLocation());
                if (state == PatchState.GROWING) {
                    log.info("Contract crop already growing, nothing to do");
                    FarmingContractPlugin.setStatus("Growing - stopping");
                    phase = Phase.DONE;
                    return false;
                }
                if (state == PatchState.HARVESTABLE || state == PatchState.GROWN_CHECK) {
                    needsHarvest = true;
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.debug("Pre-bank patch check failed, will bank for planting", e);
        }

        FarmingContractPlugin.setStatus("Banking");

        openSeedPacks();

        boolean wantSecateurs = FarmingContractData.usesSecateurs(contract.getPatchImplementation());

        if (needsHarvest) {
            log.info("Crop ready to harvest, banking for tools only");
            boolean needCoins = FarmingContractData.needsCoins(contract);
            boolean hasTools = Rs2Inventory.contains("Spade") && Rs2Inventory.contains("Rake")
                && (!wantSecateurs || Rs2Inventory.contains(FarmingContractData.MAGIC_SECATEURS))
                && (!needCoins || Rs2Inventory.itemQuantity("Coins") >= 200);
            if (!hasTools) {
                if (!openNearestBank()) return false;
                Rs2Bank.depositAllExcept("Spade", "Rake", "Seed dibber", "Magic secateurs", "Coins");
                ensureTool("Spade");
                ensureTool("Rake");
                ensureTool("Seed dibber");
                if (wantSecateurs) ensureItem(FarmingContractData.MAGIC_SECATEURS);
                if (needCoins && Rs2Inventory.itemQuantity("Coins") < 200 && Rs2Bank.hasBankItem("Coins", 200)) {
                    Rs2Bank.withdrawX("Coins", 200);
                    sleep(600, 900);
                }
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            }
            return true;
        }

        boolean needsCompost = config.compostType() != FarmingContractConfig.CompostType.NONE;
        int compostId = needsCompost ? config.compostType().getItemId() : -1;
        boolean wantProtection = config.protectTrees()
            && FarmingContractData.hasProtectionData(contract);

        if (Rs2Inventory.contains("Spade") && Rs2Inventory.contains("Rake")
                && Rs2Inventory.contains("Seed dibber") && hasRequiredSeeds()
                && (!wantSecateurs || Rs2Inventory.contains(FarmingContractData.MAGIC_SECATEURS))
                && (!needsCompost || Rs2Inventory.contains(compostId))
                && (!wantProtection || hasProtectionItems())) {
            return true;
        }

        if (!openNearestBank()) return false;

        Rs2Bank.depositAllExcept("Spade", "Rake", "Seed dibber", "Magic secateurs", "Coins");

        if (!ensureTool("Spade")) return bankFailed("No spade in bank");
        if (!ensureTool("Rake")) return bankFailed("No rake in bank");
        if (!ensureTool("Seed dibber")) return bankFailed("No seed dibber in bank");
        if (wantSecateurs) ensureItem(FarmingContractData.MAGIC_SECATEURS);

        int seedId = FarmingContractData.getSeedId(contract);
        int seedQty = FarmingContractData.getSeedsRequired(contract);
        if (seedId != -1 && (!Rs2Inventory.contains(seedId) || Rs2Inventory.count(seedId) < seedQty)) {
            log.info("Need seed id={} qty={}, bankHas={}", seedId, seedQty, Rs2Bank.hasBankItem(seedId, seedQty));
            if (Rs2Bank.hasBankItem(seedId, seedQty)) {
                Rs2Bank.withdrawX(seedId, seedQty);
                sleep(600, 900);
            }
        }

        if (needsCompost) {
            if (!Rs2Inventory.contains(compostId) && Rs2Bank.hasBankItem(compostId, 1)) {
                Rs2Bank.withdrawX(compostId, 1);
                sleep(600, 900);
            }
        }

        if (FarmingContractData.needsCoins(contract)) {
            if (Rs2Inventory.itemQuantity("Coins") < 200 && Rs2Bank.hasBankItem("Coins", 200)) {
                Rs2Bank.withdrawX("Coins", 200);
                sleep(600, 900);
            }
        }

        if (wantProtection && !hasProtectionItems()) {
            int protItemId = FarmingContractData.getProtectionItemId(contract);
            int protItemQty = FarmingContractData.getProtectionItemQty(contract);
            Rs2Bank.setWithdrawAsNote();
            sleep(300, 600);
            if (Rs2Bank.hasBankItem(protItemId, protItemQty)) {
                Rs2Bank.withdrawX(protItemId, protItemQty);
                sleep(600, 900);
            } else {
                log.warn("Protection items unavailable (id={}, need={}), will plant without protection",
                    protItemId, protItemQty);
            }
            Rs2Bank.setWithdrawAsItem();
            sleep(300, 600);
        }

        if (!hasRequiredSeeds()) {
            log.warn("Banking complete but seeds missing (id={}, have={})", seedId, Rs2Inventory.count(seedId));
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            if (config.autoDowngrade() && !needsDowngrade) {
                log.info("Seeds unavailable, requesting easier contract");
                needsDowngrade = true;
                contract = null;
                saveContract();
                phase = Phase.GET_CONTRACT;
                return false;
            }
            FarmingContractPlugin.setStatus("Missing seeds - stopping");
            phase = Phase.DONE;
            return false;
        }

        Rs2Bank.closeBank();
        return true;
    }

    private void openSeedPacks() {
        while (Rs2Inventory.contains("Seed pack")) {
            FarmingContractPlugin.setStatus("Opening seed pack");
            Rs2Inventory.interact("Seed pack", "Take-all");
            Rs2Inventory.waitForInventoryChanges(5000);
            sleep(600, 900);
        }
    }

    private static final WorldPoint GUILD_BANK = new WorldPoint(1253, 3741, 0);

    private boolean openNearestBank() {
        if (Rs2Bank.isOpen()) return true;

        GameObject bankObj = Rs2GameObject.findBank(40);
        if (bankObj != null) {
            Rs2GameObject.interact(bankObj);
            sleepUntil(() -> !Rs2Player.isMoving(), 10000);
            sleep(600, 900);
            sleepUntil(Rs2Bank::isOpen, 5000);
            if (Rs2Bank.isOpen()) return true;
        }

        Rs2Walker.walkTo(GUILD_BANK);
        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(GUILD_BANK) < 5, 15000);
        return Rs2Bank.openBank();
    }

    private boolean ensureTool(String name) {
        if (Rs2Inventory.contains(name)) return true;
        if (Rs2Bank.hasBankItem(name, 1)) {
            Rs2Bank.withdrawX(name, 1);
            sleep(600, 900);
            return true;
        }
        return false;
    }

    private void ensureItem(int itemId) {
        if (Rs2Inventory.contains(itemId)) return;
        if (Rs2Bank.hasBankItem(itemId, 1)) {
            Rs2Bank.withdrawX(itemId, 1);
            sleep(600, 900);
        }
    }

    private boolean bankFailed(String reason) {
        log.error(reason);
        Rs2Bank.closeBank();
        FarmingContractPlugin.setStatus(reason);
        phase = Phase.DONE;
        return false;
    }

    // --- Phase: HANDLE_PATCH ---

    private Phase handlePatch() {
        PatchImplementation patchType = contract.getPatchImplementation();

        if (patchType == PatchImplementation.ALLOTMENT) {
            return handleAllotmentPatch();
        }

        TileObject patch = findPatchAt(patchType);
        if (patch == null) {
            WorldPoint patchLoc = FarmingContractData.PATCH_LOCATIONS.get(patchType);
            if (patchLoc != null) {
                log.warn("{} patch not found, walking closer", patchType);
                Rs2Walker.walkTo(patchLoc);
                sleepUntil(() -> !Rs2Player.isMoving(), 15000);
            }
            return null;
        }

        PatchState state;
        try {
            state = detectPatchState(patch);
        } catch (RuntimeException e) {
            log.debug("detectPatchState interrupted, retrying next tick", e);
            return null;
        }
        FarmingContractPlugin.setStatus("Patch: " + state.name());
        log.info("Patch state: {}, type: {}", state, patchType);

        switch (state) {
            case GROWING:
                if (isWrongCrop(patch)) {
                    clearPatch(patch, patchType);
                    return null;
                }
                FarmingContractPlugin.setStatus("Growing - stopping");
                return Phase.DONE;

            case WEEDS:
                if (!Rs2Inventory.contains("Rake")) return Phase.BANKING;
                rakeWeeds(patch);
                patch = findObjectAt(patch.getWorldLocation());
                if (patch == null) return null;
                // fall through to EMPTY
            case EMPTY:
                if (!hasRequiredSeeds()) {
                    if (config.autoDowngrade() && !needsDowngrade) {
                        log.info("Seeds unavailable, requesting easier contract");
                        needsDowngrade = true;
                        contract = null;
                        saveContract();
                        return Phase.GET_CONTRACT;
                    }
                    FarmingContractPlugin.setStatus("Missing seeds");
                    return Phase.DONE;
                }
                if (!plantCrop(patch)) return null;
                protectTree();
                FarmingContractPlugin.setStatus("Planted - stopping");
                return Phase.DONE;

            case GROWN_CHECK:
                checkHealth(patch);
                return null;

            case HARVESTABLE:
                if (needsSpadeForAction(patchType) && !Rs2Inventory.contains("Spade")) return Phase.BANKING;
                boolean wrongCrop = isWrongCrop(patch);
                harvestPatch(patch, patchType);
                if (FarmingContractData.needsClearAfterHarvest(patchType)) {
                    clearPatch(patch, patchType);
                    noteItemsWithLeprechaun(contract.getItemID());
                }
                if (wrongCrop) return null;
                return Phase.TURN_IN;

            case CHECKED_TREE:
                if (FarmingContractData.needsCoins(contract) && Rs2Inventory.itemQuantity("Coins") < 200) return Phase.BANKING;
                boolean wrongTree = isWrongCrop(patch);
                clearPatch(patch, patchType);
                if (wrongTree) return null;
                return Phase.TURN_IN;

            case DEAD:
                if (!Rs2Inventory.contains("Spade")) return Phase.BANKING;
                if (FarmingContractData.needsClearAfterHarvest(patchType)) {
                    harvestPatch(patch, patchType);
                }
                clearPatch(patch, patchType);
                if (contract != null) noteItemsWithLeprechaun(contract.getItemID());
                return null;

            default:
                return null;
        }
    }

    private Phase handleAllotmentPatch() {
        java.util.List<TileObject> patches;
        try {
            patches = findAllPatchesAt(PatchImplementation.ALLOTMENT);
        } catch (RuntimeException e) {
            log.debug("findAllPatchesAt interrupted, retrying next tick", e);
            return null;
        }

        if (patches.isEmpty()) {
            WorldPoint patchLoc = FarmingContractData.PATCH_LOCATIONS.get(PatchImplementation.ALLOTMENT);
            if (patchLoc != null) {
                log.warn("Allotment patches not found, walking closer");
                Rs2Walker.walkTo(patchLoc);
                sleepUntil(() -> !Rs2Player.isMoving(), 15000);
            }
            return null;
        }

        TileObject rightCropPatch = null;
        PatchState rightCropState = null;
        TileObject usablePatch = null;
        PatchState usableState = null;

        for (TileObject p : patches) {
            PatchState state;
            try {
                state = detectPatchState(p);
            } catch (RuntimeException e) {
                log.debug("detectPatchState interrupted for allotment patch, retrying", e);
                return null;
            }
            log.info("Allotment patch at {} state: {}, wrongCrop: {}", p.getWorldLocation(), state, isWrongCrop(p));

            if (!isWrongCrop(p) && (state == PatchState.GROWING || state == PatchState.HARVESTABLE || state == PatchState.GROWN_CHECK)) {
                rightCropPatch = p;
                rightCropState = state;
                break;
            }

            if (state == PatchState.DEAD) {
                usablePatch = p;
                usableState = state;
            } else if ((state == PatchState.EMPTY || state == PatchState.WEEDS)
                    && (usableState == null || usableState != PatchState.DEAD)) {
                usablePatch = p;
                usableState = state;
            }
        }

        if (rightCropPatch != null) {
            if (rightCropState == PatchState.GROWING) {
                FarmingContractPlugin.setStatus("Growing - stopping");
                return Phase.DONE;
            }
            if (!Rs2Inventory.contains("Spade")) return Phase.BANKING;
            harvestPatch(rightCropPatch, PatchImplementation.ALLOTMENT);
            return Phase.TURN_IN;
        }

        TileObject target = usablePatch != null ? usablePatch : patches.get(0);
        PatchState state = usableState;
        if (state == null) {
            try {
                state = detectPatchState(target);
            } catch (RuntimeException e) {
                log.debug("detectPatchState interrupted, retrying", e);
                return null;
            }
        }

        FarmingContractPlugin.setStatus("Patch: " + state.name());
        log.info("Using allotment at {} state: {}", target.getWorldLocation(), state);

        switch (state) {
            case WEEDS:
                if (!Rs2Inventory.contains("Rake")) return Phase.BANKING;
                rakeWeeds(target);
                target = findObjectAt(target.getWorldLocation());
                if (target == null) return null;
                // fall through to EMPTY — patch is now clear
            case EMPTY:
                if (!hasRequiredSeeds()) {
                    if (config.autoDowngrade() && !needsDowngrade) {
                        needsDowngrade = true;
                        contract = null;
                        saveContract();
                        return Phase.GET_CONTRACT;
                    }
                    FarmingContractPlugin.setStatus("Missing seeds");
                    return Phase.DONE;
                }
                if (!plantCrop(target)) return null;
                FarmingContractPlugin.setStatus("Planted - stopping");
                return Phase.DONE;
            case DEAD:
                if (!Rs2Inventory.contains("Spade")) return Phase.BANKING;
                clearPatch(target, PatchImplementation.ALLOTMENT);
                target = findObjectAt(target.getWorldLocation());
                if (target == null) return null;
                // fall through to EMPTY — patch is now clear
                if (!hasRequiredSeeds()) {
                    if (config.autoDowngrade() && !needsDowngrade) {
                        needsDowngrade = true;
                        contract = null;
                        saveContract();
                        return Phase.GET_CONTRACT;
                    }
                    FarmingContractPlugin.setStatus("Missing seeds");
                    return Phase.DONE;
                }
                if (!plantCrop(target)) return null;
                FarmingContractPlugin.setStatus("Planted - stopping");
                return Phase.DONE;
            case GROWING:
            case HARVESTABLE:
                if (!Rs2Inventory.contains("Spade")) return Phase.BANKING;
                clearPatch(target, PatchImplementation.ALLOTMENT);
                return null;
            default:
                return null;
        }
    }

    // --- Phase: TURN_IN ---

    private boolean handleTurnIn() {
        FarmingContractPlugin.setStatus("Turning in contract");

        if (Rs2Npc.getNpc("Guildmaster Jane") == null) {
            Rs2Walker.walkTo(FarmingContractData.JANE_LOCATION);
            sleepUntil(() -> !Rs2Player.isMoving(), 15000);
            return false;
        }
        if (!Rs2Npc.interact("Guildmaster Jane", "Contract")
                && !Rs2Npc.interact("Guildmaster Jane", "Talk-to")) {
            log.info("Turn-in: failed to interact with Jane");
            return false;
        }
        sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Dialogue.hasSelectAnOption(), 15000);
        log.info("Turn-in: dialogue started: inDialogue={}, hasOptions={}",
            Rs2Dialogue.isInDialogue(), Rs2Dialogue.hasSelectAnOption());

        contract = null;
        contractName = null;

        int idleTicks = 0;
        while (idleTicks < 5 && super.run()) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                idleTicks = 0;
                String tier = getDesiredTier();
                log.info("Turn-in: select option visible, tier={}", tier);
                if (Rs2Dialogue.hasDialogueOption("Take another " + tier.toLowerCase())) {
                    Rs2Dialogue.clickOption("Take another " + tier.toLowerCase());
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption(tier)) {
                    Rs2Dialogue.clickOption(tier);
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Yes please")) {
                    Rs2Dialogue.clickOption("Yes please");
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Yes")) {
                    Rs2Dialogue.clickOption("Yes");
                    sleep(600, 900);
                    continue;
                }
                if (Rs2Dialogue.hasDialogueOption("Thank you")) {
                    if (shouldDowngradeContract()
                            && Rs2Dialogue.hasDialogueOption("Do you have anything easier?")) {
                        log.info("Turn-in: auto-downgrading {} (type: {}), asking for easier",
                            contract != null ? contract.getName() : "unknown",
                            contract != null ? contract.getPatchImplementation() : "unknown");
                        contract = null;
                        Rs2Dialogue.clickOption("Do you have anything easier?");
                        sleep(600, 900);
                        continue;
                    }
                    needsDowngrade = false;
                    Rs2Dialogue.clickOption("Thank you");
                    sleep(600, 900);
                    continue;
                }
                log.warn("Turn-in: no matching option found, clicking continue");
                Rs2Dialogue.clickContinue();
                sleep(600, 900);
                continue;
            }

            if (Rs2Dialogue.isInDialogue()) {
                idleTicks = 0;
                String text = Rs2Dialogue.getDialogueText();
                log.info("Turn-in: dialogue text: {}", text);
                if (text != null) {
                    Matcher m = CONTRACT_PATTERN.matcher(text);
                    if (m.find()) {
                        String cropName = m.group(1).trim();
                        contract = findProduceByContractName(cropName);
                        if (contract != null) {
                            log.info("New contract: {}", contract.getName());
                            saveContract();
                        }
                    }
                }
                Rs2Dialogue.clickContinue();
                sleep(600, 900);
                continue;
            }

            Rs2Dialogue.clickContinue();
            sleep(600, 900);
            idleTicks++;
        }

        log.info("Turn-in: loop exited, contract={}", contract != null ? contract.getName() : "null");

        openSeedPacks();

        return true;
    }

    // --- Patch State Detection ---

    private PatchState detectPatchState(TileObject patch) {
        WorldPoint loc = patch.getWorldLocation();

        for (String[] entry : new String[][]{
            {"Check-health", "GROWN_CHECK"}, {"Harvest", "HARVESTABLE"}, {"Pick", "HARVESTABLE"},
            {"Pick-from", "HARVESTABLE"}, {"Pick-spine", "HARVESTABLE"},
            {"Chop down", "CHECKED_TREE"}, {"Clear", "DEAD"}, {"Rake", "WEEDS"}
        }) {
            for (GameObject obj : Rs2GameObject.getGameObjects(
                    o -> Rs2GameObject.hasAction(o, entry[0], true), loc, 3)) {
                return PatchState.valueOf(entry[1]);
            }
        }

        var name = (patch instanceof GameObject) ? Rs2GameObject.getCompositionName((GameObject) patch) : java.util.Optional.<String>empty();
        String lower = name.isPresent() ? name.get().toLowerCase() : "";
        if (lower.endsWith("patch") || lower.equals("allotment")) {
            return PatchState.EMPTY;
        }
        log.info("detectPatchState id={} name={} no action matched, defaulting GROWING", patch.getId(), lower);
        return PatchState.GROWING;
    }

    private boolean isWrongCrop(TileObject patch) {
        if (contract == null) return false;
        var comp = Rs2GameObject.convertToObjectComposition(patch.getId(), false);
        if (comp == null) return false;
        String name = comp.getName().toLowerCase();
        if (name.endsWith("patch")) return false;
        String patchName = FarmingContractData.PATCH_NAMES.getOrDefault(
            contract.getPatchImplementation(), "").toLowerCase();
        if (!patchName.isEmpty() && name.contains(patchName)) return false;
        if (name.equals("allotment") || name.equals("herb") || name.equals("flower")
                || name.equals("bush") || name.equals("cactus")) return false;
        String expected = contract.getName().toLowerCase();
        return !name.contains(expected);
    }

    // --- Patch Actions ---

    private void rakeWeeds(TileObject patch) {
        FarmingContractPlugin.setStatus("Raking weeds");
        WorldPoint patchLoc = patch.getWorldLocation();
        while (super.run()) {
            if (Rs2GameObject.getGameObjects(
                    o -> Rs2GameObject.hasAction(o, "Rake", true), patchLoc, 3).isEmpty()) {
                break;
            }
            if (Rs2Inventory.isFull()) {
                Rs2Inventory.dropAll("Weeds");
                sleep(600, 900);
            }
            Rs2GameObject.interact(patch, "Rake");
            sleepUntil(() -> {
                if (Rs2Inventory.isFull()) return true;
                return Rs2GameObject.getGameObjects(
                    o -> Rs2GameObject.hasAction(o, "Rake", true), patchLoc, 3).isEmpty();
            }, 15000);
            sleep(600, 900);
        }
        if (Rs2Inventory.contains("Weeds")) {
            Rs2Inventory.dropAll("Weeds");
            sleep(600, 900);
        }
    }

    private boolean plantCrop(TileObject patch) {
        WorldPoint targetLoc = patch.getWorldLocation();

        int seedId = FarmingContractData.getSeedId(contract);
        if (seedId == -1 || !Rs2Inventory.contains(seedId)) {
            log.error("No seeds to plant");
            return false;
        }

        patch = clearWeeds(targetLoc);
        if (patch == null) return false;

        if (config.compostType() != FarmingContractConfig.CompostType.NONE) {
            int compostId = config.compostType().getItemId();
            if (Rs2Inventory.contains(compostId)) {
                FarmingContractPlugin.setStatus("Composting");
                for (int attempt = 0; attempt < 3 && Rs2Inventory.contains(compostId) && super.run(); attempt++) {
                    patch = findObjectAt(targetLoc);
                    if (patch == null) return false;
                    Rs2Inventory.useItemOnObject(compostId, patch.getId());
                    sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating(), 5000);
                    if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                        sleepUntil(() -> !Rs2Inventory.contains(compostId), 10000);
                        break;
                    }
                    sleep(1200, 1800);
                }
                sleep(600, 900);
            }
        }

        // Seeds are stackable; itemQuantity tracks the real count, count() only the slot.
        int qtyBefore = Rs2Inventory.itemQuantity(seedId);
        FarmingContractPlugin.setStatus("Planting " + contract.getName());
        for (int attempt = 0; attempt < 3 && Rs2Inventory.contains(seedId) && super.run(); attempt++) {
            // Weeds can regrow while composting — clear them again before each plant attempt.
            patch = clearWeeds(targetLoc);
            if (patch == null) return false;
            Rs2Inventory.useItemOnObject(seedId, patch.getId());
            // Success = a seed was actually consumed, not merely that the player moved.
            if (sleepUntil(() -> Rs2Inventory.itemQuantity(seedId) < qtyBefore, 8000)) {
                return true;
            }
            sleep(1200, 1800);
        }
        return Rs2Inventory.itemQuantity(seedId) < qtyBefore;
    }

    private TileObject clearWeeds(WorldPoint loc) {
        TileObject patch = findObjectAt(loc);
        while (patch != null && detectPatchState(patch) == PatchState.WEEDS && super.run()) {
            log.info("Patch has weeds, raking");
            rakeWeeds(patch);
            patch = findObjectAt(loc);
        }
        return patch;
    }

    private void harvestPatch(TileObject patch, PatchImplementation patchType) {
        String action;
        switch (patchType) {
            case HERB:
            case FLOWER:
                action = "Pick";
                break;
            case ALLOTMENT:
                action = "Harvest";
                break;
            case BUSH:
                action = "Pick-from";
                break;
            case CACTUS:
                action = "Pick-spine";
                break;
            default:
                return;
        }

        FarmingContractPlugin.setStatus("Harvesting");
        int cropItemId = contract.getItemID();
        WorldPoint patchLoc = patch.getWorldLocation();

        if (patchType == PatchImplementation.FLOWER) {
            noteItemsWithLeprechaun(cropItemId);
        }

        while (super.run()) {
            if (!Rs2GameObject.getGameObjects(
                    o -> Rs2GameObject.hasAction(o, action, true), patchLoc, 3).isEmpty()) {
                Rs2GameObject.interact(
                    Rs2GameObject.getGameObjects(
                        o -> Rs2GameObject.hasAction(o, action, true), patchLoc, 3).get(0), action);
                sleepUntil(() -> {
                    if (Rs2Inventory.isFull()) return true;
                    return Rs2GameObject.getGameObjects(
                        o -> Rs2GameObject.hasAction(o, action, true), patchLoc, 3).isEmpty();
                }, 60000);
            } else {
                break;
            }

            if (!FarmingContractData.needsClearAfterHarvest(patchType)
                    && Rs2Inventory.count(cropItemId) > 1) {
                noteItemsWithLeprechaun(cropItemId);
            }
        }

        if (patchType == PatchImplementation.FLOWER) {
            String cropName = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getItemManager().getItemComposition(cropItemId).getName()
            ).orElse("");
            if (!cropName.isEmpty()) {
                LootingParameters params = new LootingParameters(
                    5, 1, 1, 1, false, true, cropName);
                while (Rs2GroundItem.lootItemsBasedOnNames(params) && super.run()) {
                    Rs2Inventory.waitForInventoryChanges(5000);
                    sleep(600, 900);
                    noteItemsWithLeprechaun(cropItemId);
                }
            }
        }
    }

    private void noteItemsWithLeprechaun(int itemId) {
        if (!Rs2Inventory.contains(itemId)) return;
        FarmingContractPlugin.setStatus("Noting items");
        var leprechaun = Rs2Npc.getNpc("Tool leprechaun");
        if (leprechaun == null) return;
        Rs2Inventory.useItemOnNpc(itemId, leprechaun);
        Rs2Inventory.waitForInventoryChanges(10000);
        sleep(600, 900);
    }

    private void checkHealth(TileObject patch) {
        FarmingContractPlugin.setStatus("Checking health");
        Rs2GameObject.interact(patch, "Check-health");
        Rs2Player.waitForXpDrop(Skill.FARMING);
        sleep(600, 1200);
    }

    private void clearPatch(TileObject patch, PatchImplementation patchType) {
        if (patchType == PatchImplementation.TREE || patchType == PatchImplementation.FRUIT_TREE) {
            if (payGardener(patchType)) return;
        }

        FarmingContractPlugin.setStatus("Clearing patch");
        WorldPoint patchLoc = patch.getWorldLocation();
        TileObject current = findObjectAt(patchLoc);
        if (current == null) current = patch;

        // Chop first if needed
        GameObject chopTarget = Rs2GameObject.findObjectByImposter(current.getId(), "Chop", false);
        if (chopTarget != null) {
            Rs2GameObject.interact(chopTarget, "Chop down");
            sleepUntil(() -> !Rs2Player.isAnimating(), 15000);
            sleep(600, 1200);
            current = findObjectAt(patchLoc);
            if (current == null) return;
        }

        WorldPoint clearLoc = current.getWorldLocation();
        Rs2GameObject.interact(current, "Clear");
        sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Player.isAnimating(), 5000);

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasDialogueOption("Yes, don't ask me again")) {
                Rs2Dialogue.clickOption("Yes, don't ask me again");
            } else if (Rs2Dialogue.hasDialogueOption("Yes")) {
                Rs2Dialogue.clickOption("Yes");
            }
            sleep(600, 900);
        }

        sleepUntil(() -> Rs2GameObject.getGameObjects(
            o -> Rs2GameObject.hasAction(o, "Clear", true), clearLoc, 3).isEmpty(), 10000);
    }

    private boolean payGardener(PatchImplementation patchType) {
        if (Rs2Inventory.itemQuantity("Coins") < 200) return false;

        var gardener = Rs2Npc.getNearestNpcWithAction("Pay");
        if (gardener != null) {
            FarmingContractPlugin.setStatus("Paying gardener");
            Rs2Npc.interact(gardener, "Pay");
        } else {
            String npcName = patchType == PatchImplementation.FRUIT_TREE ? "Nikkie" : "Rosie";
            String payAction = patchType == PatchImplementation.FRUIT_TREE ? "Pay (Fruit tree)" : "Pay (tree patch)";
            var guildGardener = Microbot.getRs2NpcCache().query().withName(npcName).nearestOnClientThread();
            if (guildGardener == null) return false;
            FarmingContractPlugin.setStatus("Paying gardener");
            guildGardener.click(payAction);
        }

        sleepUntil(Rs2Dialogue::isInDialogue, 5000);
        if (!Rs2Dialogue.isInDialogue()) return false;

        sleep(600, 900);
        if (!Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickContinue();
            sleep(600, 900);
        }
        sleepUntil(Rs2Dialogue::hasSelectAnOption, 5000);

        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Yes");
            sleep(600, 1200);
        }

        while (Rs2Dialogue.isInDialogue()) {
            Rs2Dialogue.clickContinue();
            sleep(600, 900);
        }
        sleep(1200, 2400);
        return true;
    }

    private boolean protectTree() {
        if (!config.protectTrees() || contract == null) return false;
        if (!FarmingContractData.hasProtectionData(contract)) return false;

        int protItemId = FarmingContractData.getProtectionItemId(contract);
        int protItemQty = FarmingContractData.getProtectionItemQty(contract);
        if (countWithNoted(protItemId) < protItemQty) {
            log.warn("Missing protection items (have={}, need={}), skipping",
                countWithNoted(protItemId), protItemQty);
            return false;
        }

        PatchImplementation patchType = contract.getPatchImplementation();
        var gardener = Rs2Npc.getNearestNpcWithAction("Pay");
        if (gardener != null) {
            FarmingContractPlugin.setStatus("Protecting tree");
            Rs2Npc.interact(gardener, "Pay");
        } else {
            String npcName = patchType == PatchImplementation.FRUIT_TREE ? "Nikkie" : "Rosie";
            String payAction = patchType == PatchImplementation.FRUIT_TREE ? "Pay (Fruit tree)" : "Pay (tree patch)";
            var guildGardener = Microbot.getRs2NpcCache().query().withName(npcName).nearestOnClientThread();
            if (guildGardener == null) {
                log.warn("No gardener found for protection");
                return false;
            }
            FarmingContractPlugin.setStatus("Protecting tree");
            guildGardener.click(payAction);
        }
        sleepUntil(Rs2Dialogue::isInDialogue, 5000);

        if (!Rs2Dialogue.isInDialogue()) {
            log.warn("Gardener dialogue did not open");
            return false;
        }

        sleep(600, 900);
        if (Rs2Dialogue.hasDialogueText("already looking after")
                || Rs2Dialogue.hasDialogueText("Leave it with me")) {
            while (Rs2Dialogue.isInDialogue() && super.run()) {
                Rs2Dialogue.clickContinue();
                sleep(600, 900);
            }
            log.info("Gardener already protecting tree");
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickContinue();
            sleepUntil(Rs2Dialogue::hasSelectAnOption, 5000);
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            if (!Rs2Dialogue.clickOption("don't ask")) {
                Rs2Dialogue.clickOption("Yes");
            }
            sleep(600, 1200);
        }

        while (Rs2Dialogue.isInDialogue() && super.run()) {
            Rs2Dialogue.clickContinue();
            sleep(600, 900);
        }
        sleep(600, 1200);

        log.info("Tree protection paid for {}", contract.getName());
        return true;
    }

    private boolean hasProtectionItems() {
        if (contract == null) return false;
        int protItemId = FarmingContractData.getProtectionItemId(contract);
        int protItemQty = FarmingContractData.getProtectionItemQty(contract);
        if (protItemId == -1) return false;
        return countWithNoted(protItemId) >= protItemQty;
    }

    private int countWithNoted(int itemId) {
        int qty = Rs2Inventory.itemQuantity(itemId);
        Integer noted = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var comp = Microbot.getItemManager().getItemComposition(itemId);
            int linked = comp.getLinkedNoteId();
            return linked > 0 ? linked : null;
        }).orElse(null);
        if (noted != null) qty += Rs2Inventory.itemQuantity(noted);
        return qty;
    }

    // --- Helpers ---

    private java.util.List<TileObject> findAllPatchesAt(PatchImplementation patchType) {
        WorldPoint loc = FarmingContractData.PATCH_LOCATIONS.get(patchType);
        String patchName = FarmingContractData.PATCH_NAMES.get(patchType);
        if (loc == null || patchName == null) return java.util.Collections.emptyList();

        String lower = patchName.toLowerCase();
        int radius = patchType == PatchImplementation.ALLOTMENT ? 15 : 5;

        java.util.List<TileObject> results = new java.util.ArrayList<>();

        String cropLower = contract != null ? contract.getName().toLowerCase() : null;
        for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, radius)) {
            var name = Rs2GameObject.getCompositionName(obj);
            if (name.isEmpty()) continue;
            String n = name.get().toLowerCase();
            if ((cropLower != null && n.contains(cropLower)) || n.contains(lower)) {
                if (isDistinctPatch(results, obj)) {
                    results.add(obj);
                }
            }
        }

        if (results.isEmpty()) {
            for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, radius)) {
                var comp = Rs2GameObject.convertToObjectComposition(obj);
                if (comp == null || comp.getActions() == null) continue;
                for (String action : comp.getActions()) {
                    if (action != null && FARMING_ACTIONS.contains(action)) {
                        if (isDistinctPatch(results, obj)) {
                            results.add(obj);
                        }
                        break;
                    }
                }
            }
        }

        return results;
    }

    private boolean isDistinctPatch(java.util.List<TileObject> existing, TileObject candidate) {
        int y = candidate.getWorldLocation().getY();
        boolean isNorth = y >= 3732;
        for (TileObject t : existing) {
            boolean existingNorth = t.getWorldLocation().getY() >= 3732;
            if (isNorth == existingNorth) return false;
        }
        return true;
    }

    private TileObject findPatchAt(PatchImplementation patchType) {
        WorldPoint loc = FarmingContractData.PATCH_LOCATIONS.get(patchType);
        String patchName = FarmingContractData.PATCH_NAMES.get(patchType);
        if (loc == null || patchName == null) return null;

        String lower = patchName.toLowerCase();
        int radius = patchType == PatchImplementation.ALLOTMENT ? 15 : 5;

        // If we have a contract, check for a growing crop first (name changes when growing)
        if (contract != null) {
            String cropLower = contract.getName().toLowerCase();
            for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, radius)) {
                var name = Rs2GameObject.getCompositionName(obj);
                if (name.isPresent() && name.get().toLowerCase().contains(cropLower)) {
                    return obj;
                }
            }
        }

        // Search by patch base name (empty/weeded patches keep their base name)
        for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, radius)) {
            var name = Rs2GameObject.getCompositionName(obj);
            if (name.isPresent() && name.get().toLowerCase().contains(lower)) {
                return obj;
            }
        }

        // Fallback: find nearest with farming actions
        for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, radius)) {
            var comp = Rs2GameObject.convertToObjectComposition(obj);
            if (comp == null || comp.getActions() == null) continue;
            for (String action : comp.getActions()) {
                if (action != null && FARMING_ACTIONS.contains(action)) {
                    return obj;
                }
            }
        }

        log.debug("No {} patch found near {}", patchType, loc);
        return null;
    }

    private TileObject findObjectAt(WorldPoint loc) {
        for (GameObject obj : Rs2GameObject.getGameObjects(o -> true, loc, 1)) {
            if (obj.getWorldLocation().equals(loc)) return obj;
        }
        return null;
    }

    private static final java.util.Set<String> FARMING_ACTIONS = java.util.Set.of(
        "Rake", "Pick", "Harvest", "Pick-from", "Pick-spine",
        "Check-health", "Chop down", "Clear", "Inspect", "Guide"
    );

    private boolean needsSpadeForAction(PatchImplementation patchType) {
        return patchType == PatchImplementation.ALLOTMENT;
    }

    private boolean hasRequiredSeeds() {
        int seedId = FarmingContractData.getSeedId(contract);
        int seedQty = FarmingContractData.getSeedsRequired(contract);
        return seedId != -1 && Rs2Inventory.itemQuantity(seedId) >= seedQty;
    }


    @Override
    public void shutdown() {
        super.shutdown();
        contractName = null;
    }
}
