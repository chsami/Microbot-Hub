package net.runelite.client.plugins.microbot.mining.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum Rocks {
    TIN("tin rocks", 1, "Tin ore"),
    COPPER("copper rocks", 1, "Copper ore"),
    CLAY("clay rocks", 1, "Clay"),
    IRON("iron rocks", 15, "Iron ore"),
    SILVER("silver rocks", 20, "Silver ore"),
    COAL("coal rocks", 30, "Coal"),
    GOLD("gold rocks", 40, "Gold ore"),
    GEM("gem rocks", 40, null),
    MITHRIL("mithril rocks", 55, "Mithril ore"),
    ADAMANTITE("adamantite rocks", 70, "Adamantite ore"),
    BASALT("Basalt rocks", 72, "Basalt"),
    URT_SALT("Urt salt rocks", 72, "Urt salt"),
    EFH_SALT("Efh salt rocks", 72, "Efh salt"),
    TE_SALT("Te salt rocks", 72, "Te salt"),
    RUNITE("runite rocks", 85, "Runite ore"),
    NONE("None", 1, null);

    private final String name;
    private final int miningLevel;
    private final String oreName;

    @Override
    public String toString() {
        return name;
    }
    
    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.MINING, this.miningLevel);
    }
}
