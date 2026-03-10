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

/** Trains Ranged to the target level using chickens / cows with a bow. */
@Slf4j
public class TrainRangedTask extends AbstractTask {

    private static final WorldPoint CHICKEN_FARM = new WorldPoint(3236, 3295, 0);
    private static final String NPC = "Chicken";
    private static final int WALK_THRESHOLD = 10;

    private final int targetLevel;

    public TrainRangedTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Ranged to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= targetLevel;
    }

    @Override
    public void execute() {
        maybeIdle();

        if (Rs2Player.getWorldLocation().distanceTo(CHICKEN_FARM) > WALK_THRESHOLD) {
            Rs2Walker.walkTo(CHICKEN_FARM, 3);
            sleep(800);
            return;
        }

        if (Rs2Combat.inCombat()) {
            return;
        }

        boolean attacked = Rs2Npc.attack(NPC);
        if (!attacked) {
            Rs2Walker.walkTo(CHICKEN_FARM, 2);
        }

        sleep(600);
    }
}
