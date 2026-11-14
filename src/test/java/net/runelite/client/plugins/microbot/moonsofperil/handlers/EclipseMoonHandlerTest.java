package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.microbot.moonsofperil.enums.Locations;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EclipseMoonHandlerTest {
    private static final int BOSS_ID = NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS;
    private static final int ACTIVE_POLLS = 5;

    private static Rs2NpcCache npcCacheWithClonePhasePolls(int activePolls) {
        Rs2NpcModel sigil = mock(Rs2NpcModel.class);
        Rs2NpcModel boss = mock(Rs2NpcModel.class);
        when(boss.getLocalLocation()).thenReturn(new LocalPoint(64, 64, -1));
        AtomicInteger lastId = new AtomicInteger();
        AtomicInteger sigilLookups = new AtomicInteger();
        Rs2NpcQueryable queryable = mock(Rs2NpcQueryable.class);
        when(queryable.withId(anyInt())).thenAnswer(invocation -> {
            lastId.set(invocation.getArgument(0));
            return queryable;
        });
        when(queryable.where(any())).thenReturn(queryable);
        when(queryable.toList()).thenReturn(Collections.emptyList());
        when(queryable.nearest()).thenAnswer(invocation -> {
            if (lastId.get() == BOSS_ID) {
                return boss;
            }
            return sigilLookups.incrementAndGet() <= activePolls ? null : sigil;
        });
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(queryable);
        return cache;
    }

    @Test
    void clonePhaseEquipsOnEntryAndNotOnEveryPoll() {
        MoonsOfPerilConfig config = mock(MoonsOfPerilConfig.class);
        Rs2InventorySetup normal = mock(Rs2InventorySetup.class);
        Rs2InventorySetup clones = mock(Rs2InventorySetup.class);
        when(clones.doesEquipmentMatch()).thenReturn(false);
        Rs2NpcCache cache = npcCacheWithClonePhasePolls(ACTIVE_POLLS);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class);
             MockedStatic<Rs2LocalPoint> localPoint = mockStatic(Rs2LocalPoint.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            prayer.when(Rs2Prayer::getBestMeleePrayer).thenReturn(Rs2PrayerEnum.PIETY);
            player.when(Rs2Player::getWorldLocation).thenReturn(Locations.ECLIPSE_ARENA_CENTER.getWorldPoint());
            player.when(Rs2Player::getAnimation).thenReturn(-1);

            EclipseMoonHandler handler = new EclipseMoonHandler(config, normal, clones);

            assertTrue(handler.isSpecialAttack2Sequence());
            handler.specialAttack2Sequence();

            verify(clones, times(1)).wearEquipment();
            prayer.verify(() -> Rs2Prayer.toggle(any(Rs2PrayerEnum.class), anyBoolean()), times(1));
            global.verify(() -> Global.sleepUntilNextTick(anyLong()), times(2));
            global.verify(() -> Global.sleep(anyInt()), never());
            walker.verify(() -> Rs2Walker.walkCanvas(any()), never());
            verifyNoInteractions(normal);
        }
    }
}
