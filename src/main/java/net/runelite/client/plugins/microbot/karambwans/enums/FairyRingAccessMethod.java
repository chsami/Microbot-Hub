package net.runelite.client.plugins.microbot.karambwans.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FairyRingAccessMethod {
    ZANARIS_WALK("Zanaris (Walk)"),
    POH("POH (Teleport)");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}
