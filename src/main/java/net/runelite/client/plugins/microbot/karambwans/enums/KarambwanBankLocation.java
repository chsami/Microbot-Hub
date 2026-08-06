package net.runelite.client.plugins.microbot.karambwans.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KarambwanBankLocation {
    NONE("None (Walk to Zanaris)"),
    SEERS("Seers Village");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}
