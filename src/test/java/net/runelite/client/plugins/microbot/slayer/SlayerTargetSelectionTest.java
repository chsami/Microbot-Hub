package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlayerTargetSelectionTest {

    private static final WorldPoint ORIGIN = new WorldPoint(3200, 3200, 0);
    private final List<Rs2NpcModel> scene = new ArrayList<>();
    private final List<Rs2PlayerModel> otherPlayers = new ArrayList<>();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Slayer> slayer;
    private Player localPlayer;
    private SlayerConfig config;
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

        // Exercise the real query predicates over a fresh scene stream for every lookup.
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.getStream()).thenAnswer(invocation -> scene.stream());
        when(cache.query()).thenCallRealMethod();
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
        Rs2PlayerCache playerCache = mock(Rs2PlayerCache.class);
        when(playerCache.getStream()).thenAnswer(invocation -> otherPlayers.stream());
        microbot.when(Microbot::getRs2PlayerCache).thenReturn(playerCache);
        player.when(Rs2Player::getWorldLocation).thenReturn(ORIGIN);
        slayer.when(Rs2Slayer::getSlayerMonsters).thenReturn(List.of("Abyssal demon"));

        config = mock(SlayerConfig.class);
        when(config.attackRadius()).thenReturn(20);
        when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.OFF);
        script = new SlayerScript();
        setField("config", config);
        setField("taskDestination", ORIGIN);
    }

    @AfterEach
    void tearDown() {
        slayer.close();
        player.close();
        microbot.close();
    }

    @ParameterizedTest
    @CsvSource({
            "true, 0, false", "true, -1, false", "false, 0, false",
            "true, 0, true", "true, -1, true", "false, 0, true"
    })
    void switchesToLivingTargetInSamePassWhileDefeatedNpcStillInteracts(
            boolean dead, int healthRatio, boolean nextIsAttacking) throws Exception {
        Rs2NpcModel defeated = npc("Abyssal demon", dead, healthRatio, true);
        Rs2NpcModel next = npc("Abyssal demon", false, -1, nextIsAttacking);
        setCurrentTarget(defeated);

        handleCombat();

        verify(defeated, never()).click("Attack");
        verify(next).click("Attack");
    }

    @Test
    void proactiveSelectionSkipsZeroHpNpcAfterInteractionClears() throws Exception {
        Rs2NpcModel defeated = npc("Abyssal demon", false, 0, false);
        Rs2NpcModel next = npc("Abyssal demon", false, -1, false);

        handleCombat();

        verify(defeated, never()).click("Attack");
        verify(next).click("Attack");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fallbackRetaliationSkipsZeroHpNpc(boolean taskKnown) throws Exception {
        if (!taskKnown) {
            slayer.when(Rs2Slayer::getSlayerMonsters).thenReturn(List.of());
        }
        Rs2NpcModel defeated = npc("Goblin", false, 0, true);
        Rs2NpcModel next = npc("Rat", false, -1, true);

        handleCombat();

        verify(defeated, never()).click("Attack");
        verify(next).click("Attack");
    }

    @Test
    void dyingSuperiorDoesNotBlockRegularTargets() throws Exception {
        when(config.prioritizeSuperiors()).thenReturn(true);
        Rs2NpcModel superior = npc("Greater abyssal demon", false, 0, true);
        Rs2NpcModel next = npc("Abyssal demon", false, -1, false);
        setCurrentTarget(superior);

        handleCombat();

        verify(superior, never()).click("Attack");
        verify(next).click("Attack");
    }

    @Test
    void livingSuperiorStillTakesPriority() throws Exception {
        when(config.prioritizeSuperiors()).thenReturn(true);
        Rs2NpcModel regular = npc("Abyssal demon", false, -1, true);
        Rs2NpcModel superior = npc("Greater abyssal demon", false, -1, false);

        handleCombat();

        verify(regular, never()).click("Attack");
        verify(superior).click("Attack");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void keepsCurrentLivingTargetIncludingUnknownHealth(int healthRatio) throws Exception {
        Rs2NpcModel current = npc("Abyssal demon", false, healthRatio, true);
        Rs2NpcModel next = npc("Abyssal demon", false, -1, false);
        setCurrentTarget(current);

        handleCombat();

        verify(current, never()).click("Attack");
        verify(next, never()).click("Attack");
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "true, -1"})
    void burstSwitchesPastDefeatedTargetInSamePass(boolean dead, int healthRatio) throws Exception {
        SlayerTaskProfileJson profile = new SlayerTaskProfileJson();
        profile.setStyle("burst");
        profile.setMinStackSize(1);
        setField("activeJsonProfile", profile);
        Rs2NpcModel defeated = npc("Abyssal demon", dead, healthRatio, true);
        Rs2NpcModel next = npc("Abyssal demon", false, -1, true);
        setCurrentTarget(defeated);
        try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class)) {
            magic.when(Rs2Magic::getCurrentAutoCastSpell).thenReturn(Rs2CombatSpells.ICE_BURST);

            handleCombat();

            verify(defeated, never()).click("Attack");
            verify(next).click("Attack");
        }
    }

    @Test
    void skipsNpcFightingAnotherPlayer() throws Exception {
        Rs2NpcModel taken = npc("Abyssal demon", false, -1, false);
        Rs2NpcModel free = npc("Abyssal demon", false, -1, false);
        when(taken.getInteracting()).thenReturn(mock(Player.class));

        handleCombat();

        verify(taken, never()).click("Attack");
        verify(free).click("Attack");
    }

    @Test
    void skipsNpcAnotherPlayerIsAttacking() throws Exception {
        Rs2NpcModel taken = npc("Abyssal demon", false, -1, false);
        Rs2NpcModel free = npc("Abyssal demon", false, -1, false);
        otherPlayerTargeting(taken);

        handleCombat();

        verify(taken, never()).click("Attack");
        verify(free).click("Attack");
    }

    @Test
    void stillAttacksNpcFightingAnotherPlayerInMultiCombat() throws Exception {
        player.when(Rs2Player::isInMulti).thenReturn(true);
        Rs2NpcModel shared = npc("Abyssal demon", false, -1, false);
        when(shared.getInteracting()).thenReturn(mock(Player.class));
        otherPlayerTargeting(shared);

        handleCombat();

        verify(shared).click("Attack");
    }

    @Test
    void allTakenNpcsStartCrashTimerInsteadOfAttacking() throws Exception {
        when(config.hopWhenCrashed()).thenReturn(true);
        Rs2NpcModel first = npc("Abyssal demon", false, -1, false);
        Rs2NpcModel second = npc("Abyssal demon", false, -1, false);
        when(first.getInteracting()).thenReturn(mock(Player.class));
        otherPlayerTargeting(second);

        handleCombat();

        verify(first, never()).click("Attack");
        verify(second, never()).click("Attack");
        assertTrue((boolean) getField("isSearchingForTargets"));
    }

    @Test
    void takenMessageExcludesLastClickedNpcOnNextPass() throws Exception {
        Rs2NpcModel rejected = npc("Abyssal demon", false, -1, false);
        Rs2NpcModel free = npc("Abyssal demon", false, -1, false);
        when(rejected.getIndex()).thenReturn(101);
        when(free.getIndex()).thenReturn(102);

        handleCombat();
        verify(rejected).click("Attack");
        script.onTargetTakenMessage();
        handleCombat();

        verify(rejected).click("Attack");
        verify(free).click("Attack");
    }

    @Test
    void takenMessageDoesNotExcludeNpcAttackingUs() {
        assertFalse(SlayerScript.isTakenBySomeoneElse(localPlayer, localPlayer, true, true));
        assertTrue(SlayerScript.isTakenBySomeoneElse(null, localPlayer, false, true));
        assertFalse(SlayerScript.isTakenBySomeoneElse(null, localPlayer, false, false));
        assertFalse(SlayerScript.isTakenBySomeoneElse(mock(NPC.class), localPlayer, false, false));
    }

    @Test
    void recognisesTargetTakenGameMessage() {
        assertTrue(SlayerPlugin.isTargetTakenMessage("Someone else is fighting that."));
        assertFalse(SlayerPlugin.isTargetTakenMessage("I'm already under attack."));
        assertFalse(SlayerPlugin.isTargetTakenMessage(null));
    }

    @Test
    void allTakenWaitIsAnnouncedOncePerWaitingPeriod() throws Exception {
        Rs2NpcModel taken = npc("Abyssal demon", false, -1, false);
        when(taken.getInteracting()).thenReturn(mock(Player.class));

        handleCombat();
        assertTrue((boolean) getField("takenWaitAnnounced"));

        when(taken.getInteracting()).thenReturn(null);
        handleCombat();

        verify(taken).click("Attack");
        assertFalse((boolean) getField("takenWaitAnnounced"));
    }

    @Test
    void noAllTakenAnnouncementWhenNoMatchingMonsterIsNearby() throws Exception {
        npc("Goblin", false, -1, false);

        handleCombat();

        assertFalse((boolean) getField("takenWaitAnnounced"));
    }

    @Test
    void allTakenMessageExplainsWhatHappensNext() {
        List<String> names = List.of("Abyssal demon");
        assertEquals("All Abyssal demon here are being fought by other players - waiting for a free one."
                        + " Hopping worlds in 20 seconds if none frees up.",
                SlayerScript.allTakenMessage(names, true, true, 20));
        assertEquals("All Abyssal demon here are being fought by other players - waiting for a free one."
                        + " Turn on Hop When Crashed in the Combat settings to hop automatically.",
                SlayerScript.allTakenMessage(names, false, true, 20));
        assertEquals("All Abyssal demon here are being fought by other players - waiting for a free one.",
                SlayerScript.allTakenMessage(names, true, false, 20));
    }

    @Test
    void targetTakenMessageNamesTheMonster() {
        assertEquals("Abyssal demon is being fought by someone else - skipping it for 30 seconds",
                SlayerScript.targetTakenMessage("Abyssal demon", 30));
        assertEquals("Target is being fought by someone else - skipping it for 30 seconds",
                SlayerScript.targetTakenMessage(null, 30));
    }

    private void otherPlayerTargeting(Rs2NpcModel target) {
        NPC rawTarget = target.getNpc();
        Player other = mock(Player.class);
        when(other.getInteracting()).thenReturn(rawTarget);
        Rs2PlayerModel model = mock(Rs2PlayerModel.class);
        when(model.getPlayer()).thenReturn(other);
        otherPlayers.add(model);
    }

    private Object getField(String name) throws Exception {
        Field field = SlayerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private Rs2NpcModel npc(String name, boolean dead, int healthRatio, boolean attacking) {
        NPC raw = mock(NPC.class);
        when(raw.getName()).thenReturn(name);
        when(raw.isDead()).thenReturn(dead);
        when(raw.getHealthRatio()).thenReturn(healthRatio);
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getNpc()).thenReturn(raw);
        when(npc.getName()).thenReturn(name);
        when(npc.isDead()).thenReturn(dead);
        when(npc.getHealthRatio()).thenReturn(healthRatio);
        when(npc.getInteracting()).thenReturn(attacking ? localPlayer : null);
        when(npc.getWorldLocation()).thenReturn(ORIGIN);
        scene.add(npc);
        return npc;
    }

    private void setCurrentTarget(Rs2NpcModel target) {
        NPC rawTarget = target.getNpc();
        player.when(Rs2Player::getInteracting).thenReturn(rawTarget);
        player.when(Rs2Player::isInteracting).thenReturn(true);
    }

    private void handleCombat() throws Exception {
        Method method = SlayerScript.class.getDeclaredMethod("handleCombat");
        method.setAccessible(true);
        method.invoke(script);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = SlayerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }
}
