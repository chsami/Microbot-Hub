package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum TransmuteCategory {
    ORES("Ores", Arrays.asList(
            "Tin ore", "Copper ore", "Iron ore", "Coal", "Mithril ore", "Adamantite ore", "Runite ore"
    )),
    FISH("Fish", Arrays.asList(
            "Raw shrimps", "Raw sardine", "Raw herring", "Raw mackerel", "Raw trout",
            "Raw cod", "Raw pike", "Raw salmon", "Raw tuna", "Raw lobster",
            "Raw bass", "Raw swordfish", "Raw karambwan", "Raw shark", "Raw anglerfish"
    )),
    GEMS("Gems", Arrays.asList(
            "Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond",
            "Uncut opal", "Uncut jade", "Uncut red topaz", "Uncut dragonstone"
    )),
    RUNES("Runes", Arrays.asList(
            "Air rune", "Water rune", "Earth rune", "Fire rune",
            "Chaos rune", "Nature rune", "Cosmic rune", "Law rune",
            "Death rune", "Astral rune", "Blood rune", "Soul rune", "Wrath rune"
    )),
    ASHES("Ashes", Arrays.asList(
            "Ashes", "Volcanic ash", "Fiendish ashes", "Vile ashes",
            "Malicious ashes", "Abyssal ashes", "Infernal ashes"
    )),
    COMPOST("Compost", Arrays.asList(
            "Compost", "Supercompost", "Ultracompost"
    )),
    LOGS("Logs", Arrays.asList(
            "Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs",
            "Mahogany logs", "Yew logs", "Magic logs", "Redwood logs"
    )),
    BONES("Bones", Arrays.asList(
            "Bones", "Bat bones", "Big bones", "Wyrmling bones", "Baby dragon bones",
            "Wyrm bones", "Dragon bones", "Drake bones", "Lava dragon bones",
            "Hydra bones", "Dagannoth bones", "Superior dragon bones"
    )),
    HIDES("Hides", Arrays.asList(
            "Cowhide", "Snakeskin", "Green dragonhide", "Blue dragonhide",
            "Red dragonhide", "Black dragonhide"
    ));

    private final String displayName;
    private final List<String> chain;

    public int indexOf(String itemName) {
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).equalsIgnoreCase(itemName)) return i;
        }
        return -1;
    }

    public String getItem(int index) {
        if (index < 0 || index >= chain.size()) return null;
        return chain.get(index);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
