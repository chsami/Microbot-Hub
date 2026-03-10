package net.runelite.client.plugins.microbot.accountbuilder.task.tasks.skills;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;
import net.runelite.client.plugins.microbot.accountbuilder.task.AbstractTask;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Trains Attack to the target level by fighting chickens at Lumbridge farm.
 *
 * <p>Chickens are easy low-level targets with no food required up to moderate levels.
 * Location: Lumbridge chicken farm south-east of the castle (~3236, 3295, 0).
 */
@Slf4j
public class TrainAttackTask extends AbstractTask {

    private static final WorldPoint CHICKEN_FARM = new WorldPoint(3236, 3295, 0);
    private static final String CHICKEN_NPC = "Chicken";
    private static final int WALK_THRESHOLD = 10;

    private final int targetLevel;

    public TrainAttackTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Attack to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.ATTACK) >= targetLevel;
    }

    @Override
    public void execute() {
        maybeIdle();

        // Walk to the chicken farm if too far away
        if (Rs2Player.getWorldLocation().distanceTo(CHICKEN_FARM) > WALK_THRESHOLD) {
            Microbot.log("Walking to Lumbridge chickens...");
            Rs2Walker.walkTo(CHICKEN_FARM, 3);
            sleep(800);
            return;
        }

        // Skip if already in combat
        if (Rs2Combat.inCombat()) {
            return;
        }

        // Attack a chicken
        boolean attacked = Rs2Npc.attack(CHICKEN_NPC);
        if (attacked) {
            log.debug("Attacked chicken, training attack ({}/{})",
                    Microbot.getClient().getRealSkillLevel(Skill.ATTACK), targetLevel);
        } else {
            // No chicken in range — move slightly toward center of farm and retry
            Microbot.log("No chicken found, repositioning...");
            Rs2Walker.walkTo(CHICKEN_FARM, 2);
        }

        sleep(600);
    }
}
