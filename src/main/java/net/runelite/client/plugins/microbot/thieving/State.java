package net.runelite.client.plugins.microbot.thieving;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum State {
    IDLE,
    BANK,
    ESCAPE,
    LOOT,
    EAT(false),
    DROP(false),
    SHADOW_VEIL(false),
    PICKPOCKET,
    HOP,
    STUNNED,
    CLOSE_DOOR,
    COIN_POUCHES(false),
    WALK_TO_START,
    DRINK(false),
    SLEEPING;

    @Getter
    private final boolean awaitStuns;

    State() {
        this(true);
    }
}
