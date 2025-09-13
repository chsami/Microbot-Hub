package net.runelite.client.plugins.microbot.thieving.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThievingFood {
    // Foods from Rs2Food
    DARK_CRAB("Dark Crab"),
    ROCKTAIL("Rocktail"),
    MANTA("Manta Ray"),
    SHARK("Shark"),
    KARAMBWAN("Cooked karambwan"),
    LOBSTER("Lobster"),
    TROUT("Trout"),
    SALMON("Salmon"),
    SWORDFISH("Swordfish"),
    TUNA("Tuna"),
    MONKFISH("Monkfish"),
    SEA_TURTLE("Sea Turtle"),
    CAKE("Cake"),
    BASS("Bass"),
    COD("Cod"),
    POTATO("Potato"),
    BAKED_POTATO("Baked Potato"),
    POTATO_WITH_CHEESE("Potato with Cheese"),
    EGG_POTATO("Egg Potato"),
    CHILLI_POTATO("Chilli Potato"),
    MUSHROOM_POTATO("Mushroom Potato"),
    TUNA_POTATO("Tuna Potato"),
    SHRIMPS("Shrimps"),
    HERRING("Herring"),
    SARDINE("Sardine"),
    CHOCOLATE_CAKE("Chocolate Cake"),
    ANCHOVIES("Anchovies"),
    PLAIN_PIZA("Plain Pizza"),
    MEAT_PIZZA("Meat Pizza"),
    ANCHOVY_PIZZA("Anchovy Pizza"),
    PINEAPPLE_PIZZA("Pineapple Pizza"),
    BREAD("Bread"),
    APPLE_PIE("Apple Pie"),
    REDBERRY_PIE("Redberry Pie"),
    MEAT_PIE("Meat Pie"),
    PIKE("Pike"),
    POTATO_WITH_BUTTER("Potato with Butter"),
    BANANA("Banana"),
    PEACH("Peach"),
    ORANGE("Orange"),
    PINEAPPLE_RINGS("Pineapple Rings"),
    PINEAPPLE_CHUNKS("Pineapple Chunks"),
    JUG_OF_WINE("Jug of wine"),
    PURPLE_SWEETS("Purple Sweets"),
    CABBAGE("Cabbage"),

    // Custom potion
    ANCIENT_BREW("Ancient brew");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}