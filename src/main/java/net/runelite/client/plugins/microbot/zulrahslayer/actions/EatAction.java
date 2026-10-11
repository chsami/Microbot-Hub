package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class EatAction implements ZulrahAction {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String key() {
        return "eat";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return Rs2Player.getHealthPercentage() <= 50;
    }

    @Override
    public Object execute(ZulrahState state) {
        boolean ate = Rs2Player.eatAt(50, true);
        if (ate) {
            log.debug("Eating.");
        }
        return ate;
    }
}
