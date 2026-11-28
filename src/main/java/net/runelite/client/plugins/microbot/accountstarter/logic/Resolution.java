package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

@Value
@AllArgsConstructor
public class Resolution {
    public enum Kind { OWNED, CLAIM_REWARD, FREE, OPEN_BANK, BUY, PARK }

    Kind kind;
    String reason;
    int coinsNeeded;
    ApTask fromReward;
    int buyQuantity;

    public Resolution(Kind kind, String reason, int coinsNeeded, ApTask fromReward) {
        this(kind, reason, coinsNeeded, fromReward, 0);
    }
}
