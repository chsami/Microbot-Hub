package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PotionManagerScript extends Script {
    private static final Set<Integer> POWERED_STAFF_CATEGORIES = Set.of(24, 29);

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if(AIOFighterPlugin.getState() == State.GETTING_TASK) return;

                // Always attempt to drink anti-poison
                if (Rs2Player.drinkAntiPoisonPotion()) {
                    Rs2Player.waitForAnimation();
                }

                // Always attempt to drink antifire potion
                if (Rs2Player.drinkAntiFirePotion()) {
                    Rs2Player.waitForAnimation();
                }

                // Always attempt to drink prayer potion
                if (Rs2Player.drinkPrayerPotion()) {
                    Rs2Player.waitForAnimation();
                }

                // Only drink combat potions when in combat
                if (Rs2Combat.inCombat()) {
                    AttackStyle style = currentStyle(config.useMagic(),
                            Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY),
                            Rs2Combat.getWeaponAttackStyle());

                    for (Map.Entry<Skill, Boolean> potion : combatPotionsFor(style).entrySet()) {
                        if (Rs2Player.drinkCombatPotionAt(potion.getKey(), potion.getValue())) {
                            Rs2Player.waitForAnimation();
                        }
                    }
                }

                // Always attempt to drink goading potion
                if (Rs2Player.drinkGoadingPotion()) {
                    Rs2Player.waitForAnimation();
                }

                if(Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
                    Rs2Inventory.dropAll(ItemID.VIAL_EMPTY);
                    Rs2Inventory.waitForInventoryChanges(1000);
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    static AttackStyle currentStyle(boolean useMagic, int weaponCategory, String attackStyleName) {
        if (useMagic || POWERED_STAFF_CATEGORIES.contains(weaponCategory) || "Casting".equals(attackStyleName)) {
            return AttackStyle.MAGE;
        }
        if (attackStyleName == null) {
            return null;
        }
        if ("Ranging".equals(attackStyleName) || "Longrange".equals(attackStyleName)) {
            return AttackStyle.RANGED;
        }
        return AttackStyle.MELEE;
    }

    static Map<Skill, Boolean> combatPotionsFor(AttackStyle style) {
        Map<Skill, Boolean> potions = new LinkedHashMap<>();
        if (style == AttackStyle.RANGED) {
            potions.put(Skill.RANGED, false);
        }
        if (style == AttackStyle.MAGE) {
            potions.put(Skill.MAGIC, false);
        }
        if (style == AttackStyle.MELEE) {
            potions.put(Skill.STRENGTH, true);
            potions.put(Skill.ATTACK, true);
        }
        potions.put(Skill.DEFENCE, style == AttackStyle.MELEE);
        return potions;
    }


    // shutdown
    @Override
    public void shutdown() {
        super.shutdown();
    }

}
