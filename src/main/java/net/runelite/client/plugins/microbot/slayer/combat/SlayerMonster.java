package net.runelite.client.plugins.microbot.slayer.combat;

import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcStats;

/**
 * Tracks a monster attacking the player for prayer flicking purposes.
 */
public class SlayerMonster {
    // Default attack speed (4 ticks = 2.4 seconds) used when NPC data is unavailable
    private static final int DEFAULT_ATTACK_SPEED = 4;

    public Rs2NpcModel npc;
    public Rs2NpcStats rs2NpcStats;
    public AttackStyle attackStyle = AttackStyle.MELEE; // Default to melee
    public int lastAttack = 0;

    public SlayerMonster(Rs2NpcModel npc, Rs2NpcStats rs2NpcStats) {
        this.npc = npc;
        this.rs2NpcStats = rs2NpcStats;
        int attackSpeed = Rs2NpcManager.getAttackSpeed(npc.getId());
        // Use default if attack speed is invalid
        this.lastAttack = (attackSpeed > 0) ? attackSpeed : DEFAULT_ATTACK_SPEED;
    }

    /**
     * Gets the attack speed, using default if stats unavailable
     */
    public int getAttackSpeed() {
        if (rs2NpcStats != null && rs2NpcStats.getAttackSpeed() > 0) {
            return rs2NpcStats.getAttackSpeed();
        }
        return DEFAULT_ATTACK_SPEED;
    }
}
