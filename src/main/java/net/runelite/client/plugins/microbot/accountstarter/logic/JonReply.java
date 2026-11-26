package net.runelite.client.plugins.microbot.accountstarter.logic;

public enum JonReply {
    NO_REWARDS, MEMBERS_LEFT_ONLY, NEED_SPACE_NOTHING_GIVEN, NEED_SPACE_PART_GIVEN, ALL_GIVEN, ALL_GIVEN_MEMBERS_LEFT,
    KIT_GIVEN, KIT_ALREADY_CLAIMED, NOT_PARTICIPATING, OPT_OUT_PROMPT, UNKNOWN;

    public static JonReply parse(String text) {
        if (text == null) {
            return UNKNOWN;
        }
        String t = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "")
                .replaceAll("\\s+", " ").trim().toLowerCase();
        boolean membersLeft = t.contains("members rewards left");
        if (t.contains("opt out of adventure paths")) return OPT_OUT_PROMPT;
        if (t.contains("not participating in adventure paths")) return NOT_PARTICIPATING;
        if (t.contains("already claimed all of your starter kits")) return KIT_ALREADY_CLAIMED;
        if (t.contains("gives you a") && t.contains("starter kit")) return KIT_GIVEN;
        if (t.contains("make sure you have space before asking")) return NEED_SPACE_NOTHING_GIVEN;
        if (t.contains("more inventory space")) return NEED_SPACE_PART_GIVEN;
        if (t.contains("that is the last of")) return membersLeft ? ALL_GIVEN_MEMBERS_LEFT : ALL_GIVEN;
        if (membersLeft) return MEMBERS_LEFT_ONLY;
        if (t.contains("no rewards to give you")) return NO_REWARDS;
        return UNKNOWN;
    }
}
