package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;

/** Controls only the walker's preflight check, never the actual game setting. */
public final class TravelCombatPolicy {
    private static volatile EfficientWalkerConfig config;
    private TravelCombatPolicy() { }
    static void configure(EfficientWalkerConfig value) { config = value; }
    public static int preflightVarp(Client client, int id) {
        EfficientWalkerConfig current = config;
        if (id == 172 && current != null && !current.disableAutoRetaliateDuringTravel()) return 1;
        return client.getVarpValue(id);
    }
}
