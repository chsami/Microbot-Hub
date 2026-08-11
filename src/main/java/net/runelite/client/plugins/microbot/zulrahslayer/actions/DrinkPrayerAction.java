package net.runelite.client.plugins.custom.zulrah.actions;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public class DrinkPrayerAction implements ZulrahAction {

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String key() {
        return "drink-prayer";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return Rs2Player.getPrayerPercentage() <= 20;
    }

    @Override
    public Object execute(ZulrahState state) {
        return Rs2Player.drinkPrayerPotionAt(20);
    }
}
