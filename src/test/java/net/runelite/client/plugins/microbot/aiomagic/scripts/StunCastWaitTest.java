package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StunCastWaitTest {

    private static final int CAST_ANIMATION = 1165;

    private MockedStatic<Global> global;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Microbot> microbot;
    private Client client;
    private final List<BooleanSupplier> waits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        global = mockStatic(Global.class);
        player = mockStatic(Rs2Player.class);
        microbot = mockStatic(Microbot.class);
        client = mock(Client.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(invocation -> {
            BooleanSupplier condition = invocation.getArgument(0);
            waits.add(condition);
            return condition.getAsBoolean();
        });
    }

    @AfterEach
    void tearDown() {
        microbot.close();
        player.close();
        global.close();
    }

    @Test
    void skipsCooldownWhenTheCastNeverStarts() {
        player.when(Rs2Player::getAnimation).thenReturn(AnimationID.IDLE);

        assertFalse(StunCastWait.awaitCast());
        assertEquals(1, waits.size());
        verify(client, never()).getTickCount();
    }

    @Test
    void waitsForTheSpellAttackCooldownAfterTheCastStarts() {
        player.when(Rs2Player::getAnimation).thenReturn(CAST_ANIMATION);
        when(client.getTickCount()).thenReturn(100);

        assertTrue(StunCastWait.awaitCast());
        assertEquals(2, waits.size());
        BooleanSupplier cooldown = waits.get(1);

        when(client.getTickCount()).thenReturn(100 + StunCastWait.SPELL_ATTACK_TICKS - 1);
        assertFalse(cooldown.getAsBoolean());

        when(client.getTickCount()).thenReturn(100 + StunCastWait.SPELL_ATTACK_TICKS);
        assertTrue(cooldown.getAsBoolean());
    }
}
