package net.runelite.client.plugins.microbot.accountbuilder.task.tasks.quests;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;
import net.runelite.client.plugins.microbot.accountbuilder.task.AbstractTask;

/**
 * Completes Waterfall Quest for a free boost to Attack and Strength.
 *
 * <p>Varbit 222 == 7 when the quest is complete.
 * TODO: implement full quest automation state machine.
 */
@Slf4j
public class WaterfallQuestTask extends AbstractTask {

    /** Quest varbit ID for Waterfall Quest completion. */
    private static final int WATERFALL_QUEST_VARBIT = 222;
    private static final int COMPLETE_VALUE = 7;

    public WaterfallQuestTask(AccountProfile profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "Waterfall Quest";
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarbitValue(WATERFALL_QUEST_VARBIT) >= COMPLETE_VALUE
        ).orElse(false);
    }

    @Override
    public void execute() {
        // TODO: implement quest state machine
        // States: TALK_TO_ALMERA -> GET_BOOK -> ROW_TO_ISLAND -> ...
        maybeIdle();
        Microbot.log("WaterfallQuestTask: not yet implemented");
        sleep(2000);
    }
}
