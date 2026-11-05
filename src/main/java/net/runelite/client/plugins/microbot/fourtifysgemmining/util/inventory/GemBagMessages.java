package net.runelite.client.plugins.microbot.fourtifysgemmining.util.inventory;

import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;

/** Recognizes complete bag replies without treating player chat as game state. */
public final class GemBagMessages
{
    private static final String SEPARATOR = "(?:\\s*/\\s*|\\s+)";
    private static final Pattern CONTENTS = Pattern.compile(
        "Sapphires?:\\s*([0-9]{1,2})" + SEPARATOR
            + "Emeralds?:\\s*([0-9]{1,2})" + SEPARATOR
            + "Rub(?:y|ies):\\s*([0-9]{1,2})" + SEPARATOR
            + "Diamonds?:\\s*([0-9]{1,2})" + SEPARATOR
            + "Dragonstones?:\\s*([0-9]{1,2})", Pattern.CASE_INSENSITIVE);
    private static final String STORED = "You put it straight into your open gem bag.";
    private static final Pattern FOUND_AND_STORED = Pattern.compile(
        "You just found (?:a (?:Sapphire|Ruby|Diamond|Dragonstone)|an Emerald)! "
            + Pattern.quote(STORED));
    private static final Pattern MINED = Pattern.compile(
        "You just mined (?:an Opal|a piece of Jade|a Red Topaz|a Sapphire|an Emerald|a Ruby|a Diamond)!");
    private static final Set<String> FILL_REPLIES = Set.of(
        "You add the gem to your bag.",
        "You add the gems to your bag.",
        "You have nothing suitable for storing in the bag.",
        "Your bag is too full to hold all your gems.",
        "You have no uncut gems to put in the bag.",
        "Your gem bag cannot hold any more of those gems.");
    private GemBagMessages()
    {
    }

    public static OptionalInt checkedTotal(ChatMessageType type, String message)
    {
        String text = gameText(type, message);
        if (text.equals("The gem bag is empty."))
        {
            return OptionalInt.of(0);
        }
        Matcher matcher = CONTENTS.matcher(text);
        if (!matcher.matches())
        {
            return OptionalInt.empty();
        }
        int total = 0;
        for (int group = 1; group <= 5; group++)
        {
            int count = Integer.parseInt(matcher.group(group));
            if (count > 60)
            {
                return OptionalInt.empty();
            }
            total += count;
        }
        return OptionalInt.of(total);
    }

    public static boolean stored(ChatMessageType type, String message)
    {
        String text = gameText(type, message);
        return text.equals(STORED) || FOUND_AND_STORED.matcher(text).matches();
    }

    public static boolean fillAcknowledged(ChatMessageType type, String message)
    {
        return FILL_REPLIES.contains(gameText(type, message));
    }

    public static boolean mined(ChatMessageType type, String message)
    {
        return MINED.matcher(gameText(type, message)).matches();
    }

    private static String gameText(ChatMessageType type, String message)
    {
        if ((type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM) || message == null)
        {
            return "";
        }
        return message.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "")
            .replaceAll("\\s+", " ").trim();
    }
}
