package net.runelite.client.plugins.microbot.chillRunecraft;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EssenceType
{
    PURE_ESSENCE("Pure essence"),
    RUNE_ESSENCE("Rune essence");

    private final String itemName;
}

