package net.runelite.client.plugins.microbot.magetrainingarena;

import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MageTrainingArenaScriptTest {

    private static Widget item(int itemId) {
        Widget widget = mock(Widget.class);
        when(widget.getItemId()).thenReturn(itemId);
        return widget;
    }

    @Test
    void unloadedRewardListHasNoReward() {
        assertEquals(-1, MageTrainingArenaScript.findRewardIndex(null, 6908));
    }

    @Test
    void missingRewardIsNotClicked() {
        Widget[] widgets = {item(6908), item(6910)};
        assertEquals(-1, MageTrainingArenaScript.findRewardIndex(widgets, 6912));
    }

    @Test
    void matchingRewardReturnsItsIndex() {
        Widget[] widgets = {item(6908), null, item(6910)};
        assertEquals(2, MageTrainingArenaScript.findRewardIndex(widgets, 6910));
    }
}
