package net.runelite.client.plugins.microbot.royaltitans;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public class RoyalTitansShared {

    public static final Integer ICE_TITAN_ID = 14147;
    public static final Integer FIRE_TITAN_DEAD_ID = 14148;
    public static final Integer ICE_TITAN_DEAD_ID = 14149;
    public static final Integer FIRE_TITAN_ID = 12596;
    public static final int BOSS_REGION = 11669;
    public static final String[] ITEMS_TO_LOOT = new String[] {
            "Giantsoul amulet",
            "Fire element staff crown",
            "Ice element staff crown",
            "Mystic vigour prayer scroll",
            "Deadeye prayer scroll",
            "Mystic fire staff",
            "Mystic water staff",
            "Fire battlestaff",
            "Water battlestaff",
            "Rune plateskirt",
            "Rune platelegs",
            "Rune scimitar",
            "Rune pickaxe",
            "Rune sq shield",
            "Rune axe",
            "Chaos rune",
            "Death rune",
            "Nature rune",
            "Law rune",
            "Soul rune",
            "Blood rune",
            "Rune arrow",
            "Fire rune",
            "Water rune",
            "Gold ore",
            "Fire orb",
            "Water orb",
            "Coal",
            "Grimy avantoe",
            "Grimy cadantine",
            "Grimy dwarf weed",
            "Grimy irit leaf",
            "Grimy kwuarm",
            "Grimy lantadyme",
            "Grimy ranarr weed",
            "Avantoe seed",
            "Cadantine seed",
            "Dwarf weed seed",
            "Irit seed",
            "Kwuarm seed",
            "Lantadyme seed",
            "Ranarr seed",
            "Maple seed",
            "Palm tree seed",
            "Yew seed",
            "Coins",
            "Prayer potion(4)",
            "Desiccated page",
            "Clue scroll (hard)",
            "Clue scroll (elite)",
            "Bran"
    };

    public static void evaluateAndConsumePotions(RoyalTitansConfig config) {
        int threshold = config.boostedStatsThreshold();

        if (!isCombatPotionActive(threshold)) {
            consumePotion(Rs2Potion.getCombatPotionsVariants());
            if (Rs2Player.drinkCombatPotionAt(Skill.STRENGTH)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.ATTACK)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.DEFENCE)) {
                Rs2Player.waitForAnimation();
            }
        }

        if (!isRangingPotionActive(threshold)) {
            consumePotion(Rs2Potion.getRangePotionsVariants());
        }
    }

    public static boolean isInBossRegion() {
        return Rs2Player.getWorldLocation().getRegionID() == BOSS_REGION;
    }

    private static boolean isCombatPotionActive(int threshold) {
        return Rs2Player.hasDivineCombatActive() || (Rs2Player.hasAttackActive(threshold) && Rs2Player.hasStrengthActive(threshold));
    }

    private static boolean isRangingPotionActive(int threshold) {
        return Rs2Player.hasRangingPotionActive(threshold) || Rs2Player.hasDivineBastionActive() || Rs2Player.hasDivineRangedActive();
    }

    private static void consumePotion(List<String> keyword) {
        var potion = Rs2Inventory.get(keyword.toArray(String[]::new));
        if (potion != null) {
            Rs2Inventory.interact(potion, "Drink");
            Rs2Player.waitForAnimation(1200);
        }
    }
}
