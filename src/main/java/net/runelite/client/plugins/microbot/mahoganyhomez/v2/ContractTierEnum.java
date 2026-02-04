package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ContractTierEnum {

    BEGINNER("Beginner (1)", PlankEnum.NORMAL),
    NOVICE("Novice (20)", PlankEnum.OAK),
    ADEPT("Adept (50)", PlankEnum.TEAK),
    EXPERT("Expert (70)", PlankEnum.MAHOGANY);

    private final String name;
    private final PlankEnum plankSelection;
}
