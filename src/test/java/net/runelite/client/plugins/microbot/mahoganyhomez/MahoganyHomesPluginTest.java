package net.runelite.client.plugins.microbot.mahoganyhomez;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MahoganyHomesPluginTest {

    @Test
    void plankSackCountReadsSinglePlankTypeCheck() {
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount("Mahogany planks: 28"));
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount("Mahogany planks: 28"));
        assertEquals(0, MahoganyHomesPlugin.parsePlankSackCount("Teak planks: 0"));
    }

    @Test
    void plankSackCountSumsEveryPlankTypeInCheck() {
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount(
                "Basic planks: 3, Oak planks: 5, Teak planks: 8, Mahogany planks: 12"));
        assertEquals(17, MahoganyHomesPlugin.parsePlankSackCount("Oak planks: 5, Mahogany planks: 12"));
    }

    @Test
    void plankSackCountIgnoresColourTags() {
        assertEquals(14, MahoganyHomesPlugin.parsePlankSackCount(
                "<col=ef1020>Basic planks: 0</col>, <col=ef1020>Mahogany planks: 14</col>"));
    }

    @Test
    void plankSackCountIsNegativeForOtherMessages() {
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount(null));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount(""));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("Your sack is full."));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("You haven't got any planks that can go in the sack."));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("Expert Contract: Go see Noella in Ardougne."));
    }

    @Test
    void reminderMatchesBeginnerContractWithArticleA() {
        Matcher m = MahoganyHomesPlugin.REMINDER_PATTERN.matcher(
                "You're currently on a Beginner Contract. Go see Norman, south of the fountain in Falador. You can get another job once you have furnished his home.");
        assertTrue(m.matches());
        assertEquals("Beginner", m.group(1));
        assertEquals("Norman", m.group(2));
    }

    @Test
    void reminderMatchesNoviceContractWithArticleA() {
        Matcher m = MahoganyHomesPlugin.REMINDER_PATTERN.matcher(
                "You're currently on a Novice Contract. Go see Jess, upstairs in the building south of the church in East Ardougne. You can get another job once you have furnished her home.");
        assertTrue(m.matches());
        assertEquals("Novice", m.group(1));
        assertEquals("Jess", m.group(2));
    }

    @Test
    void reminderStillMatchesExpertContractWithArticleAn() {
        Matcher m = MahoganyHomesPlugin.REMINDER_PATTERN.matcher(
                "You're currently on an Expert Contract. Go see Noella, north of East Ardougne market. You can get another job once you have furnished her home.");
        assertTrue(m.matches());
        assertEquals("Expert", m.group(1));
        assertEquals("Noella", m.group(2));
    }
}
