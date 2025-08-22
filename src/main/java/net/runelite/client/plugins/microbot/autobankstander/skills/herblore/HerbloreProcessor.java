package net.runelite.client.plugins.microbot.autobankstander.skills.herblore;

import java.util.ArrayList;
import java.util.List;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class HerbloreProcessor implements BankStandingProcessor {
    
    private Mode mode;
    private CleanHerbMode cleanHerbMode;
    private UnfinishedPotionMode unfinishedPotionMode;
    private HerblorePotion finishedPotion;
    private boolean useAmuletOfChemistry;
    
    // processing state
    private Herb currentHerb;
    private Herb currentHerbForUnfinished;
    private HerblorePotion currentPotion;
    private boolean currentlyMakingPotions;
    private int withdrawnAmount;
    private boolean amuletBroken = false;
    
    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode, 
                           HerblorePotion finishedPotion, boolean useAmuletOfChemistry) {
        this.mode = mode;
        this.cleanHerbMode = cleanHerbMode;
        this.unfinishedPotionMode = unfinishedPotionMode;
        this.finishedPotion = finishedPotion;
        this.useAmuletOfChemistry = useAmuletOfChemistry;
        this.currentlyMakingPotions = false;
        this.withdrawnAmount = 0;
    }
    
    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        log.info("Herblore level: {}", level);
        log.info("Selected mode: {}", mode);
        
        if (mode == Mode.FINISHED_POTIONS && finishedPotion != null) {
            if (level < finishedPotion.level) {
                log.info("Insufficient herblore level for {}: need {}, have {}", 
                    finishedPotion.name(), finishedPotion.level, level);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        
        switch (mode) {
            case CLEAN_HERBS:
                requirements.add("Grimy herbs");
                break;
            case UNFINISHED_POTIONS:
                requirements.add("Clean herbs");
                requirements.add("Vials of water");
                break;
            case FINISHED_POTIONS:
                if (finishedPotion != null) {
                    if (isSuperCombat(finishedPotion)) {
                        requirements.add("Torstol");
                        requirements.add("Super attack potions");
                        requirements.add("Super strength potions");
                        requirements.add("Super defence potions");
                    } else {
                        requirements.add("Unfinished " + finishedPotion.name() + " potions");
                        requirements.add("Secondary ingredient");
                    }
                }
                if (useAmuletOfChemistry) {
                    requirements.add("Amulet of chemistry");
                }
                break;
        }
        
        return requirements;
    }
    
    @Override
    public boolean hasRequiredItems() {
        switch (mode) {
            case CLEAN_HERBS:
                return Rs2Inventory.hasItem("grimy");
            case UNFINISHED_POTIONS:
                return currentHerbForUnfinished != null && 
                       Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
                       Rs2Inventory.hasItem(ItemID.VIAL_WATER);
            case FINISHED_POTIONS:
                if (currentPotion == null) return false;
                if (isSuperCombat(currentPotion)) {
                    return Rs2Inventory.hasItem(ItemID.TORSTOL) && 
                           Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK) &&
                           Rs2Inventory.hasItem(ItemID._4DOSE2STRENGTH) && 
                           Rs2Inventory.hasItem(ItemID._4DOSE2DEFENSE);
                } else {
                    return Rs2Inventory.hasItem(currentPotion.unfinished) && 
                           Rs2Inventory.hasItem(currentPotion.secondary);
                }
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
        
        switch (mode) {
            case CLEAN_HERBS:
                return bankForCleanHerbs();
            case UNFINISHED_POTIONS:
                return bankForUnfinishedPotions();
            case FINISHED_POTIONS:
                return bankForFinishedPotions();
        }
        
        return false;
    }
    
    @Override
    public boolean process() {
        if (currentlyMakingPotions) {
            // check if we need to stop making potions
            if (!hasRequiredItems()) {
                log.info("Finished making - no more ingredients");
                currentlyMakingPotions = false;
                return true; // return to banking
            }
            log.info("Still making potions - waiting for completion");
            return true;
        }
        
        switch (mode) {
            case CLEAN_HERBS:
                return processCleanHerbs();
            case UNFINISHED_POTIONS:
                return processUnfinishedPotions();
            case FINISHED_POTIONS:
                return processFinishedPotions();
        }
        
        return false;
    }
    
    @Override
    public boolean canContinueProcessing() {
        switch (mode) {
            case CLEAN_HERBS:
                // can continue if we have grimy herbs in inventory OR more in bank
                return Rs2Inventory.hasItem("grimy") || findHerb() != null;
            case UNFINISHED_POTIONS:
                // can continue if we have ingredients in inventory OR more in bank
                return (currentHerbForUnfinished != null && 
                        Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
                        Rs2Inventory.hasItem(ItemID.VIAL_WATER)) || 
                       findHerbForUnfinished() != null;
            case FINISHED_POTIONS:
                // can continue if we have ingredients in inventory OR more in bank
                return hasRequiredItems() || findPotion() != null;
        }
        return false;
    }
    
    @Override
    public String getStatusMessage() {
        if (currentlyMakingPotions) {
            return "Making potions...";
        }
        
        switch (mode) {
            case CLEAN_HERBS:
                return "Cleaning herbs...";
            case UNFINISHED_POTIONS:
                return "Making unfinished potions...";
            case FINISHED_POTIONS:
                return "Making finished potions...";
        }
        
        return "Processing herblore...";
    }
    
    private boolean bankForCleanHerbs() {
        currentHerb = findHerb();
        if (currentHerb == null) {
            log.info("No more herbs available");
            return false;
        }
        
        log.info("Withdrawing 28 grimy {}", currentHerb.name());
        Rs2Bank.withdrawX(currentHerb.grimy, 28);
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(currentHerb.grimy), 3000);
        if (!withdrawn) {
            log.info("Failed to withdraw grimy herbs");
            return false;
        }
        
        return true;
    }
    
    private boolean bankForUnfinishedPotions() {
        currentHerbForUnfinished = findHerbForUnfinished();
        if (currentHerbForUnfinished == null) {
            log.info("No more herbs or vials available");
            return false;
        }
        
        int herbCount = Rs2Bank.count(currentHerbForUnfinished.clean);
        int vialCount = Rs2Bank.count(ItemID.VIAL_WATER);
        withdrawnAmount = Math.min(Math.min(herbCount, vialCount), 14);
        
        log.info("Withdrawing {} clean herbs and vials", withdrawnAmount);
        Rs2Bank.withdrawX(currentHerbForUnfinished.clean, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID.VIAL_WATER, withdrawnAmount);
        
        boolean withdrawn = sleepUntil(() -> 
            Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && 
            Rs2Inventory.hasItem(ItemID.VIAL_WATER), 3000);
        
        if (!withdrawn) {
            log.info("Failed to withdraw unfinished ingredients");
            return false;
        }
        
        return true;
    }
    
    private boolean bankForFinishedPotions() {
        if (amuletBroken && useAmuletOfChemistry) {
            checkAndEquipAmulet();
            amuletBroken = false;
        }
        
        currentPotion = findPotion();
        if (currentPotion == null) {
            log.info("No more ingredients for selected potion");
            return false;
        }
        
        if (isSuperCombat(currentPotion)) {
            return bankForSuperCombat();
        } else if (usesStackableSecondary(currentPotion)) {
            return bankForStackableSecondary();
        } else {
            return bankForRegularPotion();
        }
    }
    
    private boolean bankForSuperCombat() {
        int torstolCount = Rs2Bank.count(ItemID.TORSTOL);
        int superAttackCount = Rs2Bank.count(ItemID._4DOSE2ATTACK);
        int superStrengthCount = Rs2Bank.count(ItemID._4DOSE2STRENGTH);
        int superDefenceCount = Rs2Bank.count(ItemID._4DOSE2DEFENSE);
        
        withdrawnAmount = Math.min(Math.min(Math.min(Math.min(torstolCount, superAttackCount), 
                                                   superStrengthCount), superDefenceCount), 7);
        
        log.info("Withdrawing {} of each super combat ingredient", withdrawnAmount);
        Rs2Bank.withdrawX(ItemID.TORSTOL, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2ATTACK, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2STRENGTH, withdrawnAmount);
        Rs2Bank.withdrawX(ItemID._4DOSE2DEFENSE, withdrawnAmount);
        
        return sleepUntil(() -> Rs2Inventory.hasItem(ItemID.TORSTOL) && 
                               Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK), 3000);
    }
    
    private boolean bankForStackableSecondary() {
        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished);
        int secondaryCount = Rs2Bank.count(currentPotion.secondary);
        
        int secondaryRatio = getStackableSecondaryRatio(currentPotion);
        withdrawnAmount = Math.min(unfinishedCount, 27);
        int secondaryNeeded = withdrawnAmount * secondaryRatio;
        
        if (secondaryCount < secondaryNeeded) {
            withdrawnAmount = secondaryCount / secondaryRatio;
            secondaryNeeded = withdrawnAmount * secondaryRatio;
        }
        
        log.info("Withdrawing {} unfinished and {} secondary", withdrawnAmount, secondaryNeeded);
        Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.secondary, secondaryNeeded);
        
        return sleepUntil(() -> Rs2Inventory.hasItem(currentPotion.unfinished) && 
                               Rs2Inventory.hasItem(currentPotion.secondary), 3000);
    }
    
    private boolean bankForRegularPotion() {
        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished);
        int secondaryCount = Rs2Bank.count(currentPotion.secondary);
        withdrawnAmount = Math.min(Math.min(unfinishedCount, secondaryCount), 14);
        
        log.info("Withdrawing {} unfinished and secondary", withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
        Rs2Bank.withdrawX(currentPotion.secondary, withdrawnAmount);
        
        return sleepUntil(() -> Rs2Inventory.hasItem(currentPotion.unfinished) && 
                               Rs2Inventory.hasItem(currentPotion.secondary), 3000);
    }
    
    private boolean processCleanHerbs() {
        if (Rs2Inventory.hasItem("grimy")) {
            log.info("Cleaning herbs using zigzag pattern");
            Rs2Inventory.cleanHerbs(InteractOrder.ZIGZAG);
            sleepUntil(() -> !Rs2Inventory.hasItem("grimy"), 5000);
            return true;
        }
        log.info("No grimy herbs in inventory - returning to banking");
        return true; // return true to go back to banking for more herbs
    }
    
    private boolean processUnfinishedPotions() {
        if (Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && Rs2Inventory.hasItem(ItemID.VIAL_WATER)) {
            log.info("Combining {} with vial of water", currentHerbForUnfinished.name());
            
            if (Rs2Inventory.combine(currentHerbForUnfinished.clean, ItemID.VIAL_WATER)) {
                sleep(600, 800);
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue(), 3000);
                    Rs2Keyboard.keyPress('1');
                }
                currentlyMakingPotions = true;
                log.info("Started making unfinished potions");
                return true;
            }
        }
        return false;
    }
    
    private boolean processFinishedPotions() {
        if (amuletBroken && useAmuletOfChemistry) {
            log.info("Amulet broke - need banking");
            return false;
        }
        
        if (isSuperCombat(currentPotion)) {
            return processSuperCombat();
        } else {
            return processRegularPotion();
        }
    }
    
    private boolean processSuperCombat() {
        if (Rs2Inventory.hasItem(ItemID.TORSTOL) && Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK)) {
            log.info("Combining torstol with super attack for super combat");
            
            if (Rs2Inventory.combine(ItemID.TORSTOL, ItemID._4DOSE2ATTACK)) {
                sleep(600, 800);
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasQuestion("How many do you wish to make?"), 3000);
                    Rs2Keyboard.keyPress('1');
                }
                currentlyMakingPotions = true;
                log.info("Started making super combat potions");
                return true;
            }
        }
        return false;
    }
    
    private boolean processRegularPotion() {
        if (Rs2Inventory.hasItem(currentPotion.unfinished) && Rs2Inventory.hasItem(currentPotion.secondary)) {
            log.info("Combining {} unfinished with secondary ingredient", currentPotion.name());
            
            if (Rs2Inventory.combine(currentPotion.unfinished, currentPotion.secondary)) {
                sleep(600, 800);
                if (withdrawnAmount > 1) {
                    sleepUntil(() -> Rs2Dialogue.hasQuestion("How many do you wish to make?"), 3000);
                    Rs2Keyboard.keyPress('1');
                }
                currentlyMakingPotions = true;
                log.info("Started making {} potions", currentPotion.name());
                return true;
            }
        }
        return false;
    }
    
    // Helper methods from original script
    private boolean usesStackableSecondary(HerblorePotion potion) {
        return getStackableSecondaryRatio(potion) > 1;
    }
    
    private int getStackableSecondaryRatio(HerblorePotion potion) {
        if (potion.secondary == ItemID.PRIF_CRYSTAL_SHARD_CRUSHED) return 4;
        if (potion.secondary == ItemID.SNAKEBOSS_SCALE) return 20;
        if (potion.secondary == ItemID.LAVA_SHARD) return 4;
        if (potion.secondary == ItemID.AMYLASE) return 4;
        if (potion.secondary == ItemID.ARAXYTE_VENOM_SACK) return 1;
        return 1;
    }
    
    private boolean isSuperCombat(HerblorePotion potion) {
        return potion == HerblorePotion.SUPER_COMBAT;
    }
    
    private Herb findHerb() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (cleanHerbMode == CleanHerbMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.grimy)) {
                    log.info("Found herb: {} (level {})", h.name(), h.level);
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromMode(cleanHerbMode);
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.grimy)) {
                log.info("Found specific herb: {}", specificHerb.name());
                return specificHerb;
            }
        }
        return null;
    }
    
    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (unfinishedPotionMode == UnfinishedPotionMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                    log.info("Found herb for unfinished: {} (level {})", h.name(), h.level);
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromUnfinishedMode(unfinishedPotionMode);
            if (specificHerb != null && level >= specificHerb.level && 
                Rs2Bank.hasItem(specificHerb.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                log.info("Found specific herb for unfinished: {}", specificHerb.name());
                return specificHerb;
            }
        }
        return null;
    }
    
    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        
        if (finishedPotion != null && level >= finishedPotion.level) {
            if (isSuperCombat(finishedPotion)) {
                boolean hasAll = Rs2Bank.hasItem(ItemID.TORSTOL) && 
                               Rs2Bank.hasItem(ItemID._4DOSE2ATTACK) &&
                               Rs2Bank.hasItem(ItemID._4DOSE2STRENGTH) && 
                               Rs2Bank.hasItem(ItemID._4DOSE2DEFENSE);
                if (hasAll) {
                    log.info("All super combat ingredients available");
                    return finishedPotion;
                }
            } else {
                boolean hasIngredients = Rs2Bank.hasItem(finishedPotion.unfinished) && 
                                       Rs2Bank.hasItem(finishedPotion.secondary);
                if (hasIngredients) {
                    log.info("All regular potion ingredients available");
                    return finishedPotion;
                }
            }
        }
        return null;
    }
    
    private void checkAndEquipAmulet() {
        if (!useAmuletOfChemistry) return;
        
        if (!Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) && 
            !Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) {
            
            log.info("No amulet equipped - need to get one from bank");
            
            if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) {
                log.info("Withdrawing and equipping imbued amulet of chemistry");
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED);
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED), 3000);
            } else if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY)) {
                log.info("Withdrawing and equipping regular amulet of chemistry");
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY);
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY), 3000);
            } else {
                log.info("No amulet of chemistry found in bank");
            }
        }
    }
    
    // setters for amulet breaking detection
    public void setAmuletBroken(boolean broken) {
        this.amuletBroken = broken;
    }
    
    // mapping methods
    private Herb getHerbFromMode(CleanHerbMode mode) {
        switch (mode) {
            case GUAM: return Herb.GUAM;
            case MARRENTILL: return Herb.MARRENTILL;
            case TARROMIN: return Herb.TARROMIN;
            case HARRALANDER: return Herb.HARRALANDER;
            case RANARR: return Herb.RANARR;
            case TOADFLAX: return Herb.TOADFLAX;
            case IRIT: return Herb.IRIT;
            case AVANTOE: return Herb.AVANTOE;
            case KWUARM: return Herb.KWUARM;
            case SNAPDRAGON: return Herb.SNAPDRAGON;
            case CADANTINE: return Herb.CADANTINE;
            case LANTADYME: return Herb.LANTADYME;
            case DWARF: return Herb.DWARF;
            case TORSTOL: return Herb.TORSTOL;
            default: return null;
        }
    }
    
    private Herb getHerbFromUnfinishedMode(UnfinishedPotionMode mode) {
        switch (mode) {
            case GUAM_POTION_UNF: return Herb.GUAM;
            case MARRENTILL_POTION_UNF: return Herb.MARRENTILL;
            case TARROMIN_POTION_UNF: return Herb.TARROMIN;
            case HARRALANDER_POTION_UNF: return Herb.HARRALANDER;
            case RANARR_POTION_UNF: return Herb.RANARR;
            case TOADFLAX_POTION_UNF: return Herb.TOADFLAX;
            case IRIT_POTION_UNF: return Herb.IRIT;
            case AVANTOE_POTION_UNF: return Herb.AVANTOE;
            case KWUARM_POTION_UNF: return Herb.KWUARM;
            case SNAPDRAGON_POTION_UNF: return Herb.SNAPDRAGON;
            case CADANTINE_POTION_UNF: return Herb.CADANTINE;
            case LANTADYME_POTION_UNF: return Herb.LANTADYME;
            case DWARF_WEED_POTION_UNF: return Herb.DWARF;
            case TORSTOL_POTION_UNF: return Herb.TORSTOL;
            default: return null;
        }
    }
}