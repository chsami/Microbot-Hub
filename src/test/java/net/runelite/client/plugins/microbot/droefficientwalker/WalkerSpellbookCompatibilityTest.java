package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroLoginReadiness;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Main's opt-in service must survive the source-engine import with observed readiness. */
class WalkerSpellbookCompatibilityTest
{
    @Test void aBlockedTravelLegStillAdvancesTheRequestedSpellbookService() throws Exception
    {
        EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        Client client = mock(Client.class);
        EfficientWalker walker = mock(EfficientWalker.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(walker.getStatus()).thenReturn(EfficientWalker.Status.BLOCKED);
        bind(plugin, "client", client);
        bind(plugin, "walker", walker);
        bind(plugin, "statusOverlay", mock(EfficientWalkerStatusOverlay.class));
        bind(plugin, "tirannwnObstacleSupport", mock(TirannwnObstacleSupport.class));
        try (var readiness = mockStatic(DroLoginReadiness.class); var house = mockStatic(MaxPohSupport.class))
        {
            plugin.onGameTick(new GameTick());
            verify(walker).tickSpellbookPreparation();
            verify(walker, never()).k();
        }
    }

    @Test void switchingRemainsDisabledByDefaultAndDoesNotTakeNavigationOwnership()
    {
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class, CALLS_REAL_METHODS);
        WalkerSpellbookSupport.Travel travel = mock(WalkerSpellbookSupport.Travel.class);
        WalkerSpellbookSupport service = new WalkerSpellbookSupport(mock(Client.class), config, travel);

        assertFalse(config.autoSpellbook());
        assertFalse(config.hostedHouseSpellbook());
        assertEquals(EfficientWalker.SpellbookStatus.DISABLED, service.request(Rs2Spellbook.ANCIENT));
        service.tick();
        assertFalse(service.active());
        assertEquals(EfficientWalker.SpellbookStatus.IDLE, service.state());
        verifyNoInteractions(travel);
    }

    @Test void confirmedHouseSpellbookWaitsForAnObservedExit()
    {
        AtomicReference<Rs2Spellbook> current = new AtomicReference<>(Rs2Spellbook.MODERN);
        SpellbookPreparation.Port port = housePort(current);
        SpellbookPreparation flow = new SpellbookPreparation(port, Rs2Spellbook.ANCIENT, 1_000);

        assertFalse(flow.tick(1_000));
        current.set(Rs2Spellbook.ANCIENT);
        assertFalse(flow.tick(2_800), "A confirmed book alone must not release house ownership");
        verify(port).leaveHostedHouse();
        when(port.leaveHostedHouse()).thenReturn(true);
        assertTrue(flow.tick(4_600));
        verify(port, never()).walk(any());
        verify(port, never()).altar(any());
    }

    @Test void magicCapeIsTriedBeforeThePersonalHouse()
    {
        SpellbookPreparation.Port port = housePort(new AtomicReference<>(Rs2Spellbook.MODERN));
        when(port.availableCape()).thenReturn(9765);
        SpellbookPreparation flow = new SpellbookPreparation(port, Rs2Spellbook.ANCIENT, 1_000);
        assertFalse(flow.tick(1_000));
        verify(port).cape(9765, Rs2Spellbook.ANCIENT);
        verify(port, never()).ownHouse(any());
    }

    @Test void failedMagicCapeFallsBackToPersonalHouse()
    {
        SpellbookPreparation.Port port = housePort(new AtomicReference<>(Rs2Spellbook.MODERN));
        when(port.availableCape()).thenReturn(9765);
        SpellbookPreparation flow = new SpellbookPreparation(port, Rs2Spellbook.ANCIENT, 1_000);
        assertFalse(flow.tick(1_000));
        assertFalse(flow.tick(14_000));
        verify(port).ownHouse(Rs2Spellbook.ANCIENT);
    }

    @Test void exhaustedHostedHousesFallBackToThePublicAltar()
    {
        SpellbookPreparation.Port port = housePort(new AtomicReference<>(Rs2Spellbook.MODERN));
        when(port.ownHouseEnabled()).thenReturn(false);
        when(port.preferHostedHouse()).thenReturn(true);
        when(port.hostedHouse(Rs2Spellbook.ANCIENT)).thenReturn(SpellbookPreparation.HouseStep.UNAVAILABLE);
        when(port.leaveHostedHouse()).thenReturn(true);
        when(port.arrived(any())).thenReturn(true);
        when(port.switchLocation(Rs2Spellbook.ANCIENT)).thenReturn(new net.runelite.api.coords.WorldPoint(3233, 9315, 0));
        SpellbookPreparation flow = new SpellbookPreparation(port, Rs2Spellbook.ANCIENT, 1_000);
        long now = 1_000;
        for (int i = 0; i < 8; i++, now += 2_000) assertFalse(flow.tick(now));
        verify(port, times(2)).hostedHouse(Rs2Spellbook.ANCIENT);
        verify(port, never()).returnToExchange();
        verify(port, atLeastOnce()).altar(Rs2Spellbook.ANCIENT);
    }

    @Test void aBookChangeDuringHouseExitCannotReportReady()
    {
        AtomicReference<Rs2Spellbook> current = new AtomicReference<>(Rs2Spellbook.MODERN);
        SpellbookPreparation.Port port = housePort(current);
        SpellbookPreparation flow = new SpellbookPreparation(port, Rs2Spellbook.ANCIENT, 1_000);
        assertFalse(flow.tick(1_000));
        current.set(Rs2Spellbook.ANCIENT);
        when(port.leaveHostedHouse()).thenAnswer(call -> {
            current.set(Rs2Spellbook.MODERN);
            return true;
        });
        assertThrows(IllegalStateException.class, () -> flow.tick(2_800));
    }

    private static SpellbookPreparation.Port housePort(AtomicReference<Rs2Spellbook> current)
    {
        SpellbookPreparation.Port port = mock(SpellbookPreparation.Port.class);
        when(port.current()).thenAnswer(call -> current.get());
        when(port.unlocked(Rs2Spellbook.ANCIENT)).thenReturn(true);
        when(port.bankReady()).thenReturn(true);
        when(port.closeBank()).thenReturn(true);
        when(port.ownHouseEnabled()).thenReturn(true);
        when(port.canUseCape()).thenReturn(true);
        when(port.withdrawCape(anyInt())).thenReturn(true);
        when(port.ownHouse(Rs2Spellbook.ANCIENT)).thenReturn(SpellbookPreparation.HouseStep.IN_PROGRESS);
        return port;
    }

    private static void bind(Object owner, String name, Object value) throws Exception
    {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
