package net.runelite.client.plugins.microbot.accountbuilder.task.tasks.skills;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;
import net.runelite.client.plugins.microbot.accountbuilder.task.AbstractTask;

/**
 * Trains Magic to the target level by casting combat spells.
 * TODO: implement spell selection and combat targeting logic.
 */
@Slf4j
public class TrainMagicTask extends AbstractTask {

    private final int targetLevel;

    public TrainMagicTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Magic to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= targetLevel;
    }

    @Override
    public void execute() {
        // TODO: select appropriate spell, walk to target area, cast on NPCs
        maybeIdle();
        Microbot.log("TrainMagicTask: not yet implemented");
        sleep(2000);
    }
}
