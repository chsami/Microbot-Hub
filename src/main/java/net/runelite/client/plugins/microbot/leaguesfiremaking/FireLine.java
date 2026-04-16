package net.runelite.client.plugins.microbot.leaguesfiremaking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

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
