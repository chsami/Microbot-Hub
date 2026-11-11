package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/** Shared action retry spacing and warning-free world eligibility; task triggers remain separate. */
final class DroSlayerHopPolicy {
    static final long RETRY_GAP_MS = 5000L;

    static boolean eligible(World world, boolean members, int currentWorld) {
        return world != null && world.getId() != currentWorld
                && world.getPlayers() >= 0 && world.getPlayers() < 1900
                && world.getTypes().contains(WorldType.MEMBERS) == members
                && world.getTypes().stream().allMatch(type -> type == WorldType.MEMBERS);
    }
}
