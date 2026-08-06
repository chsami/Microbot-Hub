package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum GemType {
    SAPPHIRE("Uncut sapphire", "Sapphire", 20),
    EMERALD("Uncut emerald", "Emerald", 27),
    RUBY("Uncut ruby", "Ruby", 34);

    private final String uncutName;
    private final String cutName;
    private final int craftingLevel;

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.CRAFTING, craftingLevel);
    }

    @Override
    public String toString() {
        return cutName;
    }
}
