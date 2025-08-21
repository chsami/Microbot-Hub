package net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class EnchantingProcessor implements BankStandingProcessor {
    
    private BoltType selectedBoltType;
    
    public EnchantingProcessor(BoltType boltType) {
        this.selectedBoltType = boltType;
    }
    
    @Override
    public boolean validate() {
        if (selectedBoltType == null) {
            log.info("No bolt type selected");
            return false;
        }
        
        int currentLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        int requiredLevel = selectedBoltType.getLevelRequired();
        log.info("Magic level: {} (required: {})", currentLevel, requiredLevel);
        
        if (currentLevel < requiredLevel) {
            log.info("Insufficient magic level for {}", selectedBoltType.getName());
            return false;
        }
        
        log.info("Selected bolt type: {}", selectedBoltType.getName());
        return true;
    }
    
    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        requirements.add(selectedBoltType.getName());
        
        // add rune requirements based on equipped staff
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        
        for (int runeId : runeIds) {
            if (!providedRunes.contains(runeId)) {
                requirements.add(getRuneName(runeId));
            }
        }
        
        return requirements;
    }
    
    @Override
    public boolean hasRequiredItems() {
        return hasRequiredRunes() && Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId());
    }
    
    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }
        
        // check for missing items
        String missingItems = getMissingItemsList();
        if (!missingItems.isEmpty()) {
            log.info("Missing required items: {}", missingItems);
            return false;
        }
        
        // deposit everything first to start fresh
        if (!Rs2Inventory.isEmpty()) {
            log.info("Depositing inventory items");
            Rs2Bank.depositAll();
            boolean deposited = sleepUntil(() -> Rs2Inventory.isEmpty(), 3000);
            if (!deposited) {
                log.info("Failed to deposit items");
                return false;
            }
        }
        
        // try to equip the best available staff for this bolt type
        if (StaffUtils.equipBestAvailableStaff(selectedBoltType.getRuneIds())) {
            log.info("Staff equipped successfully");
            sleepUntil(() -> StaffUtils.getEquippedStaff() != null, 3000);
        }
        
        // withdraw required runes
        if (!withdrawRequiredRunes()) {
            log.info("Failed to withdraw required runes");
            return false;
        }
        
        // withdraw bolts
        if (!withdrawBolts()) {
            log.info("Failed to withdraw bolts");
            return false;
        }
        
        log.info("Banking complete");
        return true;
    }
    
    @Override
    public boolean process() {
        log.info("Processing enchanting for {}", selectedBoltType.getName());
        
        // check if we have bolts to enchant
        if (!Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            log.info("No bolts in inventory to enchant");
            return false;
        }
        
        // check if we have required runes
        if (!hasRequiredRunes()) {
            log.info("Not enough runes");
            return false;
        }
        
        // open magic tab if not already open
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            log.info("Opening magic tab");
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 2000);
            return true; // return to let the main loop continue
        }
        
        // cast the crossbow bolt enchant spell using sprite index
        log.info("Casting Enchant Crossbow Bolt spell");
        boolean success = castCrossbowBoltEnchantSpell();
        if (success) {
            // wait for the production widget to open
            log.info("Waiting for production widget to open");
            boolean widgetOpened = sleepUntil(() -> Rs2Widget.isProductionWidgetOpen(), 3000);
            if (widgetOpened) {
                log.info("Production widget opened, selecting bolt type: {}", selectedBoltType.getEnchantedName());
                // select the correct bolt type from the production menu
                boolean selected = Rs2Widget.clickWidget(selectedBoltType.getEnchantedName(), Optional.of(270), 13, false);
                if (selected) {
                    log.info("Successfully selected bolt type, waiting for enchanting to complete");
                    sleepUntil(() -> Rs2Player.isAnimating(), 2000); // wait until we start the animation
                    sleepUntil(() -> !Rs2Player.isAnimating(), 10000); // wait until the animation finishes
                    return true;
                } else {
                    log.info("Failed to select bolt type from production menu");
                    return false;
                }
            } else {
                log.info("Production widget failed to open");
                return false;
            }
        } else {
            log.info("Failed to cast spell");
            return false;
        }
    }
    
    @Override
    public boolean canContinueProcessing() {
        // check if we have bolts to enchant in inventory
        if (Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId()) && hasRequiredRunes()) {
            return true;
        }
        
        // check if there are bolts available in the bank
        if (Rs2Bank.isNearBank(10) && Rs2Bank.hasItem(selectedBoltType.getUnenchantedId())) {
            return true;
        }
        
        log.info("No more bolts to enchant in inventory or bank");
        return false;
    }
    
    @Override
    public String getStatusMessage() {
        return "Enchanting " + selectedBoltType.getName() + "...";
    }
    
    private boolean hasRequiredRunes() {
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();
        
        if (!Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            log.info("No bolts in inventory to enchant");
            return false;
        }
        
        Rs2Staff equippedStaff = StaffUtils.getEquippedStaff();
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        log.info("Checking runes with equipped staff: {}, provided runes: {}", 
                equippedStaff != null ? equippedStaff.name() : "none", providedRunes);
        
        for (int i = 0; i < runeIds.length; i++) {
            int runeId = runeIds[i];
            int runesNeededPerCast = runeQuantities[i];
            
            // check if this rune is provided by equipped staff
            if (providedRunes.contains(runeId)) {
                log.info("Rune {} provided by equipped staff", getRuneName(runeId));
                continue;
            }
            
            int available = Rs2Inventory.itemQuantity(runeId);
            if (available < runesNeededPerCast) {
                String runeName = getRuneName(runeId);
                log.info("Missing rune: {} (have: {}, need: {} per cast)", runeName, available, runesNeededPerCast);
                return false;
            }
        }
        return true;
    }
    
    private boolean withdrawRequiredRunes() {
        int[] runeIds = selectedBoltType.getRuneIds();
        
        // debug logging for staff detection
        Rs2Staff equippedStaff = StaffUtils.getEquippedStaff();
        if (equippedStaff != null) {
            log.info("Detected equipped staff: {}", equippedStaff.name());
            log.info("Staff provides runes: {}", equippedStaff.getRunes());
        } else {
            log.info("No equipped staff detected");
        }
        
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        log.info("Total provided runes: {}", providedRunes);
        
        for (int runeId : runeIds) {
            // skip runes that are provided by equipped staff
            if (providedRunes.contains(runeId)) {
                log.info("Skipping {} withdrawal - provided by equipped staff", getRuneName(runeId));
                continue;
            }
            
            if (!Rs2Inventory.hasItem(runeId)) {
                if (!Rs2Bank.hasItem(runeId)) {
                    log.info("Bank missing rune: {}", getRuneName(runeId));
                    return false;
                }
                log.info("Withdrawing all {} from bank", getRuneName(runeId));
                Rs2Bank.withdrawAll(runeId);
                boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(runeId), 3000);
                if (!withdrawn) {
                    log.info("Failed to withdraw rune: {}", getRuneName(runeId));
                    return false;
                }
                log.info("Successfully withdrew {} (total now: {})", getRuneName(runeId), Rs2Inventory.itemQuantity(runeId));
            }
        }
        return true;
    }
    
    private boolean withdrawBolts() {
        if (Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            return true;
        }
        
        if (!Rs2Bank.hasItem(selectedBoltType.getUnenchantedId())) {
            log.info("Bank missing bolts: {}", selectedBoltType.getName());
            return false;
        }
        
        log.info("Withdrawing all bolts: {}", selectedBoltType.getName());
        Rs2Bank.withdrawAll(selectedBoltType.getUnenchantedId());
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId()), 3000);
        if (!withdrawn) {
            log.info("Failed to withdraw bolts");
            return false;
        }
        return true;
    }
    
    private boolean castCrossbowBoltEnchantSpell() {
        // cast the crossbow bolt enchant spell using the specific widget ID 218.10
        try {
            log.info("Clicking Crossbow Bolt Enchantments spell at widget 218.10");
            boolean success = Rs2Widget.clickWidget(218, 10);
            if (success) {
                log.info("Successfully clicked Crossbow Bolt Enchantments spell");
                return true;
            } else {
                log.info("Failed to click Crossbow Bolt Enchantments spell widget");
                return false;
            }
        } catch (Exception e) {
            log.info("Error casting crossbow bolt enchant spell: {}", e.getMessage());
            return false;
        }
    }
    
    private String getRuneName(int runeId) {
        switch (runeId) {
            case ItemID.AIRRUNE: return "Air rune";
            case ItemID.WATERRUNE: return "Water rune";
            case ItemID.EARTHRUNE: return "Earth rune";
            case ItemID.FIRERUNE: return "Fire rune";
            case ItemID.MINDRUNE: return "Mind rune";
            case ItemID.COSMICRUNE: return "Cosmic rune";
            case ItemID.NATURERUNE: return "Nature rune";
            case ItemID.BLOODRUNE: return "Blood rune";
            case ItemID.LAWRUNE: return "Law rune";
            case ItemID.SOULRUNE: return "Soul rune";
            case ItemID.DEATHRUNE: return "Death rune";
            default: return "Unknown rune (" + runeId + ")";
        }
    }
    
    private String getMissingItemsList() {
        StringBuilder missingItems = new StringBuilder();
        
        // check for missing bolts
        if (!Rs2Bank.hasItem(selectedBoltType.getUnenchantedId()) && !Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            missingItems.append(selectedBoltType.getName());
        }
        
        // check for missing runes
        int[] runeIds = selectedBoltType.getRuneIds();
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        
        for (int runeId : runeIds) {
            // skip runes provided by equipped staff
            if (providedRunes.contains(runeId)) {
                continue;
            }
            
            if (!Rs2Bank.hasItem(runeId) && !Rs2Inventory.hasItem(runeId)) {
                if (missingItems.length() > 0) {
                    missingItems.append(" - ");
                }
                missingItems.append(getRuneName(runeId));
            }
        }
        
        return missingItems.toString();
    }
}