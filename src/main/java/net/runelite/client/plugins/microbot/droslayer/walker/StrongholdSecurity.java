package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Stronghold of Security helper for Efficient Walker.
 *
 * <p>This helper supplies the four level-exit portal transports,
 * identifies the Stronghold portal/door objects used by the walker, checks whether each level's
 * reward gate is already satisfied, and selects the unique recognized answer from a security
 * question dialogue.</p>
 */
final class StrongholdSecurity
{
    private static final Set<Integer> DOOR_OBJECT_IDS = Set.of(
        17009,
        17100,
        19206,
        19207,
        23653,
        23654,
        23727,
        23728);

    private static final Set<String> NORMALIZED_SECURITY_ANSWERS = normalizedAnswerSet(
        "nothing, it's a fake.",
        "report the player for phishing.",
        "don't type in my password backwards and report the player.",
        "don't give out your password to anyone. not even close friends.",
        "authenticator and two-step login on my registered email.",
        "no, you should never allow anyone to use your account.",
        "virus scan my device then change my password.",
        "do not visit the website and report the player who messaged you.",
        "no.",
        "don't share your information and report the player.",
        "it's never reused on other websites or accounts.",
        "two-factor authentication on your account and your registered email.",
        "me.",
        "don't give them my password.",
        "politely tell them no, then use the 'report abuse' button.",
        "nobody.",
        "no way! i'm reporting you to jagex!",
        "it's never used on other websites or accounts.",
        "don't give them the information and send an 'abuse report'.",
        "through account settings on oldschool.runescape.com.",
        "don't tell them anything and click the 'report abuse' button.",
        "read the text and follow the advice given.",
        "set up two-factor authentication with my email provider.",
        "secure my device and reset my password.",
        "delete it - it's a fake!",
        "report the incident and do not click any links.",
        "talk to any banker.",
        "no, you should never buy an account.",
        "decline the offer and report that player.",
        "only on the old school runescape website.",
        "no way! you'll just take my gold for your own! reported!",
        "report the stream as a scam. real jagex streams have a 'verified' mark.",
        "the birthday of a famous person or event.",
        "no, you should never allow anyone to level your account.",
        "nope, you're tricking me into going somewhere dangerous.",
        "use the account recovery system.",
        "politely tell them no and then use the 'report abuse' button.");

    private StrongholdSecurity()
    {
    }

    static boolean isStrongholdDoor(int objectId)
    {
        return DOOR_OBJECT_IDS.contains(objectId);
    }

    static List<TransportCatalog.TransportEdge> createPortalTransports()
    {
        return List.of(
            TransportCatalog.createObjectTransport(
                new WorldPoint(1863, 5239, 0),
                new WorldPoint(1914, 5222, 0),
                "Use",
                "Portal",
                20786),
            TransportCatalog.createObjectTransport(
                new WorldPoint(2040, 5240, 0),
                new WorldPoint(2021, 5223, 0),
                "Use",
                "Portal",
                19005),
            TransportCatalog.createObjectTransport(
                new WorldPoint(2120, 5257, 0),
                new WorldPoint(2146, 5287, 0),
                "Use",
                "Portal",
                23707),
            TransportCatalog.createObjectTransport(
                new WorldPoint(2364, 5212, 0),
                new WorldPoint(2341, 5219, 0),
                "Use",
                "Portal",
                23922));
    }

    static boolean isFloorPortal(int objectId)
    {
        return objectId == 20786
            || objectId == 19005
            || objectId == 23707
            || objectId == 23922;
    }

    static boolean canUsePortal(int objectId, int progress, IntUnaryOperator varbitValue)
    {
        switch (objectId)
        {
            case 20786:
                return progress >= 26 || varbitValue.applyAsInt(2309) == 1;
            case 19005:
                return progress >= 51 || varbitValue.applyAsInt(2310) == 1;
            case 23707:
                return progress >= 76 || varbitValue.applyAsInt(2311) == 1;
            case 23922:
                return varbitValue.applyAsInt(2312) == 1;
            default:
                return true;
        }
    }

    static String selectDialogueAnswer(List<String> options)
    {
        String match = null;
        for (String option : options)
        {
            if (!NORMALIZED_SECURITY_ANSWERS.contains(normalizeAnswer(option)))
            {
                continue;
            }

            if (match != null)
            {
                return null;
            }
            match = option;
        }
        return match;
    }

    private static Set<String> normalizedAnswerSet(String... values)
    {
        Set<String> normalized = new HashSet<>();
        Arrays.stream(values).map(StrongholdSecurity::normalizeAnswer).forEach(normalized::add);
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeAnswer(String value)
    {
        return Text.sanitizeMultilineText(value)
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
