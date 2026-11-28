package net.runelite.client.plugins.microbot.droslayer.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerConfig;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerPrayer;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerRuntime;
import net.runelite.client.plugins.microbot.droslayer.PrayerFlickStyle;
import net.runelite.client.plugins.microbot.droslayer.support.DroLoginReadiness;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DroSlayerFlickerScriptTest {
    private final Set<Rs2PrayerEnum> active = EnumSet.noneOf(Rs2PrayerEnum.class);
    private final List<String> clicks = new ArrayList<>();
    private final AtomicBoolean hasPrayerPoints = new AtomicBoolean(true);
    private final AtomicInteger animation = new AtomicInteger(-1);
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private DroSlayerFlickerScript flicker;

    @BeforeEach
    void setUp() {
        Rs2NpcModel hellhound = mock(Rs2NpcModel.class);
        when(hellhound.getIndex()).thenReturn(7);
        when(hellhound.getId()).thenReturn(104);
        when(hellhound.getName()).thenReturn("Hellhound");
        when(hellhound.getGraphic()).thenReturn(-1);
        when(hellhound.getAnimation()).thenAnswer(call -> animation.get());
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.where(any())).thenReturn(query);
        when(query.toListOnClientThread()).thenReturn(List.of(hellhound));
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);

        MockedStatic<Microbot> microbot = track(mockStatic(Microbot.class));
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
        track(mockStatic(DroLoginReadiness.class));
        MockedStatic<DroSlayerRuntime> runtime = track(mockStatic(DroSlayerRuntime.class));
        runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(Runnable.class)))
                .thenAnswer(call -> {
                    ((Runnable) call.getArgument(2)).run();
                    return null;
                });
        MockedStatic<Rs2NpcManager> npcManager = track(mockStatic(Rs2NpcManager.class));
        npcManager.when(() -> Rs2NpcManager.getAttackStyle(anyInt())).thenReturn("Crush");
        npcManager.when(() -> Rs2NpcManager.getAttackSpeed(anyInt())).thenReturn(4);
        MockedStatic<Rs2Player> player = track(mockStatic(Rs2Player.class));
        player.when(Rs2Player::hasPrayerPoints).thenAnswer(call -> hasPrayerPoints.get());
        player.when(Rs2Player::isInteracting).thenReturn(true);
        MockedStatic<Rs2Prayer> prayer = track(mockStatic(Rs2Prayer.class));
        prayer.when(() -> Rs2Prayer.isPrayerActive(any())).thenAnswer(call -> active.contains(call.<Rs2PrayerEnum>getArgument(0)));
        prayer.when(() -> Rs2Prayer.toggle(any(), anyBoolean())).thenAnswer(call -> {
            Rs2PrayerEnum target = call.getArgument(0);
            boolean on = call.getArgument(1);
            clicks.add(target + (on ? " on" : " off"));
            if (on) active.add(target); else active.remove(target);
            return true;
        });

        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.LAZY_FLICK);
        flicker = new DroSlayerFlickerScript();
        flicker.start(config);
        flicker.setActivePrayer(DroSlayerPrayer.PROTECT_MELEE);
    }

    @AfterEach
    void tearDown() {
        flicker.stop();
        statics.forEach(MockedStatic::close);
    }

    private <T extends MockedStatic<?>> T track(T mocked) {
        statics.add(mocked);
        return mocked;
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) flicker.onGameTick();
    }

    private void hit() {
        animation.set(4652);
        flicker.onPlayerHit();
        animation.set(-1);
    }

    @Test
    void protectionIsSwitchedOffOnTheTickAfterTheHitAndFlickedAgainNextAttack() {
        ticks(3);
        assertEquals(List.of("PROTECT_MELEE on"), clicks);

        hit();
        ticks(1);
        assertEquals(List.of("PROTECT_MELEE on", "PROTECT_MELEE off"), clicks);
        assertFalse(active.contains(Rs2PrayerEnum.PROTECT_MELEE));

        ticks(2);
        assertEquals(List.of("PROTECT_MELEE on", "PROTECT_MELEE off", "PROTECT_MELEE on"), clicks);
        assertTrue(active.contains(Rs2PrayerEnum.PROTECT_MELEE));
    }

    @Test
    void aHitWithoutAFlickedPrayerLeavesOtherPrayersAlone() {
        active.add(Rs2PrayerEnum.PROTECT_ITEM);
        ticks(1);
        hit();
        ticks(1);
        assertTrue(clicks.isEmpty());
        assertTrue(active.contains(Rs2PrayerEnum.PROTECT_ITEM));
    }

    @Test
    void noActivationIsSentWhilePrayerPointsAreEmpty() {
        hasPrayerPoints.set(false);
        ticks(12);
        assertTrue(clicks.isEmpty());
    }
}
