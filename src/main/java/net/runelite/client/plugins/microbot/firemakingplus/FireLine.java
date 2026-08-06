// Adapted from the leaguesfiremaking plugin (FireLine).
package net.runelite.client.plugins.microbot.firemakingplus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

/**
 * A horizontal run of open tiles to lay fires along.
 */
@Getter
@RequiredArgsConstructor
public class FireLine {
    private final WorldPoint westEnd;
    private final WorldPoint eastEnd;
    private final int length;

    public int getY() {
        return westEnd.getY();
    }
}
