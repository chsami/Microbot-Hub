package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SlayerArrivalWithoutAutoTravelTest {

    private static final WorldPoint CANNON_SPOT = new WorldPoint(1419, 10072, 0);
    private static final WorldPoint CATACOMBS = new WorldPoint(1719, 10089, 0);

    private final List<Rs2NpcModel> scene = new ArrayList<>();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Slayer> slayer;
    private Player localPlayer;
    private SlayerScript script;

    @BeforeEach
    void setUp() throws Exception {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        slayer = mockStatic(Rs2Slayer.class);
        localPlayer = mock(Player.class);
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        microbot.when(Microbot::getClient).thenReturn(client);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(invocation.<Callable<?>>getArgument(0).call()));
        when(clientThread.invoke(ArgumentMatchers.<Supplier<Object>>any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.getStream()).thenAnswer(invocation -> scene.stream());
        when(cache.query()).thenCallRealMethod();
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
        slayer.when(Rs2Slayer::getSlayerMonsters).thenReturn(List.of("Black demon"));

        SlayerConfig config = mock(SlayerConfig.class);
        when(config.attackRadius()).thenReturn(10);
        script = new SlayerScript();
        setField("config", config);
        setField("taskDestination", CANNON_SPOT);
        setField("cannonSpot", CANNON_SPOT);
        setField("isUsingCannon", true);
        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
    }

    @AfterEach
    void tearDown() {
        SlayerPlugin.setState(SlayerState.IDLE);
        SlayerPlugin.setCurrentLocation("");
        slayer.close();
        player.close();
        microbot.close();
    }

    @Test
    void playerAtTaskDestinationMovesToAtLocation() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CANNON_SPOT.dx(5));

        handleArrival();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CANNON_SPOT, getField("taskDestination"));
        assertEquals(CANNON_SPOT, getField("cannonSpot"));
    }

    @Test
    void taskMonsterNearbyAwayFromDestinationFightsAtCurrentLocation() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Black demon", CATACOMBS.dy(-3), true);

        handleArrival();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CATACOMBS, getField("taskDestination"));
        assertNull(getField("cannonSpot"));
        assertFalse((Boolean) getField("isUsingCannon"));
    }

    @Test
    void otherMonstersNearbyAwayFromDestinationKeepsWaiting() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Goblin", CATACOMBS.dy(-3), true);
        npc("Black demon", CATACOMBS.dy(-30), false);

        handleArrival();

        assertEquals(SlayerState.DETECTING_TASK, SlayerPlugin.getState());
        assertEquals(CANNON_SPOT, getField("taskDestination"));
        assertEquals(CANNON_SPOT, getField("cannonSpot"));
    }

    private void npc(String name, WorldPoint location, boolean attacking) {
        NPC raw = mock(NPC.class);
        when(raw.getName()).thenReturn(name);
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getNpc()).thenReturn(raw);
        when(npc.getName()).thenReturn(name);
        when(npc.isDead()).thenReturn(false);
        when(npc.getHealthRatio()).thenReturn(-1);
        when(npc.getInteracting()).thenReturn(attacking ? localPlayer : null);
        when(npc.getWorldLocation()).thenReturn(location);
        scene.add(npc);
    }

    private void handleArrival() throws Exception {
        Method method = SlayerScript.class.getDeclaredMethod("handleArrivalWithoutAutoTravel");
        method.setAccessible(true);
        method.invoke(script);
    }

    private Object getField(String name) throws Exception {
        Field field = SlayerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = SlayerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }
}
