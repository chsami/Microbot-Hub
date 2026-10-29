package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

/**
 * One spot of the generated spot enums ({@code SpotDataA}, {@code SpotDataB}, ...): the columns that {@link Spots}
 * describes. The enums are cut so that none passes the JVM's 64 KB limit on one static initializer, and
 * {@code SpotData.all()} lists their rows in table order.
 */
interface SpotRow {
    TreeType getType();

    String getTown();

    int getX();

    int getY();

    int getPlane();

    int getRadius();

    int getMinCombat();

    boolean isMembers();

    boolean isWilderness();

    int getTrees();
}
