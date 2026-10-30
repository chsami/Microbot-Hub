package net.runelite.client.plugins.microbot.agility.courses;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PyramidCourseTurnInTest {

    private static final WorldPoint SIMON_LOCATION = new WorldPoint(3343, 2827, 0);
    private static final WorldPoint START_POINT = new WorldPoint(3354, 2830, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Dialogue> dialogue;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Player> player;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        dialogue = mockStatic(Rs2Dialogue.class);
        walker = mockStatic(Rs2Walker.class);
        player = mockStatic(Rs2Player.class);

        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.withName(any())).thenReturn(query);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);

        inventory.when(() -> Rs2Inventory.count(anyInt())).thenReturn(5);
        inventory.when(Rs2Inventory::isFull).thenReturn(true);
        inventory.when(() -> Rs2Inventory.contains(ItemID.AGILITY_PYRAMID_GOLD_PYRAMID)).thenReturn(true, false);

        logger = (Logger) LoggerFactory.getLogger(PyramidCourse.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        player.close();
        walker.close();
        dialogue.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void nullPlayerLocationAfterTurnInSkipsWalkBackWithoutError() {
        player.when(Rs2Player::getWorldLocation).thenReturn(null);

        assertTrue(new PyramidCourse().handleWalkToStart(SIMON_LOCATION));

        walker.verify(() -> Rs2Walker.walkTo(START_POINT), never());
        assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
    }

    @Test
    void farPlayerLocationAfterTurnInWalksBackToStart() {
        player.when(Rs2Player::getWorldLocation).thenReturn(SIMON_LOCATION.dx(-20));

        assertTrue(new PyramidCourse().handleWalkToStart(SIMON_LOCATION));

        walker.verify(() -> Rs2Walker.walkTo(START_POINT));
        assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
    }
}
