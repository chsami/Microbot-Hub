package net.runelite.client.plugins.microbot.accountbuilder.task.tasks.quests;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;
import net.runelite.client.plugins.microbot.accountbuilder.task.AbstractTask;

/**
 * Completes Animal Magnetism quest, which rewards an Ava's device for Ranged.
 *
 * <p>Varbit 274 == 4 when the quest is complete.
 * TODO: implement full quest automation state machine.
 */
@Slf4j
public class AnimalMagnetismTask extends AbstractTask {

    /**
     * Quest varbit ID for Animal Magnetism progress.
     * Source: OSRS Wiki — https://oldschool.runescape.wiki/w/Animal_Magnetism#Varbit
     * Value 4 = quest complete.
     * TODO: verify against live client before enabling this task in production.
     */
    private static final int ANIMAL_MAGNETISM_VARBIT = 274;
    private static final int COMPLETE_VALUE = 4;

    public AnimalMagnetismTask(AccountProfile profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "Animal Magnetism";
    }

    @Override
    public boolean isComplete() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarbitValue(ANIMAL_MAGNETISM_VARBIT) >= COMPLETE_VALUE
        ).orElse(false);
    }

    @Override
    public void execute() {
        // TODO: implement quest state machine
        // Requires: 18 Slayer, 19 Crafting, 30 Ranged, 35 Woodcutting
        // States: TALK_TO_ASYFF -> GET_UNDEAD_CHICKEN -> ...
        maybeIdle();
        Microbot.log("AnimalMagnetismTask: not yet implemented");
        sleep(2000);
    }
}
