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

/** Trains Strength to the target level by fighting cows at Lumbridge. */
@Slf4j
public class TrainStrengthTask extends AbstractTask {

    private static final WorldPoint COW_FIELD = new WorldPoint(3255, 3272, 0);
    private static final String COW_NPC = "Cow";
    private static final int WALK_THRESHOLD = 10;

    private final int targetLevel;

    public TrainStrengthTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Strength to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.STRENGTH) >= targetLevel;
    }

    @Override
    public void execute() {
        maybeIdle();

        if (Rs2Player.getWorldLocation().distanceTo(COW_FIELD) > WALK_THRESHOLD) {
            Rs2Walker.walkTo(COW_FIELD, 3);
            sleep(800);
            return;
        }

        if (Rs2Combat.inCombat()) {
            return;
        }

        boolean attacked = Rs2Npc.attack(COW_NPC);
        if (!attacked) {
            Rs2Walker.walkTo(COW_FIELD, 2);
        }

        sleep(600);
    }
}
