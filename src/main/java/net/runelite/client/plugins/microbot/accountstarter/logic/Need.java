package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Need {
    String label;
    Set<Integer> anyOf;
    int quantity;
    FreeSource free;
    int buyItemId;
    String buyName;
    int minimum;
    int least;

    public Need(String label, Set<Integer> anyOf, int quantity, FreeSource free, int buyItemId, String buyName) {
        this(label, anyOf, quantity, free, buyItemId, buyName, quantity, quantity);
    }
}
