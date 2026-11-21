package net.runelite.client.plugins.microbot.aiofighter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class AIOFighterPluginNpcListTest {

    @Test
    void addsSeparatorWhenListHasNoTrailingComma() {
        assertEquals("Goblin,Cow,Rat,", AIOFighterPlugin.appendNpcToList("Goblin,Cow", "Rat"));
    }

    @Test
    void keepsWellFormedListFormat() {
        assertEquals("Goblin,Cow,Rat,", AIOFighterPlugin.appendNpcToList("Goblin,Cow,", "Rat"));
    }

    @Test
    void trimsEntriesWrittenWithSpaces() {
        assertEquals("Goblin,Cow,Rat,", AIOFighterPlugin.appendNpcToList("Goblin, Cow", "Rat"));
    }

    @Test
    void startsListFromEmptyOrUnsetValue() {
        assertEquals("Rat,", AIOFighterPlugin.appendNpcToList("", "Rat"));
        assertEquals("Rat,", AIOFighterPlugin.appendNpcToList(null, "Rat"));
    }
}
