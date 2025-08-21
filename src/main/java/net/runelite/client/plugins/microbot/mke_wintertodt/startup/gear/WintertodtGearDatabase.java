package net.runelite.client.plugins.microbot.mke_wintertodt.startup.gear;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.api.EquipmentInventorySlot.*;

/**
 * Comprehensive database of all possible gear items for Wintertodt optimization.
 * Items are ranked by priority with Pyromancer gear being the best, followed by
 * warm clothing, then other utility gear.
 * 
 * Priority System:
 * - 1000+: Pyromancer gear (Best in slot)
 * - 800-999: Bruma torch and special Wintertodt items
 * - 600-799: Warm clothing items
 * - 400-599: Graceful outfit (weight reduction)
 * - 200-399: High-level combat gear
 * - 100-199: Mid-level gear
 * - 0-99: Basic gear
 * 
 * @author MakeCD
 * @version 1.1.1
 */
public class WintertodtGearDatabase {
    
    private final Map<EquipmentInventorySlot, List<WintertodtGearItem>> gearBySlot;
    private final List<WintertodtGearItem> allGearItems;
    
    public WintertodtGearDatabase() {
        this.allGearItems = createGearDatabase();
        this.gearBySlot = allGearItems.stream()
                .collect(Collectors.groupingBy(WintertodtGearItem::getSlot));
    }
    
    /**
     * Creates the complete gear database with all possible items.
     */
    private List<WintertodtGearItem> createGearDatabase() {
        List<WintertodtGearItem> items = new ArrayList<>();
        
        // HEAD SLOT ITEMS
        items.addAll(createHeadGear());
        
        // BODY SLOT ITEMS  
        items.addAll(createBodyGear());
        
        // LEGS SLOT ITEMS
        items.addAll(createLegsGear());
        
        // FEET SLOT ITEMS
        items.addAll(createFeetGear());
        
        // HANDS SLOT ITEMS
        items.addAll(createHandsGear());
        
        // WEAPON SLOT ITEMS
        items.addAll(createWeaponGear());
        
        // SHIELD/OFFHAND SLOT ITEMS
        items.addAll(createShieldGear());
        
        // NECK SLOT ITEMS
        items.addAll(createNeckGear());
        
        // RING SLOT ITEMS
        items.addAll(createRingGear());
        
        // CAPE SLOT ITEMS
        items.addAll(createCapeGear());
        
        // Sort all items by effective priority (highest first)
        items.sort((a, b) -> Integer.compare(b.getEffectivePriority(), a.getEffectivePriority()));
        
        return items;
    }
    
