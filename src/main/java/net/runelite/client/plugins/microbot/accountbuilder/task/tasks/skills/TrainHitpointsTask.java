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
 * Trains Hitpoints to the target level by fighting barbarians at Barbarian Village.
 * Hitpoints XP is gained passively alongside any combat style, so this task runs
 * as a normal melee session — the target level is usually reached before melee tasks
 * complete, so isComplete() short-circuits early when that happens.
 */
@Slf4j
public class TrainHitpointsTask extends AbstractTask {

    private static final WorldPoint BARBARIAN_VILLAGE = new WorldPoint(3082, 3422, 0);
    private static final String NPC = "Barbarian";
    private static final int WALK_THRESHOLD = 10;

    private final int targetLevel;

    public TrainHitpointsTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Hitpoints to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS) >= targetLevel;
    }

    @Override
    public void execute() {
        maybeIdle();

        if (Rs2Player.getWorldLocation().distanceTo(BARBARIAN_VILLAGE) > WALK_THRESHOLD) {
            Rs2Walker.walkTo(BARBARIAN_VILLAGE, 3);
            sleep(800);
            return;
        }

        if (Rs2Combat.inCombat()) {
            return;
        }

        boolean attacked = Rs2Npc.attack(NPC);
        if (!attacked) {
            Rs2Walker.walkTo(BARBARIAN_VILLAGE, 2);
        }

        sleep(600);
    }
}
