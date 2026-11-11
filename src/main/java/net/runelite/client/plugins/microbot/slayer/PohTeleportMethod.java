package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PohTeleportMethod {
    HOUSE_TAB("House Tab", "Teleport to house", "Break"),
    SPELL("Teleport Spell", null, null),
    CONSTRUCTION_CAPE("Construction Cape", "Construct. cape", "Tele to POH"),
    MAX_CAPE("Max Cape", "Max cape", "Tele to POH");

    private final String displayName;
    private final String itemName;
    private final String action;

    @Override
    public String toString() {
        return displayName;
    }
}
