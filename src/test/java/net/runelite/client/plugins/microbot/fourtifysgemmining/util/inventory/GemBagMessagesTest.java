package net.runelite.client.plugins.microbot.fourtifysgemmining.util.inventory;

import net.runelite.api.ChatMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GemBagMessagesTest
{
    private static final String COUNTS = "Sapphires: 1 / Emeralds: 2 / Rubies: 3 / Diamonds: 4 / Dragonstones: 5";
    private static final String STORED = "You put it straight into your open gem bag.";

    @Test
    public void checkReadsCompleteVanillaAndPrivateServerReplies()
    {
        assertTotal(15, COUNTS);
        assertTotal(15, COUNTS.replace(" / Diamonds", " Diamonds"));
        assertTotal(15, "sapphire: 1 / EMERALD: 2 / ruby: 3 / Diamond: 4 / dragonstone: 5");
        assertTotal(15, "<col=ff0000>" + COUNTS.replace(" / ", "<br>") + "</col>");
        assertTotal(0, "The gem bag is empty.");
        assertTotal(0, "Sapphires: 0 / Emeralds: 0 / Rubies: 0 / Diamonds: 0 / Dragonstones: 0");
        assertTotal(300, "Sapphires: 60 / Emeralds: 60 / Rubies: 60 / Diamonds: 60 / Dragonstones: 60");
    }

    @Test
    public void checkRejectsIncompleteDuplicateOrMalformedCounts()
    {
        for (String invalid : new String[] {
            COUNTS.replace("1 /", "61 /"), COUNTS.replace("1 /", "-1 /"),
            COUNTS.replace("1 /", "1.5 /"), COUNTS.replace("1 /", "999999999999 /"),
            COUNTS.replace("1 /", "one /"), COUNTS.replace("1 /", "/"),
            COUNTS.replace("Rubies", "Sapphires"), COUNTS + " / Sapphires: 1",
            COUNTS.replace(" / Dragonstones: 5", ""), "Someone said: " + COUNTS,
            COUNTS + " extra", "The gem bag is empty. " + COUNTS,
            "The gem bag is empty today.", "", "<col=ff0000>"})
        {
            assertFalse(GemBagMessages.checkedTotal(ChatMessageType.GAMEMESSAGE, invalid).isPresent(), invalid);
        }
        assertFalse(GemBagMessages.checkedTotal(ChatMessageType.GAMEMESSAGE, null).isPresent());
    }

    @Test
    public void storageRequiresExactConfirmationWithAnOptionalExactFindLine()
    {
        for (ChatMessageType type : new ChatMessageType[] {ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM})
        {
            assertTrue(GemBagMessages.stored(type, STORED));
            for (String gem : new String[] {"a Sapphire", "a Ruby", "an Emerald", "a Diamond", "a Dragonstone"})
            {
                assertTrue(GemBagMessages.stored(type, "You just found " + gem + "!<br><col=ff0000>" + STORED + "</col>"));
            }
            for (String invalid : new String[] {"You just found a Emerald! " + STORED,
                "You just found an Opal! " + STORED, "You just mined a Sapphire!", "Someone: " + STORED,
                STORED + " " + STORED, STORED + " extra", "You put it into your bag.", ""})
            {
                assertFalse(GemBagMessages.stored(type, invalid), invalid);
            }
            assertFalse(GemBagMessages.stored(type, null));
        }
    }

    @Test
    public void fillAcknowledgesBothSuccessAndTheEvidencedNoTransferReplies()
    {
        for (String reply : new String[] {"You add the gem to your bag.", "You add the gems to your bag.",
            "You have nothing suitable for storing in the bag.", "Your bag is too full to hold all your gems.",
            "You have no uncut gems to put in the bag.", "Your gem bag cannot hold any more of those gems."})
        {
            assertTrue(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, reply));
            assertTrue(GemBagMessages.fillAcknowledged(ChatMessageType.SPAM, "<col=ff0000>" + reply + "</col>"));
            assertFalse(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, "Someone: " + reply));
        }
        assertFalse(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, STORED));
        assertFalse(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, "The gem bag is empty."));
        assertFalse(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, "You add the gems to your bag. extra"));
        assertFalse(GemBagMessages.fillAcknowledged(ChatMessageType.GAMEMESSAGE, null));
    }

    @Test
    public void recognizesOnlyCompleteShiloMiningSuccessMessages()
    {
        for (String result : new String[] {"an Opal", "a piece of Jade", "a Red Topaz",
            "a Sapphire", "an Emerald", "a Ruby", "a Diamond"})
        {
            String message = "You just mined " + result + "!";
            assertTrue(GemBagMessages.mined(ChatMessageType.SPAM, message));
            assertFalse(GemBagMessages.mined(ChatMessageType.PUBLICCHAT, message));
            assertFalse(GemBagMessages.mined(ChatMessageType.SPAM, message + " extra"));
        }
        assertFalse(GemBagMessages.mined(ChatMessageType.GAMEMESSAGE, "You just mined a coal!"));
        assertFalse(GemBagMessages.mined(ChatMessageType.SPAM, null));
    }

    @Test
    public void allOtherMessageTypesAndNullTypesAreIgnored()
    {
        for (ChatMessageType type : ChatMessageType.values())
        {
            if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
            {
                assertRejectedType(type);
            }
        }
        assertRejectedType(null);
    }

    private static void assertRejectedType(ChatMessageType type)
    {
        assertFalse(GemBagMessages.checkedTotal(type, COUNTS).isPresent());
        assertFalse(GemBagMessages.checkedTotal(type, "The gem bag is empty.").isPresent());
        assertFalse(GemBagMessages.stored(type, STORED));
        assertFalse(GemBagMessages.fillAcknowledged(type, "You add the gems to your bag."));
    }

    private static void assertTotal(int total, String reply)
    {
        assertEquals(total, GemBagMessages.checkedTotal(ChatMessageType.GAMEMESSAGE, reply).getAsInt());
        assertEquals(total, GemBagMessages.checkedTotal(ChatMessageType.SPAM, reply).getAsInt());
    }
}
