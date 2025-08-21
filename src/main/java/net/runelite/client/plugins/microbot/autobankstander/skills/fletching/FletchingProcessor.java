package net.runelite.client.plugins.microbot.autobankstander.skills.fletching;

import java.util.ArrayList;
import java.util.List;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class FletchingProcessor implements BankStandingProcessor {
    
    private FletchingMode mode;
    private DartType selectedDart;
    private BoltType selectedBolt;
    private ArrowType selectedArrow;
    private JavelinType selectedJavelin;
    private BowType selectedBow;
    private CrossbowType selectedCrossbow;
    private ShieldType selectedShield;
    
    // processing state
    private boolean currentlyMaking;
    private int withdrawnAmount;
    
    // processing state tracking
    private long processingStartTime;
    private boolean hadAllMaterialsWhenStarted;
    
    public FletchingProcessor(FletchingMode mode, DartType dart, BoltType bolt, ArrowType arrow, 
                            JavelinType javelin, BowType bow, CrossbowType crossbow, ShieldType shield) {
        log.info("Initializing FletchingProcessor with mode: {}", mode);
        this.mode = mode;
        this.selectedDart = dart;
        this.selectedBolt = bolt;
        this.selectedArrow = arrow;
        this.selectedJavelin = javelin;
        this.selectedBow = bow;
        this.selectedCrossbow = crossbow;
        this.selectedShield = shield;
        this.currentlyMaking = false;
        this.withdrawnAmount = 0;
        
        log.info("FletchingProcessor initialized - bow type: {}", bow);
    }
    
    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.FLETCHING);
        log.info("Fletching level: {}", level);
        log.info("Selected mode: {}", mode);
        
        // first check if a specific type is selected for the mode
        switch (mode) {
            case DARTS:
                if (selectedDart == null) {
                    log.info("No dart type selected - please configure a specific dart type");
                    return false;
                }
                if (level < selectedDart.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedDart.getName(), selectedDart.getLevelRequired(), level);
                    return false;
                }
                break;
            case BOLTS:
                if (selectedBolt == null) {
                    log.info("No bolt type selected - please configure a specific bolt type");
                    return false;
                }
                if (level < selectedBolt.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedBolt.getName(), selectedBolt.getLevelRequired(), level);
                    return false;
                }
                break;
            case ARROWS:
                if (selectedArrow == null) {
                    log.info("No arrow type selected - please configure a specific arrow type");
                    return false;
                }
                if (level < selectedArrow.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedArrow.getName(), selectedArrow.getLevelRequired(), level);
                    return false;
                }
                break;
            case JAVELINS:
                if (selectedJavelin == null) {
                    log.info("No javelin type selected - please configure a specific javelin type");
                    return false;
                }
                if (level < selectedJavelin.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedJavelin.getName(), selectedJavelin.getLevelRequired(), level);
                    return false;
                }
                break;
            case BOWS:
                if (selectedBow == null) {
                    log.info("No bow type selected - please configure a specific bow type");
                    return false;
                }
                if (level < selectedBow.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedBow.getName(), selectedBow.getLevelRequired(), level);
                    return false;
                }
                break;
            case CROSSBOWS:
                if (selectedCrossbow == null) {
                    log.info("No crossbow type selected - please configure a specific crossbow type");
                    return false;
                }
                if (level < selectedCrossbow.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedCrossbow.getName(), selectedCrossbow.getLevelRequired(), level);
                    return false;
                }
                break;
            case SHIELDS:
                if (selectedShield == null) {
                    log.info("No shield type selected - please configure a specific shield type");
                    return false;
                }
                if (level < selectedShield.getLevelRequired()) {
                    log.info("Insufficient fletching level for {}: need {}, have {}", 
                        selectedShield.getName(), selectedShield.getLevelRequired(), level);
                    return false;
                }
                break;
        }
        
        log.info("Fletching processor validation successful");
        return true;
    }
    
    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        
        switch (mode) {
            case DARTS:
                if (selectedDart != null) {
                    requirements.add("Dart tips");
                    requirements.add("Feathers");
                }
                break;
            case BOLTS:
                if (selectedBolt != null) {
                    requirements.add("Unfinished bolts or bolt tips");
                    requirements.add("Feathers or bolts");
                }
                break;
            case ARROWS:
                if (selectedArrow != null) {
                    requirements.add("Arrow materials");
                    requirements.add("Feathers or arrowtips");
                }
                break;
            case JAVELINS:
                if (selectedJavelin != null) {
                    requirements.add("Javelin heads");
                    requirements.add("Javelin shafts");
                }
                break;
            case BOWS:
                if (selectedBow != null) {
                    requirements.add("Logs or unstrung bows");
                    requirements.add("Knife or bow strings");
                }
                break;
            case CROSSBOWS:
                if (selectedCrossbow != null) {
                    requirements.add("Crossbow materials");
                    requirements.add("Knife, limbs, or strings");
                }
                break;
            case SHIELDS:
                if (selectedShield != null) {
                    requirements.add("Logs");
                    requirements.add("Knife");
                }
                break;
        }
        
        return requirements;
    }
    
    @Override
    public boolean hasRequiredItems() {
        switch (mode) {
            case DARTS:
                return selectedDart != null && 
                       Rs2Inventory.hasItem(selectedDart.getTipId()) && 
                       Rs2Inventory.hasItem(selectedDart.getFeatherId());
            case BOLTS:
                return selectedBolt != null && 
                       Rs2Inventory.hasItem(selectedBolt.getMaterialOneId()) && 
                       Rs2Inventory.hasItem(selectedBolt.getMaterialTwoId());
            case ARROWS:
                return selectedArrow != null && 
                       Rs2Inventory.hasItem(selectedArrow.getMaterialOneId()) && 
                       Rs2Inventory.hasItem(selectedArrow.getMaterialTwoId());
            case JAVELINS:
                return selectedJavelin != null && 
                       Rs2Inventory.hasItem(selectedJavelin.getHeadId()) && 
                       Rs2Inventory.hasItem(selectedJavelin.getShaftId());
            case BOWS:
                return selectedBow != null && 
                       Rs2Inventory.hasItem(selectedBow.getMaterialOneId()) && 
                       Rs2Inventory.hasItem(selectedBow.getMaterialTwoId());
            case CROSSBOWS:
                return selectedCrossbow != null && 
                       Rs2Inventory.hasItem(selectedCrossbow.getMaterialOneId()) && 
                       Rs2Inventory.hasItem(selectedCrossbow.getMaterialTwoId());
            case SHIELDS:
                return selectedShield != null && 
                       Rs2Inventory.hasItem(selectedShield.getLogId()) && 
                       Rs2Inventory.hasItem(selectedShield.getKnifeId());
        }
        return false;
    }
    
    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }
        
        log.info("Depositing all items");
        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 3000);
        
        // reset processing state when banking (we're starting fresh)
        currentlyMaking = false;
        hadAllMaterialsWhenStarted = false;
        log.info("Reset processing state for new inventory");
        
        switch (mode) {
            case DARTS:
                return bankForDarts();
            case BOLTS:
                return bankForBolts();
            case ARROWS:
                return bankForArrows();
            case JAVELINS:
                return bankForJavelins();
            case BOWS:
                return bankForBows();
            case CROSSBOWS:
                return bankForCrossbows();
            case SHIELDS:
                return bankForShields();
        }
        
        return false;
    }
    
    @Override
    public boolean process() {
        // if currently making, check completion status
        if (currentlyMaking) {
            // check if we no longer have all materials we started with (indicating completion or depletion)
            if (!hasRequiredItems()) {
                log.info("Finished fletching - one or more materials depleted");
                currentlyMaking = false;
                hadAllMaterialsWhenStarted = false;
                return true;
            }
            
            // if we recently started processing, wait minimum time before checking again
            long timeSinceStart = System.currentTimeMillis() - processingStartTime;
            if (timeSinceStart < 3000) { // wait at least 3 seconds
                log.info("Recently started processing, waiting... ({}ms ago)", timeSinceStart);
                return true;
            }
            
            // if we had materials when started but still have them, processing might have stopped - try restart
            if (hadAllMaterialsWhenStarted) {
                log.info("Processing appears complete with materials remaining, attempting restart");
                currentlyMaking = false;
                hadAllMaterialsWhenStarted = false;
                // fall through to restart processing
            }
        }
        
        switch (mode) {
            case DARTS:
                return processDarts();
            case BOLTS:
                return processBolts();
            case ARROWS:
                return processArrows();
            case JAVELINS:
                return processJavelins();
            case BOWS:
                return processBows();
            case CROSSBOWS:
                return processCrossbows();
            case SHIELDS:
                return processShields();
        }
        
        return false;
    }
    
    @Override
    public boolean canContinueProcessing() {
        switch (mode) {
            case DARTS:
                return selectedDart != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedDart.getTipId()) && Rs2Bank.hasItem(selectedDart.getFeatherId())));
            case BOLTS:
                return selectedBolt != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedBolt.getMaterialOneId()) && Rs2Bank.hasItem(selectedBolt.getMaterialTwoId())));
            case ARROWS:
                return selectedArrow != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedArrow.getMaterialOneId()) && Rs2Bank.hasItem(selectedArrow.getMaterialTwoId())));
            case JAVELINS:
                return selectedJavelin != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedJavelin.getHeadId()) && Rs2Bank.hasItem(selectedJavelin.getShaftId())));
            case BOWS:
                return selectedBow != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedBow.getMaterialOneId()) && Rs2Bank.hasItem(selectedBow.getMaterialTwoId())));
            case CROSSBOWS:
                return selectedCrossbow != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedCrossbow.getMaterialOneId()) && Rs2Bank.hasItem(selectedCrossbow.getMaterialTwoId())));
            case SHIELDS:
                return selectedShield != null && 
                       (hasRequiredItems() || 
                        (Rs2Bank.hasItem(selectedShield.getLogId()) && Rs2Bank.hasItem(selectedShield.getKnifeId())));
        }
        return false;
    }
    
    @Override
    public String getStatusMessage() {
        if (currentlyMaking) {
            return "Making items...";
        }
        
        return "Fletching " + mode.getDisplayName().toLowerCase() + "...";
    }
    
    private boolean bankForDarts() {
        if (selectedDart == null) return false;
        
        int tipCount = Rs2Bank.count(selectedDart.getTipId());
        int featherCount = Rs2Bank.count(selectedDart.getFeatherId());
        withdrawnAmount = Math.min(Math.min(tipCount, featherCount), 14);
        
        log.info("Withdrawing {} dart tips and feathers", withdrawnAmount);
        Rs2Bank.withdrawX(selectedDart.getTipId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedDart.getFeatherId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedDart.getTipId()) && 
            Rs2Inventory.hasItem(selectedDart.getFeatherId()), 3000);
    }
    
    private boolean bankForBolts() {
        if (selectedBolt == null) return false;
        
        int materialOneCount = Rs2Bank.count(selectedBolt.getMaterialOneId());
        int materialTwoCount = Rs2Bank.count(selectedBolt.getMaterialTwoId());
        withdrawnAmount = Math.min(Math.min(materialOneCount, materialTwoCount), 14);
        
        log.info("Withdrawing {} bolt materials", withdrawnAmount);
        Rs2Bank.withdrawX(selectedBolt.getMaterialOneId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedBolt.getMaterialTwoId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedBolt.getMaterialOneId()) && 
            Rs2Inventory.hasItem(selectedBolt.getMaterialTwoId()), 3000);
    }
    
    private boolean bankForArrows() {
        if (selectedArrow == null) return false;
        
        int materialOneCount = Rs2Bank.count(selectedArrow.getMaterialOneId());
        int materialTwoCount = Rs2Bank.count(selectedArrow.getMaterialTwoId());
        withdrawnAmount = Math.min(Math.min(materialOneCount, materialTwoCount), 14);
        
        log.info("Withdrawing {} arrow materials", withdrawnAmount);
        Rs2Bank.withdrawX(selectedArrow.getMaterialOneId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedArrow.getMaterialTwoId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedArrow.getMaterialOneId()) && 
            Rs2Inventory.hasItem(selectedArrow.getMaterialTwoId()), 3000);
    }
    
    private boolean bankForJavelins() {
        if (selectedJavelin == null) return false;
        
        int headCount = Rs2Bank.count(selectedJavelin.getHeadId());
        int shaftCount = Rs2Bank.count(selectedJavelin.getShaftId());
        withdrawnAmount = Math.min(Math.min(headCount, shaftCount), 14);
        
        log.info("Withdrawing {} javelin materials", withdrawnAmount);
        Rs2Bank.withdrawX(selectedJavelin.getHeadId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedJavelin.getShaftId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedJavelin.getHeadId()) && 
            Rs2Inventory.hasItem(selectedJavelin.getShaftId()), 3000);
    }
    
    private boolean bankForBows() {
        if (selectedBow == null) return false;
        
        int materialOneCount = Rs2Bank.count(selectedBow.getMaterialOneId());
        int materialTwoCount = Rs2Bank.count(selectedBow.getMaterialTwoId());
        withdrawnAmount = Math.min(Math.min(materialOneCount, materialTwoCount), 14);
        
        log.info("Withdrawing {} bow materials", withdrawnAmount);
        Rs2Bank.withdrawX(selectedBow.getMaterialOneId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedBow.getMaterialTwoId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedBow.getMaterialOneId()) && 
            Rs2Inventory.hasItem(selectedBow.getMaterialTwoId()), 3000);
    }
    
    private boolean bankForCrossbows() {
        if (selectedCrossbow == null) return false;
        
        int materialOneCount = Rs2Bank.count(selectedCrossbow.getMaterialOneId());
        int materialTwoCount = Rs2Bank.count(selectedCrossbow.getMaterialTwoId());
        withdrawnAmount = Math.min(Math.min(materialOneCount, materialTwoCount), 14);
        
        log.info("Withdrawing {} crossbow materials", withdrawnAmount);
        Rs2Bank.withdrawX(selectedCrossbow.getMaterialOneId(), withdrawnAmount);
        Rs2Bank.withdrawX(selectedCrossbow.getMaterialTwoId(), withdrawnAmount);
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedCrossbow.getMaterialOneId()) && 
            Rs2Inventory.hasItem(selectedCrossbow.getMaterialTwoId()), 3000);
    }
    
    private boolean bankForShields() {
        if (selectedShield == null) return false;
        
        int logCount = Rs2Bank.count(selectedShield.getLogId());
        int maxWithdraw = 28 / selectedShield.getLogsRequired();
        withdrawnAmount = Math.min(logCount / selectedShield.getLogsRequired(), maxWithdraw);
        int logsToWithdraw = withdrawnAmount * selectedShield.getLogsRequired();
        
        log.info("Withdrawing {} logs for {} shields", logsToWithdraw, withdrawnAmount);
        Rs2Bank.withdrawX(selectedShield.getLogId(), logsToWithdraw);
        Rs2Bank.withdrawOne(selectedShield.getKnifeId());
        
        return sleepUntil(() -> 
            Rs2Inventory.hasItem(selectedShield.getLogId()) && 
            Rs2Inventory.hasItem(selectedShield.getKnifeId()), 3000);
    }
    
    private boolean processDarts() {
        if (selectedDart != null && Rs2Inventory.hasItem(selectedDart.getTipId()) && Rs2Inventory.hasItem(selectedDart.getFeatherId())) {
            log.info("Combining dart tips with feathers for: {}", selectedDart.getName());
            
            if (Rs2Inventory.combine(selectedDart.getTipId(), selectedDart.getFeatherId())) {
                if (handleMakeDialogue(selectedDart.getName())) {
                    currentlyMaking = true;
                    processingStartTime = System.currentTimeMillis();
                    hadAllMaterialsWhenStarted = true;
                    log.info("Started making: {} at {}", selectedDart.getName(), processingStartTime);
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processBolts() {
        if (selectedBolt != null && Rs2Inventory.hasItem(selectedBolt.getMaterialOneId()) && Rs2Inventory.hasItem(selectedBolt.getMaterialTwoId())) {
            log.info("Combining bolt materials for: {}", selectedBolt.getName());
            
            if (Rs2Inventory.combine(selectedBolt.getMaterialOneId(), selectedBolt.getMaterialTwoId())) {
                if (handleMakeDialogue(selectedBolt.getName())) {
                    currentlyMaking = true;
                    log.info("Started making: {}", selectedBolt.getName());
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processArrows() {
        if (selectedArrow != null && Rs2Inventory.hasItem(selectedArrow.getMaterialOneId()) && Rs2Inventory.hasItem(selectedArrow.getMaterialTwoId())) {
            log.info("Combining arrow materials for: {}", selectedArrow.getName());
            
            if (Rs2Inventory.combine(selectedArrow.getMaterialOneId(), selectedArrow.getMaterialTwoId())) {
                if (handleMakeDialogue(selectedArrow.getName())) {
                    currentlyMaking = true;
                    log.info("Started making: {}", selectedArrow.getName());
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processJavelins() {
        if (selectedJavelin != null && Rs2Inventory.hasItem(selectedJavelin.getHeadId()) && Rs2Inventory.hasItem(selectedJavelin.getShaftId())) {
            log.info("Combining javelin heads with shafts for: {}", selectedJavelin.getName());
            
            if (Rs2Inventory.combine(selectedJavelin.getHeadId(), selectedJavelin.getShaftId())) {
                if (handleMakeDialogue(selectedJavelin.getName())) {
                    currentlyMaking = true;
                    log.info("Started making: {}", selectedJavelin.getName());
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processBows() {
        if (selectedBow != null && Rs2Inventory.hasItem(selectedBow.getMaterialOneId()) && Rs2Inventory.hasItem(selectedBow.getMaterialTwoId())) {
            log.info("Combining bow materials for: {}", selectedBow.getName());
            
            if (Rs2Inventory.combine(selectedBow.getMaterialOneId(), selectedBow.getMaterialTwoId())) {
                if (handleMakeDialogue(selectedBow.getName())) {
                    currentlyMaking = true;
                    processingStartTime = System.currentTimeMillis();
                    hadAllMaterialsWhenStarted = true;
                    log.info("Started making: {} at {}", selectedBow.getName(), processingStartTime);
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processCrossbows() {
        if (selectedCrossbow != null && Rs2Inventory.hasItem(selectedCrossbow.getMaterialOneId()) && Rs2Inventory.hasItem(selectedCrossbow.getMaterialTwoId())) {
            log.info("Combining crossbow materials for: {}", selectedCrossbow.getName());
            
            if (Rs2Inventory.combine(selectedCrossbow.getMaterialOneId(), selectedCrossbow.getMaterialTwoId())) {
                if (handleMakeDialogue(selectedCrossbow.getName())) {
                    currentlyMaking = true;
                    log.info("Started making: {}", selectedCrossbow.getName());
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean processShields() {
        if (selectedShield != null && Rs2Inventory.hasItem(selectedShield.getLogId()) && Rs2Inventory.hasItem(selectedShield.getKnifeId())) {
            log.info("Using knife on logs to make: {}", selectedShield.getName());
            
            if (Rs2Inventory.combine(selectedShield.getLogId(), selectedShield.getKnifeId())) {
                if (handleMakeDialogue(selectedShield.getName())) {
                    currentlyMaking = true;
                    log.info("Started making: {}", selectedShield.getName());
                    return true;
                }
            }
        }
        return false;
    }
    
    // generic dialogue handler that uses enum data to select the correct product
    private boolean handleMakeDialogue(String productName) {
        log.info("Handling make dialogue for product: {}", productName);
        
        // wait for dialogue to appear
        boolean dialogueAppeared = sleepUntil(() -> Rs2Dialogue.hasQuestion("How many") || 
                                                   Rs2Dialogue.hasCombinationDialogue(), 3000);
        
        if (!dialogueAppeared) {
            log.info("No dialogue appeared within timeout");
            return false;
        }
        
        // handle standard "How many" dialogue
        if (Rs2Dialogue.hasQuestion("How many")) {
            log.info("Standard make dialogue detected, looking for option: {}", productName);
            
            // try to find and click the specific product option
            if (Rs2Dialogue.hasDialogueOption(productName)) {
                log.info("Found product option: {}", productName);
                return Rs2Dialogue.clickOption(productName);
            }
            
            // fallback to "Make All" if specific product not found
            if (Rs2Dialogue.hasDialogueOption("Make All")) {
                log.info("Using fallback option: Make All");
                return Rs2Dialogue.clickOption("Make All");
            }
            
            // final fallback to "All" option
            if (Rs2Dialogue.hasDialogueOption("All")) {
                log.info("Using fallback option: All");
                return Rs2Dialogue.clickOption("All");
            }
            
            log.info("No suitable dialogue option found");
            return false;
        }
        
        // handle combination-style dialogue
        if (Rs2Dialogue.hasCombinationDialogue()) {
            log.info("Combination dialogue detected, looking for option: {}", productName);
            return Rs2Dialogue.clickCombinationOption(productName);
        }
        
        log.info("No supported dialogue type detected");
        return false;
    }
    
    // public method to check if we're actively processing (for main script)
    public boolean isActivelyProcessing() {
        if (!currentlyMaking) {
            return false;
        }
        
        // if we recently started processing, consider it active
        long timeSinceStart = System.currentTimeMillis() - processingStartTime;
        if (timeSinceStart < 3000) { // active for first 3 seconds
            return true;
        }
        
        // if we still have all materials we started with, might still be processing
        return hasRequiredItems() && hadAllMaterialsWhenStarted;
    }
}