package net.runelite.client.plugins.microbot.butterflycatcher;

import net.runelite.api.gameval.NpcID;

public enum Butterfly {
    RUBY_HARVEST(NpcID.BUTTERFLY_RUBY, 15),
    SAPPHIRE_GLACIALIS(NpcID.BUTTERFLY_GLACIALIS, 25),
    SNOWY_KNIGHT(NpcID.BUTTERFLY_SNOWY, 35),
    BLACK_WARLOCK(NpcID.BUTTERFLY_WARLOCK, 45),
    SUNLIGHT_MOTH(NpcID.MOTH_SUNLIGHT, 65),
    MOONLIGHT_MOTH(NpcID.MOTH_MOONLIGHT,75);

    private final int id;
    private final int levelRequired;


    Butterfly(int id, int levelRequired) {
        this.id = id;
        this.levelRequired = levelRequired;
    }

    public int getId() {
        return id;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    @Override
    public String toString() {
        return id + " (Level required: " + levelRequired + ")";
    }

}
