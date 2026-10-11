package net.runelite.client.plugins.microbot.zulrahslayer.rotationutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.ZulrahType;

import java.util.Objects;

@RequiredArgsConstructor
@Getter
public final class ZulrahNpc {
    private final ZulrahType type;
    private final boolean jad;

    public static ZulrahNpc valueOf(NPC zulrah, boolean jad) {
        ZulrahType type = ZulrahType.valueOf(zulrah.getId());
        return type == null ? null : new ZulrahNpc(type, jad);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ZulrahNpc zulrahNpc = (ZulrahNpc) o;
        return jad == zulrahNpc.jad && type == zulrahNpc.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jad);
    }
}
