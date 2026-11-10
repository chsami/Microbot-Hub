package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DroSlayerHopActionTest {
    @Test @Timeout(value = 3, threadMode = Timeout.ThreadMode.SAME_THREAD) public void rejectedHopReturnsWithoutDuplicateWaitAndCannotSpam() throws Exception {
        exercise(false, false);
    }
    @Test @Timeout(value = 3, threadMode = Timeout.ThreadMode.SAME_THREAD) public void confirmedArrivalResumesTaskDetection() throws Exception {
        exercise(true, false);
    }
    @Test @Timeout(value = 3, threadMode = Timeout.ThreadMode.SAME_THREAD) public void combatPreventsDispatchBeforePrayerOrRouteChanges() throws Exception {
        exercise(false, true);
    }
    private DroSlayerScript script;
    private Method hop;
    private Client client;
    private MockedStatic<Microbot> dro;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<DroSlayerRuntime> runtime;
    private MockedStatic<DroSlayerPlugin> plugin;
    private final List<MockedStatic<?>> mocks = new ArrayList<>();

    // Cold JVM/Mockito instrumentation is fixture work, outside the behavioral deadline.
    // Static mocks and timed actions must share their owning thread.
    @BeforeEach
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SAME_THREAD)
    void setUp() throws Exception {
        script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
        when(config.selectedProtectionPrayer()).thenReturn(DroSlayerPrayer.NONE);
        when(config.hopWorldList()).thenReturn("441");
        Field field = DroSlayerScript.class.getDeclaredField("config");
        field.setAccessible(true); field.set(script, config);
        hop = DroSlayerScript.class.getDeclaredMethod("hopToNewWorld"); hop.setAccessible(true);
        client = mock(Client.class);
        when(client.getWorld()).thenReturn(337);
        when(client.getWorldType()).thenReturn(EnumSet.of(net.runelite.api.WorldType.MEMBERS));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
        WorldService service = mock(WorldService.class);
        WorldResult worlds = mock(WorldResult.class);
        World target = mock(World.class);
        when(target.getId()).thenReturn(441);
        when(target.getPlayers()).thenReturn(100);
        when(target.getTypes()).thenReturn(EnumSet.of(net.runelite.http.api.worlds.WorldType.MEMBERS));
        when(worlds.findWorld(441)).thenReturn(target);
        when(service.getWorlds()).thenReturn(worlds);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        dro = register(Microbot.class);
        register(Rs2Bank.class);
        player = register(Rs2Player.class);
        register(Rs2Prayer.class);
        runtime = register(DroSlayerRuntime.class);
        plugin = register(DroSlayerPlugin.class);
        dro.when(Microbot::getClient).thenReturn(client);
        dro.when(Microbot::getClientThread).thenReturn(thread);
        dro.when(Microbot::getWorldService).thenReturn(service);
        dro.when(Microbot::getRs2NpcCache).thenReturn(cache);
        runtime.when(() -> DroSlayerRuntime.performAction(eq(BaseProfileDro.ActionPhase.SETUP),
                eq(false), any(BooleanSupplier.class))).thenAnswer(invocation ->
                ((BooleanSupplier) invocation.getArgument(2)).getAsBoolean());
    }

    private <T> MockedStatic<T> register(Class<T> type) {
        MockedStatic<T> scoped = mockStatic(type);
        mocks.add(scoped);
        return scoped;
    }

    @AfterEach
    void tearDown() {
        for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).closeOnDemand();
    }

    private void exercise(boolean arrived, boolean combat) throws Exception {
        dro.when(() -> Microbot.hopToWorld(441)).thenAnswer(invocation -> {
            if (arrived) when(client.getWorld()).thenReturn(441);
            return arrived;
        });
        player.when(Rs2Player::isInCombat).thenReturn(combat);
        hop.invoke(script);
        hop.invoke(script);
        dro.verify(() -> Microbot.hopToWorld(441), times(combat ? 0 : 1));
        plugin.verify(() -> DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK), times(arrived ? 1 : 0));
        if (combat) runtime.verify(DroSlayerRuntime::cancelWalk, never());
    }
}
