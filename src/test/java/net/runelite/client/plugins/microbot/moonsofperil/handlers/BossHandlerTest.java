package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class BossHandlerTest {
    private static final int SIGIL_ID = 1;
    private static final int BOSS_ID = 2;
    private static final WorldPoint SIGIL_TILE = new WorldPoint(1500, 9600, 0);

    private static MoonsOfPerilConfig config() {
        MoonsOfPerilConfig config = mock(MoonsOfPerilConfig.class);
        when(config.healthPercentage()).thenReturn(60);
        when(config.prayerPercentage()).thenReturn(50);
        when(config.debugLogging()).thenReturn(false);
        return config;
    }

    private static Rs2NpcCache npcCacheWithOneSigilIteration() {
        Rs2NpcModel sigil = mock(Rs2NpcModel.class);
        when(sigil.getWorldLocation()).thenReturn(SIGIL_TILE);
        Rs2NpcModel boss = mock(Rs2NpcModel.class);
        AtomicInteger lastId = new AtomicInteger();
        AtomicInteger sigilLookups = new AtomicInteger();
        Rs2NpcQueryable queryable = mock(Rs2NpcQueryable.class);
        when(queryable.withId(anyInt())).thenAnswer(invocation -> {
            lastId.set(invocation.getArgument(0));
            return queryable;
        });
        when(queryable.nearest()).thenAnswer(invocation -> {
            if (lastId.get() == BOSS_ID) {
                return boss;
            }
            return sigilLookups.incrementAndGet() <= 2 ? sigil : null;
        });
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(queryable);
        return cache;
    }

    @Test
    void normalAttackSequenceTopsUpHealthAndPrayerWhileFighting() {
        Rs2InventorySetup setup = mock(Rs2InventorySetup.class);
        when(setup.doesEquipmentMatch()).thenReturn(true);
        Rs2NpcCache cache = npcCacheWithOneSigilIteration();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class);
             MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class)) {
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            prayer.when(Rs2Prayer::getBestMeleePrayer).thenReturn(Rs2PrayerEnum.PIETY);
            combat.when(Rs2Combat::inCombat).thenReturn(true);
            player.when(() -> Rs2Player.distanceTo(any(WorldPoint.class))).thenReturn(0);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.PRAYER)).thenReturn(70);
            player.when(Rs2Player::hasMoonlightActive).thenReturn(true);

            new BossHandler(config()).normalAttackSequence(SIGIL_ID, BOSS_ID, new WorldPoint[]{SIGIL_TILE}, setup);

            player.verify(() -> Rs2Player.eatAt(60));
            player.verify(() -> Rs2Player.drinkPrayerPotionAt(35));
            player.verify(Rs2Player::drinkPrayerPotion, never());
        }
    }
}
