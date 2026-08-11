package net.runelite.client.plugins.custom.zulrah.constants;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.custom.zulrah.ZulrahPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
@Getter
public enum ZulrahType {
    RANGE("Range", 2042, Skill.RANGED, Color.YELLOW),
    MELEE("Melee", 2043, Skill.ATTACK, Color.RED),
    MAGIC("Magic", 2044, Skill.MAGIC, Color.CYAN);

    private final String name;
    private final int npcId;
    private final Skill skill;
    private final Color color;

    ZulrahType(final String name, final int npcId, final Skill skill, final Color color) {
        this.name = name;
        this.npcId = npcId;
        this.skill = skill;
        this.color = color;
    }

    public static ZulrahType valueOf(final int npcId) {
        switch (npcId) {
            case 2042: {
                return ZulrahType.RANGE;
            }
            case 2043: {
                return ZulrahType.MELEE;
            }
            case 2044: {
                return ZulrahType.MAGIC;
            }
            default: {
                return null;
            }
        }
    }
}
