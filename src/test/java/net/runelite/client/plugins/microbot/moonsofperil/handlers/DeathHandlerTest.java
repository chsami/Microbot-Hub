package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeathHandlerTest {
    private static MoonsOfPerilConfig config() {
        MoonsOfPerilConfig config = mock(MoonsOfPerilConfig.class);
        when(config.shutdownOnDeath()).thenReturn(false);
        when(config.debugLogging()).thenReturn(false);
        return config;
    }

    @Test
    void graveWalkWaitTreatsMissingPlayerLocationAsNotArrived() {
        Rs2NpcQueryable queryable = mock(Rs2NpcQueryable.class);
        when(queryable.withId(anyInt())).thenReturn(queryable);
        when(queryable.interact("Loot")).thenReturn(true);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(queryable);
        AtomicReference<WorldPoint> location = new AtomicReference<>();
        List<Boolean> results = new ArrayList<>();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<BreakHandlerScript> breakHandler = mockStatic(BreakHandlerScript.class)) {
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            bank.when(Rs2Bank::getNearestBank).thenReturn(BankLocation.LUMBRIDGE_TOP);
            bank.when(Rs2Bank::openBank).thenReturn(true);
            inventory.when(Rs2Inventory::all).thenReturn(Collections.emptyList());
            player.when(Rs2Player::getWorldLocation).thenAnswer(invocation -> location.get());
            global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(invocation -> {
                BooleanSupplier condition = invocation.getArgument(0);
                location.set(null);
                results.add(condition.getAsBoolean());
                location.set(new WorldPoint(1450, 9626, 1));
                results.add(condition.getAsBoolean());
                location.set(new WorldPoint(1442, 9626, 1));
                results.add(condition.getAsBoolean());
                return true;
            });

            new DeathHandler(config()).execute();

            assertEquals(List.of(false, false, true), results);
            verify(queryable).withId(NpcID.GRAVESTONE_DEFAULT);
            verify(queryable).interact("Loot");
        }
    }
}
