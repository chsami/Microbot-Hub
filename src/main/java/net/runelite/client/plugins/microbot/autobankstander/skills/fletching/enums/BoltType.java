package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

public enum BoltType {
    // Regular bolts
    BRONZE("Bronze bolts", 9, ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_UNFEATHERED, ItemID.FEATHER, ItemID.BOLT),
    BLURITE("Blurite bolts", 24, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE),
    IRON("Iron bolts", 39, ItemID.XBOWS_CROSSBOW_BOLTS_IRON_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_IRON),
    SILVER("Silver bolts", 43, ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_SILVER),
    STEEL("Steel bolts", 46, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL),
    MITHRIL("Mithril bolts", 54, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL),
    BROAD("Broad bolts", 55, ItemID.SLAYER_BROAD_BOLT_UNFINISHED, ItemID.FEATHER, ItemID.SLAYER_BROAD_BOLT),
    ADAMANT("Adamant bolts", 61, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE),
    RUNE("Rune bolts", 69, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_UNFEATHERED, ItemID.FEATHER, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE),
    DRAGON("Dragon bolts", 84, ItemID.DRAGON_BOLTS_UNFEATHERED, ItemID.FEATHER, ItemID.DRAGON_BOLTS),
    
    // Tipped bolts
    OPAL_BRONZE("Opal bronze bolts", 11, ItemID.BOLT, ItemID.OPAL_BOLTTIPS, ItemID.OPAL_BOLT),
    JADE_BLURITE("Jade blurite bolts", 26, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE, ItemID.XBOWS_BOLT_TIPS_JADE, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE),
    PEARL_IRON("Pearl iron bolts", 41, ItemID.XBOWS_CROSSBOW_BOLTS_IRON, ItemID.PEARL_BOLTTIPS, ItemID.PEARL_BOLT),
    TOPAZ_STEEL("Red topaz steel bolts", 48, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL, ItemID.XBOWS_BOLT_TIPS_REDTOPAZ, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ),
    BARB_BRONZE("Barb bronze bolts", 51, ItemID.BOLT, ItemID.BARBED_BOLTTIPS, ItemID.BARBED_BOLT),
    SAPPHIRE_MITHRIL("Sapphire mithril bolts", 56, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL, ItemID.XBOWS_BOLT_TIPS_SAPPHIRE, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE),
    EMERALD_MITHRIL("Emerald mithril bolts", 58, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL, ItemID.XBOWS_BOLT_TIPS_EMERALD, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD),
    RUBY_ADAMANT("Ruby adamant bolts", 63, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE, ItemID.XBOWS_BOLT_TIPS_RUBY, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY),
    DIAMOND_ADAMANT("Diamond adamant bolts", 65, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE, ItemID.XBOWS_BOLT_TIPS_DIAMOND, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND),
    DRAGONSTONE_RUNE("Dragonstone rune bolts", 71, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE, ItemID.XBOWS_BOLT_TIPS_DRAGONSTONE, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE),
    ONYX_RUNE("Onyx rune bolts", 73, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE, ItemID.XBOWS_BOLT_TIPS_ONYX, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX),
    AMETHYST_BROAD("Amethyst broad bolts", 76, ItemID.SLAYER_BROAD_BOLT, ItemID.XBOWS_BOLT_TIPS_AMETHYST, ItemID.SLAYER_BROAD_BOLT_AMETHYST),
    
    // Dragon bolt tips
    OPAL_DRAGON("Opal dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.OPAL_BOLTTIPS, ItemID.DRAGON_BOLTS_UNENCHANTED_OPAL),
    JADE_DRAGON("Jade dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_JADE, ItemID.DRAGON_BOLTS_UNENCHANTED_JADE),
    PEARL_DRAGON("Pearl dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.PEARL_BOLTTIPS, ItemID.DRAGON_BOLTS_UNENCHANTED_PEARL),
    TOPAZ_DRAGON("Red topaz dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_REDTOPAZ, ItemID.DRAGON_BOLTS_UNENCHANTED_TOPAZ),
    SAPPHIRE_DRAGON("Sapphire dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_SAPPHIRE, ItemID.DRAGON_BOLTS_UNENCHANTED_SAPPHIRE),
    EMERALD_DRAGON("Emerald dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_EMERALD, ItemID.DRAGON_BOLTS_UNENCHANTED_EMERALD),
    RUBY_DRAGON("Ruby dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_RUBY, ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY),
    DIAMOND_DRAGON("Diamond dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_DIAMOND, ItemID.DRAGON_BOLTS_UNENCHANTED_DIAMOND),
    DRAGONSTONE_DRAGON("Dragonstone dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_DRAGONSTONE, ItemID.DRAGON_BOLTS_UNENCHANTED_DRAGONSTONE),
    ONYX_DRAGON("Onyx dragon bolts", 84, ItemID.DRAGON_BOLTS, ItemID.XBOWS_BOLT_TIPS_ONYX, ItemID.DRAGON_BOLTS_UNENCHANTED_ONYX);

    private final String name;
    private final int levelRequired;
    private final int materialOneId;
    private final int materialTwoId;
    private final int productId;

    BoltType(String name, int levelRequired, int materialOneId, int materialTwoId, int productId) {
        this.name = name;
        this.levelRequired = levelRequired;
        this.materialOneId = materialOneId;
        this.materialTwoId = materialTwoId;
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public int getMaterialOneId() {
        return materialOneId;
    }

    public int getMaterialTwoId() {
        return materialTwoId;
    }

    public int getProductId() {
        return productId;
    }


    @Override
    public String toString() {
        return name;
    }
}