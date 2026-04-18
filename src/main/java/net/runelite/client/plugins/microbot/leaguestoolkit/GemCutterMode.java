package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GemCutterMode {
    BUY_AND_BANK("Buy & Bank (fast stockpile uncut gems via briefcase)"),
    BUY_CUT_SELL("Buy, Cut & Sell (buy uncut, cut, sell cut back to Toci)"),
    BUY_CUT_BANK("Buy, Cut & Bank (buy uncut, cut, bank via briefcase)");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}
