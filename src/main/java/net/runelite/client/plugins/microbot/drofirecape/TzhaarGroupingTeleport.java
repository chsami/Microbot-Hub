package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.Microbot;

import java.util.function.BooleanSupplier;

/** Reuses the verified grouping UI steps without starting Fight Caves or its combat driver. */
public final class TzhaarGroupingTeleport {
    private final FcActions ui=new FcActions();
    public boolean cooldownActive() {
        return groupingCooldown();
    }
    /** Varp 888 holds the minute of the last grouping teleport; the cooldown is 20 minutes. */
    public static boolean groupingCooldown() {
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(()->{
            int last=net.runelite.client.plugins.microbot.Microbot.getClient().getVarpValue(888);
            return last>0&&System.currentTimeMillis()/60_000L-(long)last<=20;
        }).orElse(true);
    }
    public String step(BooleanSupplier allowed) {
        if(!allowed.getAsBoolean())return "Local teleport paused";
        return ui.minigameStep();
    }
}
