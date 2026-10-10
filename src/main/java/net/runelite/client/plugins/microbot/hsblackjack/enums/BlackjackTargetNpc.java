package net.runelite.client.plugins.microbot.hsblackjack.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BlackjackTargetNpc {
    VILLAGER("Villager"),
    BANDIT("Bandit"),
    MENAPHITE_THUG("Menaphite thug");

    private final String npcName;

    @Override
    public String toString() {
        return npcName;
    }
}