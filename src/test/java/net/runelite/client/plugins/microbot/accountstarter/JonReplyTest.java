package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.runelite.client.plugins.microbot.accountstarter.logic.JonReply;
import org.junit.jupiter.api.Test;

class JonReplyTest {
    @Test
    void everyDataMapReplyMapsToItsKind() {
        assertEquals(JonReply.NO_REWARDS, JonReply.parse("I have no rewards to give you at the moment."));
        assertEquals(JonReply.MEMBERS_LEFT_ONLY, JonReply.parse(
                "I have no rewards to give you at the moment. But I do have some members rewards left to give you, visit me on a members world to receive them."));
        assertEquals(JonReply.MEMBERS_LEFT_ONLY, JonReply.parse(
                "But I do have some members rewards left to give you, visit me on a members world to receive them."));
        assertEquals(JonReply.NEED_SPACE_NOTHING_GIVEN, JonReply.parse("You should make sure you have space before asking for rewards."));
        assertEquals(JonReply.ALL_GIVEN_MEMBERS_LEFT, JonReply.parse(
                "That is the last of the rewards you have currently earned. But I do have some members rewards left to give you, visit me on a members world to receive them."));
        assertEquals(JonReply.ALL_GIVEN, JonReply.parse("That is the last of your rewards which you have earned."));
        assertEquals(JonReply.NEED_SPACE_PART_GIVEN, JonReply.parse(
                "I can't give you any more rewards until you come back with more inventory space."));
        assertEquals(JonReply.KIT_GIVEN, JonReply.parse("Adventurer Jon gives you a Combat path starter kit"));
        assertEquals(JonReply.KIT_ALREADY_CLAIMED, JonReply.parse("You seem to have already claimed all of your starter kits."));
        assertEquals(JonReply.NOT_PARTICIPATING, JonReply.parse(
                "You are currently not participating in Adventure Paths. Would you like to join back in?"));
        assertEquals(JonReply.OPT_OUT_PROMPT, JonReply.parse("Opt out of Adventure Paths?"));
    }

    @Test
    void tagsAndLineBreaksAreStripped() {
        assertEquals(JonReply.ALL_GIVEN, JonReply.parse("<col=0000ff>That is the last of your rewards which you have earned.</col>"));
        assertEquals(JonReply.NEED_SPACE_PART_GIVEN, JonReply.parse(
                "I can't give you any more rewards until you come back<br>with more inventory space."));
        assertEquals(JonReply.NO_REWARDS, JonReply.parse("I HAVE NO REWARDS TO GIVE YOU AT THE MOMENT."));
    }

    @Test
    void unknownTextIsUnknown() {
        assertEquals(JonReply.UNKNOWN, JonReply.parse("Start by opening your quest panel and then selecting the flashing yellow star."));
        assertEquals(JonReply.UNKNOWN, JonReply.parse(""));
        assertEquals(JonReply.UNKNOWN, JonReply.parse(null));
    }
}
