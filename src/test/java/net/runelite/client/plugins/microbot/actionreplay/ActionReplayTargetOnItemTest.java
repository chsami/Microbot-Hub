package net.runelite.client.plugins.microbot.actionreplay;

import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.actionreplay.model.RecordedAction;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionReplayTargetOnItemTest
{
	private static final int INVENTORY_CONTAINER = 9764864;
	private static final int ONYX_BOLTS_E = 9245;

	private static RecordedAction highAlchOnBolts()
	{
		RecordedAction a = new RecordedAction();
		a.setMenuOption("Cast");
		a.setTargetName("High Level Alchemy -> Onyx bolts (e)");
		a.setMenuAction(MenuAction.WIDGET_TARGET_ON_WIDGET.name());
		a.setIdentifier(0);
		a.setParam0(3);
		a.setParam1(INVENTORY_CONTAINER);
		a.setItemId(ONYX_BOLTS_E);
		return a;
	}

	@Test
	void spellCastOnInventoryItemIsTargetStep()
	{
		assertTrue(ActionReplayScript.isTargetOnInventoryItem(highAlchOnBolts(), MenuAction.WIDGET_TARGET_ON_WIDGET));
	}

	@Test
	void plainInventoryOptionIsNotTargetStep()
	{
		RecordedAction wield = new RecordedAction();
		wield.setMenuOption("Wield");
		wield.setTargetName("Staff of fire");
		wield.setMenuAction(MenuAction.CC_OP.name());
		wield.setIdentifier(3);
		wield.setParam0(5);
		wield.setParam1(INVENTORY_CONTAINER);
		wield.setItemId(1387);

		assertFalse(ActionReplayScript.isTargetOnInventoryItem(wield, MenuAction.CC_OP));
	}

	@Test
	void spellbookSelectionIsNotTargetStep()
	{
		RecordedAction select = new RecordedAction();
		select.setMenuOption("Cast");
		select.setTargetName("High Level Alchemy");
		select.setMenuAction(MenuAction.WIDGET_TARGET.name());
		select.setParam0(-1);
		select.setParam1(14286895);

		assertFalse(ActionReplayScript.isTargetOnInventoryItem(select, MenuAction.WIDGET_TARGET));
	}

	@Test
	void targetStepEntryUsesCurrentSlotAndKeepsRecordedEntry()
	{
		NewMenuEntry entry = ActionReplayScript.buildMenuEntry(highAlchOnBolts(), MenuAction.WIDGET_TARGET_ON_WIDGET, 17);

		assertEquals(17, entry.getParam0());
		assertEquals(INVENTORY_CONTAINER, entry.getParam1());
		assertEquals(MenuAction.WIDGET_TARGET_ON_WIDGET, entry.getType());
		assertEquals("Cast", entry.getOption());
		assertEquals("High Level Alchemy -> Onyx bolts (e)", entry.getTarget());
		assertEquals(0, entry.getIdentifier());
		assertEquals(ONYX_BOLTS_E, entry.getItemId());
	}
}