    private List<WintertodtGearItem> createHeadGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_HOOD, "Pyromancer hood", HEAD)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best headgear for Wintertodt")
                .build(),
                
            // High-tier warm headgear (800-899)
            new WintertodtGearItem.Builder(ItemID.FIRE_MAX_HOOD, "Fire max hood", HEAD)
                .priority(850).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Fire max hood - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIREMAKING_HOOD, "Firemaking hood", HEAD)
                .priority(840).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.FIREMAKING, 99)
                .description("Firemaking skill hood - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.INFERNAL_MAX_HOOD, "Infernal max hood", HEAD)
                .priority(830).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Infernal max hood - provides warmth")
                .build(),
                
            // Slayer helmets (820-829) - Recently added as warm clothing (Jan 2025)
            new WintertodtGearItem.Builder(ItemID.SLAYER_HELMET, "Slayer helmet", HEAD)
                .priority(820).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.SLAYER_HELMET_I, "Slayer helmet (i)", HEAD)
                .priority(825).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Imbued slayer helmet - provides warmth")
                .build(),
                
            // Add other slayer helmet variants (820-829)
            new WintertodtGearItem.Builder(ItemID.BLACK_SLAYER_HELMET, "Black slayer helmet", HEAD)
                .priority(822).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Black slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BLACK_SLAYER_HELMET_I, "Black slayer helmet (i)", HEAD)
                .priority(823).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Black slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GREEN_SLAYER_HELMET, "Green slayer helmet", HEAD)
                .priority(821).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Green slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GREEN_SLAYER_HELMET_I, "Green slayer helmet (i)", HEAD)
                .priority(822).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Green slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RED_SLAYER_HELMET, "Red slayer helmet", HEAD)
                .priority(821).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Red slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RED_SLAYER_HELMET_I, "Red slayer helmet (i)", HEAD)
                .priority(822).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Red slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.PURPLE_SLAYER_HELMET, "Purple slayer helmet", HEAD)
                .priority(821).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Purple slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.PURPLE_SLAYER_HELMET_I, "Purple slayer helmet (i)", HEAD)
                .priority(822).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Purple slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TURQUOISE_SLAYER_HELMET, "Turquoise slayer helmet", HEAD)
                .priority(821).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Turquoise slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TURQUOISE_SLAYER_HELMET_I, "Turquoise slayer helmet (i)", HEAD)
                .priority(822).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Turquoise slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.HYDRA_SLAYER_HELMET, "Hydra slayer helmet", HEAD)
                .priority(824).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Hydra slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.HYDRA_SLAYER_HELMET_I, "Hydra slayer helmet (i)", HEAD)
                .priority(825).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Hydra slayer helmet (i) - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TWISTED_SLAYER_HELMET, "Twisted slayer helmet", HEAD)
                .priority(824).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Twisted slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TWISTED_SLAYER_HELMET_I, "Twisted slayer helmet (i)", HEAD)
                .priority(825).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .levelRequirement(Skill.SLAYER, 60)
                .description("Twisted slayer helmet (i) - provides warmth")
                .build(),
                
            // Snow/Winter special headgear (800-819)
            new WintertodtGearItem.Builder(ItemID.SNOW_GOGGLES__HAT, "Snow goggles & hat", HEAD)
                .priority(810).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Snow goggles & hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.SNOWGLOBE_HELMET, "Snowglobe helmet", HEAD)
                .priority(805).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Snowglobe helmet - provides warmth")
                .build(),
                
            // Santa outfit (750-759)
            new WintertodtGearItem.Builder(ItemID.SANTA_HAT, "Santa hat", HEAD)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Santa hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BLACK_SANTA_HAT, "Black santa hat", HEAD)
                .priority(748).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Black santa hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.INVERTED_SANTA_HAT, "Inverted santa hat", HEAD)
                .priority(746).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Inverted santa hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FESTIVE_ELF_HAT, "Festive elf hat", HEAD)
                .priority(745).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Festive elf hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FESTIVE_GAMES_CROWN, "Festive games crown", HEAD)
                .priority(744).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Festive games crown - provides warmth")
                .build(),
                
            // Antisanta outfit (740-749)
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_MASK, "Anti-santa mask", HEAD)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Anti-santa mask - provides warmth")
                .build(),
                
            // Hunter/Animal gear (700-739)
            new WintertodtGearItem.Builder(ItemID.LARUPIA_HAT, "Larupia hat", HEAD)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .levelRequirement(Skill.HUNTER, 28)
                .description("Larupia hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GRAAHK_HEADDRESS, "Graahk headdress", HEAD)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .levelRequirement(Skill.HUNTER, 38)
                .description("Graahk headdress - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.KYATT_HAT, "Kyatt hat", HEAD)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .levelRequirement(Skill.HUNTER, 52)
                .description("Kyatt hat - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_GARB, "Clue hunter helmet", HEAD)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter helmet - provides warmth")
                .build(),
                
            // Festive/Winter gear (660-679)
            new WintertodtGearItem.Builder(ItemID.WOLF_MASK, "Wolf mask", HEAD)
                .priority(675).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Wolf mask - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BEARHEAD, "Bearhead", HEAD)
                .priority(670).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bearhead - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WOOLLY_HAT, "Woolly hat", HEAD)
                .priority(665).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Woolly hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOBBLE_HAT, "Bobble hat", HEAD)
                .priority(663).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bobble hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EARMUFFS, "Earmuffs", HEAD)
                .priority(661).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Earmuffs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.JESTER_HAT, "Jester hat", HEAD)
                .priority(660).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Jester hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TRIJESTER_HAT, "Tri-jester hat", HEAD)
                .priority(659).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Tri-jester hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.CAP_AND_GOGGLES, "Cap and goggles", HEAD)
                .priority(658).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Cap and goggles - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOMBER_CAP, "Bomber cap", HEAD)
                .priority(657).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bomber cap - provides warmth")
                .build(),
                
            // Special tiara gear (650-659)
            new WintertodtGearItem.Builder(ItemID.FIRE_TIARA, "Fire tiara", HEAD)
                .priority(650).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Fire tiara - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ELEMENTAL_TIARA, "Elemental tiara", HEAD)
                .priority(648).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Elemental tiara - provides warmth")
                .build(),
                
            // Skill hoods (600-649)
            new WintertodtGearItem.Builder(ItemID.LUMBERJACK_HAT, "Lumberjack hat", HEAD)
                .priority(630).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Lumberjack hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FORESTRY_HAT, "Forestry hat", HEAD)
                .priority(628).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Forestry hat - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_HOOD, "Graceful hood", HEAD)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-3).untradeable()
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat helms (300-399)   
            new WintertodtGearItem.Builder(ItemID.FIGHTER_HAT, "Fighter hat", HEAD)
                .priority(360).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .untradeable()
                .description("Good defensive helm - no warmth")
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_FULL_HELM, "Rune full helm", HEAD)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_MED_HELM, "Rune med helm", HEAD)
                .priority(145).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_COWL, "Leather cowl", HEAD)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createBodyGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_GARB, "Pyromancer garb", BODY)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best body armor for Wintertodt")
                .build(),
                
            // Warm jumpers and special body gear (750-799)
            new WintertodtGearItem.Builder(ItemID.CHRISTMAS_JUMPER, "Christmas jumper", BODY)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Christmas jumper - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.OLDSCHOOL_JUMPER, "Oldschool jumper", BODY)
                .priority(778).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Oldschool jumper - provides warmth (added Mar 2023)")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RAINBOW_JUMPER, "Rainbow jumper", BODY)
                .priority(776).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Rainbow jumper - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ICY_JUMPER, "Icy jumper", BODY)
                .priority(774).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Icy jumper - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.UGLY_HALLOWEEN_JUMPER_BLACK, "Ugly halloween jumper black", BODY)
                .priority(772).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Ugly halloween jumper - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.UGLY_HALLOWEEN_JUMPER_ORANGE, "Ugly halloween jumper orange", BODY)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Ugly halloween jumper - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.SCARECROW_SHIRT, "Scarecrow shirt", BODY)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Scarecrow shirt - provides warmth")
                .build(),
                
            // Santa outfit
            new WintertodtGearItem.Builder(ItemID.SANTA_JACKET, "Santa jacket", BODY)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa jacket - provides warmth")
                .build(),
                
            // Antisanta outfit
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_JACKET, "Antisanta jacket", BODY)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta jacket - provides warmth")
                .build(),
                
            // Hunter gear (700-739)
            new WintertodtGearItem.Builder(ItemID.KYATT_TOP, "Kyatt top", BODY)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 52)
                .providesWarmth()
                .description("Kyatt top - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.GRAAHK_TOP, "Graahk top", BODY)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 38)
                .providesWarmth()
                .description("Graahk top - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.LARUPIA_TOP, "Larupia top", BODY)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 28)
                .providesWarmth()
                .description("Larupia top - provides warmth")
                .build(),
                
            // Animal costumes (680-699)
            new WintertodtGearItem.Builder(ItemID.CHICKEN_WINGS, "Chicken wings", BODY)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken wings - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_WINGS, "Evil chicken wings", BODY)
                .priority(688).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken wings - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_TOP, "Bunny top", BODY)
                .priority(685).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny top - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_TOP, "Graceful top", BODY)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-5).untradeable()
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat armor (300-399)
            new WintertodtGearItem.Builder(ItemID.BANDOS_CHESTPLATE, "Bandos chestplate", BODY)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 65)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIGHTER_TORSO, "Fighter torso", BODY)
                .priority(360).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .untradeable()
                .description("Good strength bonus - no warmth")
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_CHAINBODY, "Rune chainbody", BODY)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATEBODY, "Rune platebody", BODY)
                .priority(145).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_BODY, "Leather body", BODY)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createLegsGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_ROBE, "Pyromancer robe", LEGS)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best legs for Wintertodt")
                .build(),
                
            // Santa outfit
            new WintertodtGearItem.Builder(ItemID.SANTA_PANTALOONS, "Santa pantaloons", LEGS)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa pantaloons - provides warmth")
                .build(),
                
            // Antisanta outfit
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_PANTALOONS, "Antisanta pantaloons", LEGS)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta pantaloons - provides warmth")
                .build(),
                
            // Hunter gear (700-739)
            new WintertodtGearItem.Builder(ItemID.KYATT_LEGS, "Kyatt legs", LEGS)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 52)
                .providesWarmth()
                .description("Kyatt legs - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.GRAAHK_LEGS, "Graahk legs", LEGS)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 38)
                .providesWarmth()
                .description("Graahk legs - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.LARUPIA_LEGS, "Larupia legs", LEGS)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 28)
                .providesWarmth()
                .description("Larupia legs - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_TROUSERS, "Clue hunter trousers", LEGS)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter trousers - provides warmth")
                .build(),
                
            // Animal costumes
            new WintertodtGearItem.Builder(ItemID.CHICKEN_LEGS, "Chicken legs", LEGS)
                .priority(685).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_LEGS, "Evil chicken legs", LEGS)
                .priority(683).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_LEGS, "Bunny legs", LEGS)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny legs - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_LEGS, "Graceful legs", LEGS)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat legs (300-399)
            new WintertodtGearItem.Builder(ItemID.BANDOS_TASSETS, "Bandos tassets", LEGS)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 65)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATELEGS, "Rune platelegs", LEGS)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATESKIRT, "Rune plateskirt", LEGS)
                .priority(148).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createFeetGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_BOOTS, "Pyromancer boots", BOOTS)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best boots for Wintertodt")
                .build(),
                
            // Special warm boots (750-799)
            new WintertodtGearItem.Builder(ItemID.FESTIVE_ELF_SLIPPERS, "Festive elf slippers", BOOTS)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Festive elf slippers - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOB_THE_CAT_SLIPPERS, "Bob the cat slippers", BOOTS)
                .priority(775).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bob the cat slippers - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.JAD_SLIPPERS, "Jad slippers", BOOTS)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Jad slippers - provides warmth")
                .build(),
                
            // Santa outfit (760-769)
            new WintertodtGearItem.Builder(ItemID.SANTA_BOOTS, "Santa boots", BOOTS)
                .priority(760).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa boots - provides warmth")
                .build(),
                
            // Antisanta outfit
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_BOOTS, "Antisanta boots", BOOTS)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta boots - provides warmth")
                .build(),
                
            // Animal costume feet (700-749)
            new WintertodtGearItem.Builder(ItemID.CHICKEN_FEET, "Chicken feet", BOOTS)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_FEET, "Evil chicken feet", BOOTS)
                .priority(738).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_FEET, "Bunny feet", BOOTS)
                .priority(735).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BEAR_FEET, "Bear feet", BOOTS)
                .priority(730).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Bear feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.DEMON_FEET, "Demon feet", BOOTS)
                .priority(728).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Demon feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.MOLE_SLIPPERS, "Mole slippers", BOOTS)
                .priority(725).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Mole slippers - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FROG_SLIPPERS, "Frog slippers", BOOTS)
                .priority(723).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Frog slippers - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_BOOTS, "Clue hunter boots", BOOTS)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter boots - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_BOOTS, "Graceful boots", BOOTS)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .levelRequirement(Skill.AGILITY, 20)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat boots (300-399) - NO warmth
            new WintertodtGearItem.Builder(ItemID.BANDOS_BOOTS, "Bandos boots", BOOTS)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 65)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.DRAGON_BOOTS, "Dragon boots", BOOTS)
                .priority(350).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 60)
                .description("Good strength bonus - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.CLIMBING_BOOTS, "Climbing boots", BOOTS)
                .priority(320).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .questRequirement("Death Plateau")
                .description("Basic strength bonus - no warmth")
                .build(),
                
            // Basic gear - NO warmth
            new WintertodtGearItem.Builder(ItemID.RUNE_BOOTS, "Rune boots", BOOTS)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .description("Mid-level combat boots - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ADAMANT_BOOTS, "Adamant boots", BOOTS)
                .priority(120).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 30)
                .description("Mid-level combat boots - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_BOOTS, "Leather boots", BOOTS)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Basic boots - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createHandsGear() {
        return Arrays.asList(
            // Regen Bracelet
            new WintertodtGearItem.Builder(ItemID.REGEN_BRACELET, "Regen bracelet", GLOVES)
                .priority(1000).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Best gloves for Wintertodt, warmth meter regens faster, no warmth")
                .build(),

            // Pyromancer gear
            new WintertodtGearItem.Builder(ItemID.WARM_GLOVES, "Warm gloves", GLOVES)
                .priority(900).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .levelRequirement(Skill.FIREMAKING, 50)
                .providesWarmth().untradeable()
                .description("Pyromancer gloves - provides warmth")
                .build(),
                
            // Santa/Antisanta gloves
            new WintertodtGearItem.Builder(ItemID.SANTA_GLOVES, "Santa gloves", GLOVES)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_GLOVES, "Antisanta gloves", GLOVES)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta gloves - provides warmth")
                .build(),
                
            // Special warm gloves (720-730)
            new WintertodtGearItem.Builder(ItemID.GLOVES_OF_SILENCE, "Gloves of silence", GLOVES)
                .priority(725).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HUNTER, 54)
                .providesWarmth()
                .description("Gloves of silence - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FREMENNIK_GLOVES, "Fremennik gloves", GLOVES)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .questRequirement("The Fremennik Trials")
                .providesWarmth()
                .description("Fremennik gloves - provides warmth")
                .build(),
                
            // Colored gloves (700-719) - All tradeable
            new WintertodtGearItem.Builder(ItemID.RED_GLOVES, "Red gloves", GLOVES)
                .priority(710).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Red gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.YELLOW_GLOVES, "Yellow gloves", GLOVES)
                .priority(708).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Yellow gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.PURPLE_GLOVES, "Purple gloves", GLOVES)
                .priority(706).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Purple gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GREY_GLOVES, "Grey gloves", GLOVES)
                .priority(704).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Grey gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TEAL_GLOVES, "Teal gloves", GLOVES)
                .priority(702).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Teal gloves - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_GLOVES, "Clue hunter gloves", GLOVES)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter gloves - provides warmth")
                .build(),
                
            // Animal costume hands
            new WintertodtGearItem.Builder(ItemID.BUNNY_PAWS, "Bunny paws", GLOVES)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny paws - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_GLOVES, "Graceful gloves", GLOVES)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-3).untradeable()
                .levelRequirement(Skill.AGILITY, 20)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level gloves (300-399) - NO warmth
            new WintertodtGearItem.Builder(ItemID.BARROWS_GLOVES, "Barrows gloves", GLOVES)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .questRequirement("Recipe for Disaster")
                .untradeable()
                .description("Best overall gloves - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_GLOVES, "Rune gloves", GLOVES)
                .priority(300).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .questRequirement("Recipe for Disaster")
                .untradeable()
                .description("High-level combat gloves - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_GLOVES, "Leather gloves", GLOVES)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Basic gloves - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createWeaponGear() {
        return Arrays.asList(
            // Bruma torch (Best for Wintertodt)
            new WintertodtGearItem.Builder(ItemID.BRUMA_TORCH, "Bruma torch", WEAPON)
                .priority(950).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 50)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Acts as tinderbox, light source, and provides warmth.")
                .build(),
                
            // Infernal Axe (Excellent tool that provides warmth)
            new WintertodtGearItem.Builder(ItemID.INFERNAL_AXE, "Infernal axe", WEAPON)
                .priority(900).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 61)
                .levelRequirement(Skill.FIREMAKING, 85)
                .levelRequirement(Skill.ATTACK, 60)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Burns logs while cutting, provides warmth.")
                .build(),
                
            // --- REGULAR AXES (Essential Tools - NO WARMTH) ---
            new WintertodtGearItem.Builder(ItemID.DRAGON_AXE, "Dragon axe", WEAPON)
                .priority(850).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 61)
                .levelRequirement(Skill.ATTACK, 60)
                .hasSpecialEffect()
                .description("Fastest axe, requires 60 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.RUNE_AXE, "Rune axe", WEAPON)
                .priority(830).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 41)
                .levelRequirement(Skill.ATTACK, 40)
                .description("Good axe, requires 40 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.ADAMANT_AXE, "Adamant axe", WEAPON)
                .priority(810).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 31)
                .levelRequirement(Skill.ATTACK, 30)
                .description("Decent axe, requires 30 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.MITHRIL_AXE, "Mithril axe", WEAPON)
                .priority(800).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 21)
                .levelRequirement(Skill.ATTACK, 20)
                .description("Requires 20 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STEEL_AXE, "Steel axe", WEAPON)
                .priority(790).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 6)
                .levelRequirement(Skill.ATTACK, 5)
                .description("Requires 5 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.IRON_AXE, "Iron axe", WEAPON)
                .priority(780).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 1)
                .levelRequirement(Skill.ATTACK, 1)
                .description("Basic axe, requires 1 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.BRONZE_AXE, "Bronze axe", WEAPON)
                .priority(770).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 1)
                .description("Most basic axe, no Attack requirement.")
                .build(),

            // --- WARM WEAPONS (Lower priority than axes) ---
            new WintertodtGearItem.Builder(ItemID.INFERNAL_PICKAXE, "Infernal pickaxe", WEAPON)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MINING, 61)
                .levelRequirement(Skill.ATTACK, 60)
                .providesWarmth().untradeable()
                .description("Provides warmth, but is a pickaxe.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.LAVA_BATTLESTAFF, "Lava battlestaff", WEAPON)
                .priority(700).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 30)
                .levelRequirement(Skill.ATTACK, 30)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIRE_BATTLESTAFF, "Fire battlestaff", WEAPON)
                .priority(698).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 30)
                .levelRequirement(Skill.ATTACK, 30)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STEAM_BATTLESTAFF, "Steam battlestaff", WEAPON)
                .priority(696).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 30)
                .levelRequirement(Skill.ATTACK, 30)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.SMOKE_BATTLESTAFF, "Smoke battlestaff", WEAPON)
                .priority(694).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 30)
                .levelRequirement(Skill.ATTACK, 30)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_LAVA_STAFF, "Mystic lava staff", WEAPON)
                .priority(692).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 40)
                .levelRequirement(Skill.ATTACK, 40)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_FIRE_STAFF, "Mystic fire staff", WEAPON)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 40)
                .levelRequirement(Skill.ATTACK, 40)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_STEAM_STAFF, "Mystic steam staff", WEAPON)
                .priority(688).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 40)
                .levelRequirement(Skill.ATTACK, 40)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.MYSTIC_SMOKE_STAFF, "Mystic smoke staff", WEAPON)
                .priority(686).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MAGIC, 40)
                .levelRequirement(Skill.ATTACK, 40)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STAFF_OF_FIRE, "Staff of fire", WEAPON)
                .priority(684).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.INFERNAL_HARPOON, "Infernal harpoon", WEAPON)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.FISHING, 70)
                .levelRequirement(Skill.ATTACK, 60)
                .providesWarmth().untradeable()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.VOLCANIC_ABYSSAL_WHIP, "Volcanic abyssal whip", WEAPON)
                .priority(670).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.ATTACK, 70)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.ALE_OF_THE_GODS, "Ale of the gods", WEAPON)
                .priority(660).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.DRAGON_CANDLE_DAGGER, "Dragon candle dagger", WEAPON)
                .priority(655).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.ATTACK, 60)
                .providesWarmth().untradeable()
                .description("Dragon candle dagger - provides warmth (added Mar 2023)")
                .build(),

            // FASHIONSCAPE/UTILITY WEAPONS
            new WintertodtGearItem.Builder(ItemID.RUBBER_CHICKEN, "Rubber chicken", WEAPON)
                .priority(300).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .untradeable()
                .description("Funny fashionscape item")
                .build(),

            new WintertodtGearItem.Builder(ItemID.GOLDEN_TENCH, "Golden tench", WEAPON)
                .priority(290).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .untradeable()
                .description("Fish as weapon - pure fashionscape")
                .build(),

            // BASIC WEAPONS (Fallback)
            new WintertodtGearItem.Builder(ItemID.IRON_SCIMITAR, "Iron scimitar", WEAPON)
                .priority(80).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.ATTACK, 1)
                .description("Basic weapon if no axe available")
                .build(),

            new WintertodtGearItem.Builder(ItemID.BRONZE_SCIMITAR, "Bronze scimitar", WEAPON)
                .priority(60).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Most basic weapon")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createShieldGear() {
        return Arrays.asList(
            // Bruma torch offhand (Best for Wintertodt)
            new WintertodtGearItem.Builder(ItemID.BRUMA_TORCH_OFFHAND, "Bruma torch (off-hand)", SHIELD)
                .priority(900).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 50)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Acts as both tinderbox and light source - provides warmth")
                .build(),
                
            // Tome of fire
            new WintertodtGearItem.Builder(ItemID.TOME_OF_FIRE, "Tome of fire", SHIELD)
                .priority(850).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.MAGIC, 50)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Firemaking experience bonus - provides warmth")
                .build(),
                
            // Lit bug lantern
            new WintertodtGearItem.Builder(ItemID.LIT_BUG_LANTERN, "Lit bug lantern", SHIELD)
                .priority(820).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.SLAYER, 33)
                .providesWarmth().untradeable()
                .description("Lit bug lantern - provides warmth")
                .build(),
                
            // High-level shields - NO warmth
            new WintertodtGearItem.Builder(ItemID.DRAGON_DEFENDER, "Dragon defender", SHIELD)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 60)
                .untradeable()
                .description("Best defender - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_DEFENDER, "Rune defender", SHIELD)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .untradeable()
                .description("Mid-tier defender - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_KITESHIELD, "Rune kiteshield", SHIELD)
                .priority(140).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .description("Mid-tier combat shield - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createNeckGear() {
        return Arrays.asList(
            // High-priority warm necklaces (700-799)
            new WintertodtGearItem.Builder(ItemID.GNOME_SCARF, "Gnome scarf", AMULET)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Gnome scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RAINBOW_SCARF, "Rainbow scarf", AMULET)
                .priority(745).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Rainbow scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FESTIVE_SCARF, "Festive scarf", AMULET)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Festive scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.JESTER_SCARF, "Jester scarf", AMULET)
                .priority(735).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Jester scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TRIJESTER_SCARF, "Tri-jester scarf", AMULET)
                .priority(730).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Tri-jester scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WOOLLY_SCARF, "Woolly scarf", AMULET)
                .priority(725).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Woolly scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOBBLE_SCARF, "Bobble scarf", AMULET)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bobble scarf - provides warmth")
                .build(),
                
            // High-level utility amulets (400-500) - NO warmth
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_FURY, "Amulet of fury", AMULET)
                .priority(450).category(WintertodtGearItem.GearCategory.UTILITY)
                .description("Excellent all-around amulet - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_TORTURE, "Amulet of torture", AMULET)
                .priority(430).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Strength bonus - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.OCCULT_NECKLACE, "Occult necklace", AMULET)
                .priority(420).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Magic damage bonus - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.NECKLACE_OF_ANGUISH, "Necklace of anguish", AMULET)
                .priority(410).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Ranged bonus - no warmth")
                .build(),
                
            // Glory variants (340-399) - NO warmth
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY6, "Amulet of glory(6)", AMULET)
                .priority(360).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation amulet - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY4, "Amulet of glory(4)", AMULET)
                .priority(350).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation amulet - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY3, "Amulet of glory(3)", AMULET)
                .priority(345).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation amulet - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY2, "Amulet of glory(2)", AMULET)
                .priority(340).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation amulet - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY1, "Amulet of glory(1)", AMULET)
                .priority(335).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation amulet - no warmth")
                .build(),
                
            // Games necklace - NO warmth
            new WintertodtGearItem.Builder(ItemID.GAMES_NECKLACE8, "Games necklace(8)", AMULET)
                .priority(440).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Wintertodt teleport - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_POWER, "Amulet of power", AMULET)
                .priority(250).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Balanced combat stats - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_STRENGTH, "Amulet of strength", AMULET)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Strength bonus - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createRingGear() {
        return Arrays.asList(
            // Ring of the elements (warm ring)
            new WintertodtGearItem.Builder(ItemID.RING_OF_THE_ELEMENTS, "Ring of the elements", RING)
                .priority(800).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Ring of the elements - provides warmth")
                .build(),
                
            // Utility rings (400-500) - NO warmth
            new WintertodtGearItem.Builder(ItemID.RING_OF_DUELING8, "Ring of dueling(8)", RING)
                .priority(450).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Useful teleportations - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RING_OF_DUELING7, "Ring of dueling(7)", RING)
                .priority(445).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Useful teleportations - no warmth")
                .build(),
                
            // Combat rings (300-399) - NO warmth
            new WintertodtGearItem.Builder(ItemID.BERSERKER_RING, "Berserker ring", RING)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Strength bonus - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WARRIOR_RING, "Warrior ring", RING)
                .priority(350).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Attack bonus - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createCapeGear() {
        return Arrays.asList(
            // Fire-themed capes (850-899) - All provide warmth
            new WintertodtGearItem.Builder(ItemID.INFERNAL_MAX_CAPE, "Infernal max cape", CAPE)
                .priority(899).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HITPOINTS, 99)
                .untradeable().providesWarmth()
                .description("Infernal max cape - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.INFERNAL_CAPE, "Infernal cape", CAPE)
                .priority(890).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Infernal cape - provides warmth and excellent stats")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIRE_MAX_CAPE, "Fire max cape", CAPE)
                .priority(885).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HITPOINTS, 99)
                .untradeable().providesWarmth()
                .description("Fire max cape - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIRE_CAPE, "Fire cape", CAPE)
                .priority(875).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Fire cape - provides warmth and excellent melee bonuses")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.OBSIDIAN_CAPE, "Obsidian cape", CAPE)
                .priority(860).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Obsidian cape - provides warmth")
                .build(),
                
            // Max cape 880
            new WintertodtGearItem.Builder(ItemID.MAX_CAPE, "Max cape", CAPE)
                .priority(880).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HITPOINTS, 99) // Represents 99 in all skills
                .untradeable().providesWarmth()
                .description("Max cape - provides warmth")
                .build(),
                
            // Skill capes (800-859)
            new WintertodtGearItem.Builder(ItemID.HITPOINTS_CAPET, "Hitpoints cape (t)", CAPE)
                .priority(879).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HITPOINTS, 99)
                .untradeable()
                .description("Hitpoints cape (t) - warmth regens faster")
                .build(),

            new WintertodtGearItem.Builder(ItemID.HITPOINTS_CAPE, "Hitpoints cape", CAPE)
                .priority(878).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.HITPOINTS, 99)
                .untradeable().providesWarmth()
                .description("Hitpoints cape - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIREMAKING_CAPET, "Firemaking cape (t)", CAPE)
                .priority(865).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.FIREMAKING, 99)
                .untradeable().providesWarmth()
                .description("Firemaking cape (t) - provides warmth")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIREMAKING_CAPE, "Firemaking cape", CAPE)
                .priority(864).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 99)
                .untradeable().providesWarmth()
                .description("Firemaking cape - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WOODCUTTING_CAPE, "Woodcutting cape", CAPE)
                .priority(830).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 99)
                .untradeable()
                .description("Useful for Wintertodt - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FLETCHING_CAPE, "Fletching cape", CAPE)
                .priority(820).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FLETCHING, 99)
                .untradeable()
                .description("Useful for Wintertodt - no warmth")
                .build(),
                
            // Special warm capes (750-799)
            new WintertodtGearItem.Builder(ItemID.WOLF_CLOAK, "Wolf cloak", CAPE)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Wolf cloak - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RAINBOW_CAPE, "Rainbow cape", CAPE)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Rainbow cape - provides warmth")
                .build(),
                
            // Clue hunter cloak
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_CLOAK, "Clue hunter cloak", CAPE)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter cloak - provides warmth")
                .build(),
                
            // Graceful cape (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_CAPE, "Graceful cape", CAPE)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // Basic capes (50-200) - NO warmth
            new WintertodtGearItem.Builder(ItemID.PINK_CAPE, "Pink cape", CAPE)
                .priority(50).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .description("Basic cosmetic cape - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BLACK_CAPE, "Black cape", CAPE)
                .priority(40).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Basic combat cape - no warmth")
                .build()
        );
    }
    
    /**
     * Gets all gear items for a specific equipment slot.
     */
    public List<WintertodtGearItem> getGearForSlot(EquipmentInventorySlot slot) {
        return gearBySlot.getOrDefault(slot, new ArrayList<>());
    }
    
    /**
     * Gets all gear items in the database.
     */
    public List<WintertodtGearItem> getAllGearItems() {
        return new ArrayList<>(allGearItems);
    }
    
    /**
     * Finds a specific gear item by ID.
     */
    public WintertodtGearItem findGearItemById(int itemId) {
        return allGearItems.stream()
                .filter(item -> item.getItemId() == itemId)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets gear items filtered by category.
     */
    public List<WintertodtGearItem> getGearByCategory(WintertodtGearItem.GearCategory category) {
        return allGearItems.stream()
                .filter(item -> item.getCategory() == category)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the total number of gear items in the database.
     */
    public int getTotalGearCount() {
        return allGearItems.size();
    }
} 