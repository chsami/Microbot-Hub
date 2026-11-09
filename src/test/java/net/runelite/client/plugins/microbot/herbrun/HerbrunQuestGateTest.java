package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingRegion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HerbrunQuestGateTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private MockedStatic<Rs2Walker> rs2Walker;
    private HerbrunConfig config;
    private FarmingHandler farmingHandler;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        rs2Player = mockStatic(Rs2Player.class);
        rs2Walker = mockStatic(Rs2Walker.class);
        config = mock(HerbrunConfig.class);
        when(config.enableWeiss()).thenReturn(true);
        when(config.enableTrollheim()).thenReturn(true);
        when(config.enableCatherby()).thenReturn(true);
        farmingHandler = mock(FarmingHandler.class);
        when(farmingHandler.predictPatch(any())).thenReturn(CropState.HARVESTABLE);
    }

    @AfterEach
    void tearDown() {
        rs2Walker.close();
        rs2Player.close();
        microbot.close();
    }

    @Test
    void weissIsSkippedWithoutMakingFriendsWithMyArm() {
        questState(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.IN_PROGRESS);

        assertFalse(patch("Weiss").isEnabled());
    }

    @Test
    void weissIsEnabledOnceMakingFriendsWithMyArmIsFinished() {
        questState(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.FINISHED);

        assertTrue(patch("Weiss").isEnabled());
    }

    @Test
    void trollStrongholdIsSkippedWithoutMyArmsBigAdventure() {
        questState(Quest.MY_ARMS_BIG_ADVENTURE, QuestState.NOT_STARTED);

        assertFalse(patch("Troll Stronghold").isEnabled());
    }

    @Test
    void trollStrongholdIsEnabledOnceMyArmsBigAdventureIsFinished() {
        questState(Quest.MY_ARMS_BIG_ADVENTURE, QuestState.FINISHED);

        assertTrue(patch("Troll Stronghold").isEnabled());
    }

    @Test
    void weissStaysOffWhenDisabledInConfigEvenWithTheQuest() {
        when(config.enableWeiss()).thenReturn(false);
        questState(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.FINISHED);

        assertFalse(patch("Weiss").isEnabled());
    }

    @Test
    void patchesWithoutAQuestRequirementIgnoreQuestState() {
        assertTrue(patch("Catherby").isEnabled());
    }

    @Test
    void unreachablePatchIsSkippedAfterRepeatedFailures() throws Exception {
        HerbrunScript script = scriptWalkingTo(patch("Catherby"));
        rs2Walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.UNREACHABLE);

        script.walkToCurrentPatch();
        script.walkToCurrentPatch();
        assertNotNull(currentPatch(script));

        script.walkToCurrentPatch();
        assertNull(currentPatch(script));
    }

    @Test
    void patchIsKeptWhileTheWalkerIsStillMoving() throws Exception {
        HerbrunScript script = scriptWalkingTo(patch("Catherby"));
        rs2Walker.when(() -> Rs2Walker.walkWithState(any(WorldPoint.class), anyInt())).thenReturn(WalkerState.MOVING);

        for (int i = 0; i < 5; i++) {
            script.walkToCurrentPatch();
        }

        assertNotNull(currentPatch(script));
    }

    private void questState(Quest quest, QuestState state) {
        rs2Player.when(() -> Rs2Player.getQuestState(quest)).thenReturn(state);
    }

    private HerbPatch patch(String regionName) {
        FarmingRegion region = mock(FarmingRegion.class);
        when(region.getName()).thenReturn(regionName);
        FarmingPatch farmingPatch = mock(FarmingPatch.class);
        when(farmingPatch.getRegion()).thenReturn(region);
        return new HerbPatch(farmingPatch, config, farmingHandler);
    }

    private HerbrunScript scriptWalkingTo(HerbPatch patch) throws Exception {
        HerbrunScript script = new HerbrunScript(mock(HerbrunPlugin.class), config);
        Field field = HerbrunScript.class.getDeclaredField("currentPatch");
        field.setAccessible(true);
        field.set(script, patch);
        return script;
    }

    private static HerbPatch currentPatch(HerbrunScript script) throws Exception {
        Field field = HerbrunScript.class.getDeclaredField("currentPatch");
        field.setAccessible(true);
        return (HerbPatch) field.get(script);
    }
}
