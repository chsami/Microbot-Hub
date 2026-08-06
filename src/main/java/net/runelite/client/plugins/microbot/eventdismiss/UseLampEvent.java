package net.runelite.client.plugins.microbot.eventdismiss;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

@Slf4j
public class UseLampEvent implements BlockingEvent {

    private final EventDismissConfig config;

    public UseLampEvent(EventDismissConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        return config.checkForLamps() && Rs2Inventory.contains(ItemID.LAMP);
    }

    @Override
    public boolean execute() {
        log.debug("Using stray lamp on {}", config.lampSkill());
        return LampUtility.useLamp(config.lampSkill());
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
