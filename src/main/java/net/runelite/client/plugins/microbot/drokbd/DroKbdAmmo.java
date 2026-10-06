package net.runelite.client.plugins.microbot.drokbd;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum DroKbdAmmo
{
    RUBY_BOLTS_E("Ruby bolts (e)", ItemID.RUBY_BOLTS_E, true, false),
    RUBY_DRAGON_BOLTS_E("Ruby dragon bolts (e)", ItemID.RUBY_DRAGON_BOLTS_E, true, false),
    DIAMOND_BOLTS_E("Diamond bolts (e)", ItemID.DIAMOND_BOLTS_E, true, false),
    DIAMOND_DRAGON_BOLTS_E("Diamond dragon bolts (e)", ItemID.DIAMOND_DRAGON_BOLTS_E, true, false),
    TOXIC_BLOWPIPE("Toxic blowpipe", ItemID.TOXIC_BLOWPIPE, false, true),
    MELEE("Melee", -1, false, false);

    private final String displayName;
    private final int itemId;
    private final boolean ammunitionBootstrap;
    private final boolean requiresExtendedSuperAntifire;

    @Override
    public String toString()
    {
        return displayName;
    }
}
