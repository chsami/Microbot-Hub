package net.runelite.client.plugins.microbot.accountbuilder.task.tasks.skills;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;
import net.runelite.client.plugins.microbot.accountbuilder.task.AbstractTask;

/**
 * Trains Prayer to the target level by burying bones.
 * TODO: implement bank loop and bone-burying logic.
 */
@Slf4j
public class TrainPrayerTask extends AbstractTask {

    private final int targetLevel;

    public TrainPrayerTask(int targetLevel, AccountProfile profile) {
        super(profile);
        this.targetLevel = targetLevel;
    }

    @Override
    public String getName() {
        return "Train Prayer to " + targetLevel;
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= targetLevel;
    }

    @Override
    public void execute() {
        // TODO: walk to bank, withdraw bones, bury at a suitable location
        maybeIdle();
        Microbot.log("TrainPrayerTask: not yet implemented");
        sleep(2000);
    }
}
