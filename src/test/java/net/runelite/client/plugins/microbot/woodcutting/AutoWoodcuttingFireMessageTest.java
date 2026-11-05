package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AutoWoodcuttingFireMessageTest {

    private static AutoWoodcuttingScript receive(ChatMessageType type, String message) {
        AutoWoodcuttingPlugin plugin = new AutoWoodcuttingPlugin();
        AutoWoodcuttingScript script = mock(AutoWoodcuttingScript.class);
        script.cannotLightFire = false;
        plugin.autoWoodcuttingScript = script;

        ChatMessage event = new ChatMessage();
        event.setType(type);
        event.setMessage(message);
        plugin.onChatMessage(event);
        return script;
    }

    @Test
    void gameMessageAsSentByTheGameFlagsBlockedTile() {
        assertTrue(receive(ChatMessageType.GAMEMESSAGE, "You can't light a fire here.").cannotLightFire);
    }

    @Test
    void spamMessageFlagsBlockedTile() {
        assertTrue(receive(ChatMessageType.SPAM, "You can't light a fire here.").cannotLightFire);
    }

    @Test
    void colouredMessageFlagsBlockedTile() {
        assertTrue(receive(ChatMessageType.GAMEMESSAGE, "<col=ef1020>You can't light a fire here.</col>").cannotLightFire);
    }

    @Test
    void unrelatedMessageLeavesFlagUnset() {
        assertFalse(receive(ChatMessageType.GAMEMESSAGE, "The fire catches and the logs begin to burn.").cannotLightFire);
    }

    @Test
    void publicChatDoesNotFlagBlockedTile() {
        assertFalse(receive(ChatMessageType.PUBLICCHAT, "You can't light a fire here.").cannotLightFire);
    }
}
