package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerProfileManager;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SlayerArrivalWithoutAutoTravelTest {

    private static final String TASK = "Black demons";
    private static final WorldPoint CHASM_OF_FIRE = SlayerLocation.CHASM_OF_FIRE.getWorldPoint();
    private static final WorldPoint CATACOMBS = new WorldPoint(1719, 10089, 0);

    private final List<Rs2NpcModel> scene = new ArrayList<>();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Slayer> slayer;
    private MockedStatic<Rs2Dialogue> dialogue;
    private SlayerConfig config;
    private SlayerTaskProfileJson profile;
    private SlayerScript script;

    @BeforeEach
    void setUp() throws Exception {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        slayer = mockStatic(Rs2Slayer.class);
        dialogue = mockStatic(Rs2Dialogue.class);
        Client client = mock(Client.class);
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
        dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
        slayer.when(Rs2Slayer::getSlayerTask).thenReturn(TASK);
        slayer.when(Rs2Slayer::getSlayerMonsters).thenReturn(List.of("Black demon"));

        config = mock(SlayerConfig.class);
        when(config.attackRadius()).thenReturn(10);
        when(config.enableAutoTravel()).thenReturn(false);
        when(config.enableAutoBanking()).thenReturn(false);
        when(config.enableCannon()).thenReturn(true);

        profile = new SlayerTaskProfileJson();
        profile.setCannon(true);
        profile.setCannonLocation("Chasm of Fire");
        SlayerProfileManager profileManager = mock(SlayerProfileManager.class);
        when(profileManager.findProfile(TASK)).thenReturn(profile);

        script = new SlayerScript();
        setField("config", config);
        setField("profileManager", profileManager);
        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
    }

    @AfterEach
    void tearDown() {
        SlayerPlugin.setState(SlayerState.IDLE);
        SlayerPlugin.setCurrentLocation("");
        dialogue.close();
        slayer.close();
        player.close();
        microbot.close();
    }

    @Test
    void playerAtCannonSpotStartsTheTaskWithCannon() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CHASM_OF_FIRE.dx(5));

        detectTask();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CHASM_OF_FIRE, getField("taskDestination"));
        assertEquals(CHASM_OF_FIRE, getField("cannonSpot"));
        assertTrue((Boolean) getField("isUsingCannon"));
        assertEquals("Chasm of Fire (Cannon)", SlayerPlugin.getCurrentLocation());
    }

    @Test
    void taskMonsterNearbyAwayFromCannonSpotFightsHereWithoutCannon() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Black demon", CATACOMBS.dy(-3));

        detectTask();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CATACOMBS, getField("taskDestination"));
        assertNull(getField("cannonSpot"));
        assertFalse((Boolean) getField("isUsingCannon"));
        assertEquals("Current location (no cannon)", SlayerPlugin.getCurrentLocation());
    }

    @Test
    void returningToDetectingTaskKeepsTheCannonOffForTheAdoptedLocation() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Black demon", CATACOMBS.dy(-3));
        detectTask();

        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
        scene.clear();
        detectTask();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CATACOMBS, getField("taskDestination"));
        assertNull(getField("cannonSpot"));
        assertFalse((Boolean) getField("isUsingCannon"));
        assertEquals("Current location", SlayerPlugin.getCurrentLocation());
    }

    @Test
    void taskMonsterNearbyWithoutCannonFightsHere() throws Exception {
        when(config.enableCannon()).thenReturn(false);
        profile.setLocation("Chasm of Fire");
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Black demon", CATACOMBS.dy(-3));

        detectTask();

        assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
        assertEquals(CATACOMBS, getField("taskDestination"));
        assertEquals("Current location", SlayerPlugin.getCurrentLocation());
    }

    @Test
    void noTaskMonsterInRangeKeepsWaitingAndShowsWhereToWalk() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(CATACOMBS);
        npc("Goblin", CATACOMBS.dy(-3));
        npc("Black demon", CATACOMBS.dy(-11));

        detectTask();
        detectTask();

        assertEquals(SlayerState.DETECTING_TASK, SlayerPlugin.getState());
        assertEquals(CHASM_OF_FIRE, getField("taskDestination"));
        assertEquals(CHASM_OF_FIRE, getField("cannonSpot"));
        assertEquals("Walk to Chasm of Fire", SlayerPlugin.getCurrentLocation());
        assertTrue((Boolean) getField("arrivalHintShown"));
    }

    private void npc(String name, WorldPoint location) {
        NPC raw = mock(NPC.class);
        when(raw.getName()).thenReturn(name);
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getNpc()).thenReturn(raw);
        when(npc.getName()).thenReturn(name);
        when(npc.isDead()).thenReturn(false);
        when(npc.getHealthRatio()).thenReturn(-1);
        when(npc.getWorldLocation()).thenReturn(location);
        scene.add(npc);
    }

    private void detectTask() throws Exception {
        Method method = SlayerScript.class.getDeclaredMethod("handleDetectingTaskState", boolean.class);
        method.setAccessible(true);
        method.invoke(script, true);
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
