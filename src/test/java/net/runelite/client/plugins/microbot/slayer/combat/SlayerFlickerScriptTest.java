package net.runelite.client.plugins.microbot.slayer.combat;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.slayer.PrayerFlickStyle;
import net.runelite.client.plugins.microbot.slayer.SlayerConfig;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SlayerFlickerScriptTest {

    @AfterEach
    void clearTrackedMonsters() {
        SlayerFlickerScript.currentMonstersAttackingUsRef.set(new ArrayList<>());
    }

    @Test
    void twoSameStyleMonstersDueOnTheSameTickToggleThePrayerOnce() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class);
             MockedStatic<Rs2NpcManager> npcManager = mockStatic(Rs2NpcManager.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            npcManager.when(() -> Rs2NpcManager.getAttackSpeed(anyInt())).thenReturn(4);
            Rs2NpcModel first = npc(1);
            Rs2NpcModel second = npc(2);
            stubNpcQuery(microbot, List.of(first, second));
            microbot.when(Microbot::getClient).thenReturn(mock(Client.class));

            SlayerMonster a = new SlayerMonster(first, null);
            SlayerMonster b = new SlayerMonster(second, null);
            a.lastAttack = 2;
            b.lastAttack = 2;
            SlayerFlickerScript.currentMonstersAttackingUsRef.set(new ArrayList<>(List.of(a, b)));

            SlayerConfig config = mock(SlayerConfig.class);
            when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.LAZY_FLICK);
            SlayerFlickerScript script = new SlayerFlickerScript();
            script.start(config);

            script.onGameTick();

            prayer.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true), times(1));
        }
    }

    private static Rs2NpcModel npc(int index) {
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getIndex()).thenReturn(index);
        when(npc.getId()).thenReturn(100 + index);
        when(npc.isDead()).thenReturn(false);
        when(npc.getAnimation()).thenReturn(-1);
        when(npc.getGraphic()).thenReturn(-1);
        return npc;
    }

    private static void stubNpcQuery(MockedStatic<Microbot> microbot, List<Rs2NpcModel> npcs) {
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.where(any())).thenReturn(query);
        when(query.toList()).thenReturn(npcs);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
    }
}
