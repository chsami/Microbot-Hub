package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

final class StunCastWait {

    static final int SPELL_ATTACK_TICKS = 5;
    private static final int CAST_START_TIMEOUT_MS = 3000;
    private static final int COOLDOWN_TIMEOUT_MS = 4200;

    private StunCastWait() {
    }

    static boolean awaitCast() {
        if (!Global.sleepUntil(() -> Rs2Player.getAnimation() != AnimationID.IDLE, CAST_START_TIMEOUT_MS)) {
            return false;
        }
        Client client = Microbot.getClient();
        int castTick = client.getTickCount();
        Global.sleepUntil(() -> client.getTickCount() - castTick >= SPELL_ATTACK_TICKS, COOLDOWN_TIMEOUT_MS);
        return true;
    }
}
