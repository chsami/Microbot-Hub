package net.runelite.client.plugins.microbot.hsblackjack.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BlackjackType {
    OAK("Oak blackjack"),
    OAK_OFFENSIVE("Oak blackjack(o)"),
    OAK_DEFENSIVE("Oak blackjack(d)"),
    WILLOW("Willow blackjack"),
    WILLOW_OFFENSIVE("Willow blackjack(o)"),
    WILLOW_DEFENSIVE("Willow blackjack(d)"),
    MAPLE("Maple blackjack"),
    MAPLE_OFFENSIVE("Maple blackjack(o)"),
    MAPLE_DEFENSIVE("Maple blackjack(d)");

    private final String itemName;

    @Override
    public String toString() {
        return itemName;
    }
}