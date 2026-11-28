package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.client.plugins.microbot.accountstarter.run.CombatActivity;
import net.runelite.api.coords.WorldPoint;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.LootRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import org.junit.jupiter.api.Test;

class LootRulesTest {

    private static Choices choices(boolean bury) {
        return Choices.from(Preset.EVERYTHING, Collections.emptyMap(), bury, false, false);
    }

    @Test
    void ownArrowsAlwaysTaken() {
        assertTrue(LootRules.take(ItemID.BRONZE_ARROW, 1, choices(false), true));
    }

    @Test
    void bonesWhenBurying() {
        assertTrue(LootRules.take(ItemID.BONES, 1, choices(true), false));
    }

    @Test
    void bonesLeftWhenPrayerProtected() {
        Choices c = new Choices(Set.of(), EnumSet.of(Skill.PRAYER), true, false, false);
        assertFalse(LootRules.take(ItemID.BONES, 1, c, false));
    }

    @Test
    void coinsAlwaysTaken() {
        assertTrue(LootRules.take(ItemID.COINS, 3, choices(false), false));
    }

    @Test
    void itemsInOnePileEachGetTheirOwnLootKey() {
        WorldPoint tile = new WorldPoint(3254, 3283, 0);
        assertNotEquals(CombatActivity.lootKey(526, tile), CombatActivity.lootKey(1739, tile));
    }
}
