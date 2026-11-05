package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackChatMessageTest
{
    private static final String[] MESSAGES = {
            "You swing your blackjack and render them unconscious.",
            "Your blow only glances off them.",
            "You pick the thug's pocket.",
            "You fail to pick the thug's pocket.",
            "You've been stunned!",
            "You don't have enough inventory space.",
            "The merchant converts your banknote."
    };
    private static final String[] OUTCOMES = {
            "KNOCKOUT_SUCCESS", "KNOCKOUT_FAILED", "PICKPOCKET_SUCCESS",
            "PICKPOCKET_FAILED", "STUNNED", "INVENTORY_FULL", "WINE_EXCHANGED"
    };
    private BlackjackScript script;
    private BlackjackPlugin plugin;
    private Queue<?> outcomes;

    @BeforeEach
    void setUp() throws Exception
    {
        script = new BlackjackScript();
        plugin = new BlackjackPlugin();
        Field scriptField = BlackjackPlugin.class.getDeclaredField("script");
        scriptField.setAccessible(true);
        scriptField.set(plugin, script);
        Field queueField = BlackjackScript.class.getDeclaredField("outcomes");
        queueField.setAccessible(true);
        outcomes = (Queue<?>) queueField.get(script);
    }

    @AfterEach
    void dispose() throws Exception
    {
        Field executor = Script.class.getDeclaredField("scheduledExecutorService");
        executor.setAccessible(true);
        ((ScheduledExecutorService) executor.get(script)).shutdownNow();
    }

    @Test
    void playerAndOtherUntrustedChannelsCannotSpoofAnyOutcome()
    {
        for (ChatMessageType type : ChatMessageType.values())
        {
            if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM)
            {
                continue;
            }
            for (String message : MESSAGES)
            {
                dispatch(type, message);
                assertTrue(outcomes.isEmpty(), type + ": " + message);
            }
        }
        dispatch(null, MESSAGES[0]);
        assertTrue(outcomes.isEmpty());
    }

    @Test
    void gameAndFilteredGameMessagesStillConfirmOutcomes()
    {
        for (ChatMessageType type : new ChatMessageType[]{ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM})
        {
            for (int i = 0; i < MESSAGES.length; i++)
            {
                dispatch(type, MESSAGES[i]);
                assertEquals(1, outcomes.size());
                assertEquals(OUTCOMES[i], outcomes.remove().toString());
            }
        }
    }

    @Test
    void missingOrUnrelatedGameMessagesDoNotConfirmOutcomes()
    {
        dispatch(ChatMessageType.GAMEMESSAGE, null);
        dispatch(ChatMessageType.SPAM, null);
        dispatch(ChatMessageType.GAMEMESSAGE, "Nothing interesting happens.");
        assertTrue(outcomes.isEmpty());
    }

    private void dispatch(ChatMessageType type, String message)
    {
        ChatMessage event = new ChatMessage();
        event.setType(type);
        event.setMessage(message);
        plugin.onChatMessage(event);
    }
}
