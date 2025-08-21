package net.runelite.client.plugins.microbot.mke_wintertodt.startup.gear;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.mke_wintertodt.MKE_WintertodtConfig;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.inventory.WintertodtInventoryManager;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Database-driven gear manager for Wintertodt optimization.
 * Leverages the comprehensive WintertodtGearDatabase to automatically find and equip
 * the best available gear based on player's access and skill levels.
 * 
 * @author MakeCD
 * @version 2.0.2
 */
public class WintertodtGearManager {
    
    private final MKE_WintertodtConfig config;
    private final WintertodtGearDatabase gearDatabase;
    private Map<EquipmentInventorySlot, WintertodtGearItem> optimalGear;
    private List<String> gearAnalysisLog;
    
    // Cache these to avoid repeated API calls during analysis
    private Map<Skill, Integer> cachedPlayerLevels;
    private Set<String> cachedCompletedQuests;
    
    public WintertodtGearManager(MKE_WintertodtConfig config) {
        this.config = config;
        this.gearDatabase = new WintertodtGearDatabase();
        this.optimalGear = new HashMap<>();
        this.gearAnalysisLog = new ArrayList<>();
    }
    
    /**
     * Main method - analyzes and equips optimal gear for Wintertodt.
     */
    public boolean setupOptimalGear() {
        try {
            Microbot.log("Setting up optimal Wintertodt gear using database analysis...");
            
            // Ensure bank access
            if (!ensureBankAccess()) {
                return false;
            }
            
            // Handle bruma torch conversion if needed
            handleBrumaTorchConversion();
            
            // Analyze optimal gear using database
            if (!analyzeOptimalGearFromDatabase()) {
                return false;
            }
            
            // Equip the optimal gear
            return equipOptimalGear();
            
        } catch (Exception e) {
            Microbot.log("Error setting up optimal gear: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Cache player data once per analysis to improve performance.
     */
    private void cachePlayerData() {
        cachedPlayerLevels = getCurrentPlayerSkillLevels();
        cachedCompletedQuests = getCompletedQuests();
        gearAnalysisLog.add("Cached player data - " + cachedCompletedQuests.size() + " quests completed");
    }
    
    /**
     * Database-driven gear analysis - finds best available gear for each slot.
     */
    private boolean analyzeOptimalGearFromDatabase() {
        try {
            Microbot.log("Analyzing gear using comprehensive database...");
            gearAnalysisLog.clear();
            optimalGear.clear();
            
            // Cache player data once for entire analysis
            cachePlayerData();
            
            // Analyze each equipment slot using database
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
                analyzeSlotFromDatabase(slot);
            }
            
            // Log comprehensive analysis
            logDetailedGearAnalysis();
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error analyzing gear from database: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Analyzes a specific equipment slot using the database.
     */
    private void analyzeSlotFromDatabase(EquipmentInventorySlot slot) {
        gearAnalysisLog.add("=== " + slot.name() + " SLOT ANALYSIS ===");
        
        // Get all gear items for this slot from database
        List<WintertodtGearItem> availableGear = gearDatabase.getGearForSlot(slot)
            .stream()
            .filter(this::canPlayerUseItem)
            .filter(this::hasAccessToItem)
            .sorted((a, b) -> Integer.compare(b.getEffectivePriority(), a.getEffectivePriority()))
            .collect(Collectors.toList());
            
        if (availableGear.isEmpty()) {
            gearAnalysisLog.add("No suitable gear found for " + slot.name());
            return;
        }
        
        // Special handling for weapon slot (axe logic)
        if (slot == EquipmentInventorySlot.WEAPON) {
            handleWeaponSlotSpecialLogic(availableGear);
            return;
        }
        
        // For other slots, pick the highest priority item
        WintertodtGearItem bestItem = availableGear.get(0);
        optimalGear.put(slot, bestItem);
        
        gearAnalysisLog.add("Selected: " + bestItem.getItemName() + 
                           " (Priority: " + bestItem.getEffectivePriority() + ")");
        gearAnalysisLog.add("Reason: " + bestItem.getDescription());
        
        if (bestItem.providesWarmth()) {
            gearAnalysisLog.add("Provides warmth");
        }
        if (bestItem.getWeight() < 0) {
            gearAnalysisLog.add("Weight reduction: " + Math.abs(bestItem.getWeight()) + " kg");
        }
        if (bestItem.hasSpecialEffect()) {
            gearAnalysisLog.add("Special effect");
        }
        
        // Log alternatives
        if (availableGear.size() > 1) {
            gearAnalysisLog.add("Alternatives found: " + (availableGear.size() - 1));
            for (int i = 1; i < Math.min(4, availableGear.size()); i++) {
                WintertodtGearItem alt = availableGear.get(i);
                gearAnalysisLog.add("  - " + alt.getItemName() + " (Priority: " + alt.getEffectivePriority() + ")");
            }
        }
    }
    
    /**
     * Special logic for weapon slot considering axe management.
     */
    private void handleWeaponSlotSpecialLogic(List<WintertodtGearItem> availableWeapons) {
        gearAnalysisLog.add("Applying weapon slot special logic...");
        
        // Get axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        gearAnalysisLog.add("Axe decision: " + axeDecision.toString());
        
        if (axeDecision.shouldEquipAxe()) {
            // Find the axe in available weapons
            WintertodtGearItem axeItem = availableWeapons.stream()
                .filter(item -> item.getItemId() == axeDecision.getAxeId())
                .findFirst()
                .orElse(null);
                
            if (axeItem != null) {
                optimalGear.put(EquipmentInventorySlot.WEAPON, axeItem);
                gearAnalysisLog.add("Selected axe for weapon slot: " + axeItem.getItemName());
            } else {
                gearAnalysisLog.add("Optimal axe not accessible, finding fallback...");
                findFallbackWeapon(availableWeapons);
            }
        } else {
            gearAnalysisLog.add("Axe will be kept in inventory, selecting alternative weapon...");
            // Find best non-axe weapon
            WintertodtGearItem bestNonAxe = availableWeapons.stream()
                .filter(item -> !item.getItemName().toLowerCase().contains("axe"))
                .findFirst()
                .orElse(null);
                
            if (bestNonAxe != null) {
                optimalGear.put(EquipmentInventorySlot.WEAPON, bestNonAxe);
                gearAnalysisLog.add("Selected alternative weapon: " + bestNonAxe.getItemName());
            }
        }
    }
    
    /**
     * Finds fallback weapon if optimal choice isn't available.
     */
    private void findFallbackWeapon(List<WintertodtGearItem> availableWeapons) {
        if (!availableWeapons.isEmpty()) {
            WintertodtGearItem fallback = availableWeapons.get(0);
            optimalGear.put(EquipmentInventorySlot.WEAPON, fallback);
            gearAnalysisLog.add("Fallback weapon: " + fallback.getItemName());
        }
    }
    
    /**
     * Checks if player can use an item based on requirements using the item's built-in method.
     */
    private boolean canPlayerUseItem(WintertodtGearItem item) {
        // Use cached data instead of re-fetching
        boolean meetsReqs = item.meetsRequirements(cachedPlayerLevels, cachedCompletedQuests);
        
        if (!meetsReqs) {
            gearAnalysisLog.add("  X " + item.getItemName() + " - requirements not met");
            logUnmetRequirements(item, cachedPlayerLevels, cachedCompletedQuests);
        }
        
        return meetsReqs;
    }
    
    /**
     * Gets current player skill levels.
     */
    private Map<Skill, Integer> getCurrentPlayerSkillLevels() {
        Map<Skill, Integer> playerLevels = new HashMap<>();
        for (Skill skill : Skill.values()) {
            playerLevels.put(skill, Rs2Player.getRealSkillLevel(skill));
        }
        return playerLevels;
    }
    
    /**
     * Enhanced quest checking with better error handling.
     */
    private Set<String> getCompletedQuests() {
        Set<String> completedQuests = new HashSet<>();
        
        try {
            int questCount = 0;
            // Check all quests using RuneLite's quest state system
            for (Quest quest : Quest.values()) {
                try {
                    if (Rs2Player.getQuestState(quest) == QuestState.FINISHED) {
                        completedQuests.add(quest.getName());
                        completedQuests.add(quest.toString());
                        questCount++;
                    }
                } catch (Exception e) {
                    // Individual quest check failed, continue with others
                    Microbot.log("Failed to check quest: " + quest.name());
                }
            }
            
            gearAnalysisLog.add("Detected " + questCount + " completed quests");
            
        } catch (Exception e) {
            Microbot.log("Error checking quest states: " + e.getMessage());
            gearAnalysisLog.add("Quest checking failed - using conservative gear analysis");
        }
        
        return completedQuests;
    }
    
    /**
     * Logs unmet requirements for debugging.
     */
    private void logUnmetRequirements(WintertodtGearItem item, Map<Skill, Integer> playerLevels, Set<String> completedQuests) {
        // Check which skill requirements are not met
        for (Map.Entry<Skill, Integer> req : item.getSkillRequirements().entrySet()) {
            int playerLevel = playerLevels.getOrDefault(req.getKey(), 1);
            if (playerLevel < req.getValue()) {
                gearAnalysisLog.add("    Need " + req.getKey().getName() + " " + req.getValue() + " (have " + playerLevel + ")");
            }
        }
        
        // Check which quest requirements are not met
        for (String quest : item.getQuestRequirements()) {
            if (!completedQuests.contains(quest)) {
                gearAnalysisLog.add("    Need quest: " + quest);
            }
        }
    }
    
    /**
     * Checks if player has access to an item.
     */
    private boolean hasAccessToItem(WintertodtGearItem item) {
        int itemId = item.getItemId();
        return Rs2Inventory.hasItem(itemId) || 
               Rs2Equipment.isWearing(itemId) || 
               Rs2Bank.hasItem(itemId);
    }
    
    /**
     * Equips all optimal gear pieces.
     */
    private boolean equipOptimalGear() {
        try {
            Microbot.log("Equipping optimal gear setup...");
            
            if (!ensureBankAccess()) {
                return false;
            }
            
            // Bank current non-optimal equipment
            bankCurrentGear();
            
            // Equip each optimal gear piece
            for (Map.Entry<EquipmentInventorySlot, WintertodtGearItem> entry : optimalGear.entrySet()) {
                equipGearItem(entry.getValue());
            }
            
            Microbot.log("Optimal gear equipped successfully!");
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error equipping gear: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Equips a specific gear item.
     */
    private void equipGearItem(WintertodtGearItem gearItem) {
        int itemId = gearItem.getItemId();
        
        // Skip if already equipped
        if (Rs2Equipment.isWearing(itemId)) {
            Microbot.log("Already wearing: " + gearItem.getItemName());
            return;
        }
        
        // Withdraw if needed
        if (!Rs2Inventory.hasItem(itemId)) {
            if (Rs2Bank.hasItem(itemId)) {
                Rs2Bank.withdrawOne(itemId);
                sleepUntil(() -> Rs2Inventory.hasItem(itemId), 3000);
            } else {
                Microbot.log("Warning: Cannot find " + gearItem.getItemName() + " in bank");
                return;
            }
        }
        
        // Equip the item
        Microbot.log("Equipping: " + gearItem.getItemName());
        Rs2Inventory.wield(itemId);
        sleepUntil(() -> Rs2Equipment.isWearing(itemId), 3000);
    }
    
    /**
     * Banks current gear intelligently, preserving optimally placed tools.
     */
    private void bankCurrentGear() {
        try {
            Microbot.log("Banking current gear (preserving optimal tool placement)...");
            
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            
            // Bank inventory items except optimally placed tools
            for (int slotIndex = 0; slotIndex < 28; slotIndex++) {
                final int slot = slotIndex; // Make effectively final for lambda
                Rs2ItemModel item = Rs2Inventory.get(slot);
                if (item != null && !shouldPreserveTool(item.getId(), slot, axeDecision)) {
                    Rs2Bank.depositOne(item.getId());
                    sleepUntilTrue(() -> Rs2Inventory.get(slot) == null || 
                                         Rs2Inventory.get(slot).getId() != item.getId(), 100, 2000);
                }
            }
            
        } catch (Exception e) {
            Microbot.log("Error banking gear: " + e.getMessage());
        }
    }
    
    /**
     * Determines if a tool should be preserved in its current position.
     */
    private boolean shouldPreserveTool(int itemId, int slot, WintertodtAxeManager.AxeDecision axeDecision) {
        // Preserve knife in slot 27 (fletching)
        if (itemId == WintertodtInventoryManager.knifeToUse && slot == 27 && config.fletchRoots()) {
            return true;
        }
        
        // Preserve hammer in slot 26 (brazier repair)
        if (itemId == ItemID.HAMMER && slot == 26 && config.fixBrazier()) {
            return true;
        }
        
        // Preserve tinderbox in slot 25 (if no bruma torch)
        if (itemId == ItemID.TINDERBOX && slot == 25 && 
            !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) && 
            !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND)) {
            return true;
        }
        
        // Preserve axe in slot 24 (if should be in inventory)
        if (itemId == axeDecision.getAxeId() && slot == 24 && !axeDecision.shouldEquipAxe()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Handles bruma torch conversion if beneficial.
     */
    private void handleBrumaTorchConversion() {
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        if (axeDecision.needsBrumaTorchConversion()) {
            gearAnalysisLog.add("Converting bruma torch to offhand version...");
            if (WintertodtAxeManager.performBrumaTorchConversion()) {
                gearAnalysisLog.add("Successfully converted bruma torch");
            } else {
                gearAnalysisLog.add("X Failed to convert bruma torch");
            }
        }
    }
    
    /**
     * Ensures bank access is available.
     */
    private boolean ensureBankAccess() {
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Inventory.isOpen()) {
                Rs2Inventory.open();
            }
            if (!Rs2Bank.openBank()) {
                Microbot.log("Failed to open bank for gear setup");
                return false;
            }
            Rs2Player.waitForWalking();
            if (!Rs2Bank.isOpen()) {
                Microbot.log("Bank not open for gear setup");
                return false;
            }
        }
        return true;
    }
    
    /**
     * Logs detailed gear analysis results.
     */
    private void logDetailedGearAnalysis() {
        Microbot.log("=== DETAILED GEAR ANALYSIS RESULTS ===");
        
        int warmthItems = 0;
        int totalWeight = 0;
        int pyromancerPieces = 0;
        
        for (String logEntry : gearAnalysisLog) {
            Microbot.log(logEntry);
        }
        
        // Calculate summary statistics
        for (WintertodtGearItem item : optimalGear.values()) {
            if (item.providesWarmth()) warmthItems++;
            totalWeight += item.getWeight();
            if (item.getCategory() == WintertodtGearItem.GearCategory.PYROMANCER) pyromancerPieces++;
        }
        
        Microbot.log("=== GEAR SUMMARY ===");
        Microbot.log("Total gear pieces: " + optimalGear.size());
        Microbot.log("Warmth-providing items: " + warmthItems + "/4 (max cold protection)");
        Microbot.log("Pyromancer pieces: " + pyromancerPieces + "/4 (for XP bonus)");
        Microbot.log("Total weight reduction: " + Math.abs(totalWeight) + " kg");
        
        if (warmthItems >= 4) {
            Microbot.log("MAXIMUM COLD PROTECTION ACHIEVED");
        } else {
            Microbot.log("Only " + warmthItems + "/4 warmth items - consider getting more warm gear");
        }
        
        if (pyromancerPieces >= 4) {
            Microbot.log("Pyromancer XP bonus active ( 2.5% bonus)");
        }
    }
    
    /**
     * Resets the gear manager state.
     */
    public void reset() {
        optimalGear.clear();
        gearAnalysisLog.clear();
        Microbot.log("Gear manager reset - ready for fresh analysis");
    }
    
    /**
     * Gets the detailed gear analysis log.
     */
    public List<String> getGearAnalysisLog() {
        return new ArrayList<>(gearAnalysisLog);
    }
    
    /**
     * Gets summary of current optimal gear setup.
     */
    public Map<EquipmentInventorySlot, WintertodtGearItem> getOptimalGear() {
        return new HashMap<>(optimalGear);
    }
} 