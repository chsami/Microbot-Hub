package net.runelite.client.plugins.microbot.actionreplay;

import org.junit.jupiter.api.Test;

import javax.swing.SpinnerNumberModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionReplayPanelDelaySpinnerTest
{
	@Test
	void nullDelayStartsAtZeroWithDefaultMaximum()
	{
		SpinnerNumberModel model = ActionReplayPanel.delaySpinnerModel(null);

		assertEquals(0, model.getValue());
		assertEquals(0, model.getMinimum());
		assertEquals(ActionReplayPanel.MAX_EDIT_DELAY_TICKS, model.getMaximum());
	}

	@Test
	void delayWithinRangeKeepsDefaultMaximum()
	{
		SpinnerNumberModel model = ActionReplayPanel.delaySpinnerModel(42);

		assertEquals(42, model.getValue());
		assertEquals(ActionReplayPanel.MAX_EDIT_DELAY_TICKS, model.getMaximum());
	}

	@Test
	void delayAboveDefaultMaximumRaisesMaximumAndKeepsValue()
	{
		SpinnerNumberModel model = ActionReplayPanel.delaySpinnerModel(4321);

		assertEquals(4321, model.getValue());
		assertEquals(4321, model.getMaximum());
	}

	@Test
	void negativeDelayIsClampedToZero()
	{
		SpinnerNumberModel model = ActionReplayPanel.delaySpinnerModel(-5);

		assertEquals(0, model.getValue());
		assertEquals(ActionReplayPanel.MAX_EDIT_DELAY_TICKS, model.getMaximum());
	}
}
