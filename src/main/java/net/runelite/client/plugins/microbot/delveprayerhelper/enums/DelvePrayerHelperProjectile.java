package net.runelite.client.plugins.microbot.delveprayerhelper.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DelvePrayerHelperProjectile {
    MELEE(3378),
    MAGE(3379),
    RANGE(3380);

    private final int projectileID;
}
