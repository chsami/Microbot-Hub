package net.runelite.client.plugins.microbot.actionreplay.model;

import net.runelite.api.MenuAction;

public enum TargetType
{
	NPC,
	GAME_OBJECT,
	GROUND_ITEM,
	PLAYER,
	WIDGET,
	WALK,
	UNKNOWN;

	public static TargetType fromMenuAction(MenuAction action)
	{
		if (action == null)
		{
			return UNKNOWN;
		}
		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case EXAMINE_NPC:
				return NPC;
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case EXAMINE_OBJECT:
				return GAME_OBJECT;
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
				return GROUND_ITEM;
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				return PLAYER;
			case WALK:
				return WALK;
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			case WIDGET_TYPE_1:
			case WIDGET_TYPE_4:
			case WIDGET_TYPE_5:
			case WIDGET_TARGET:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_PLAYER:
			case WIDGET_TARGET_ON_WIDGET:
				return WIDGET;
			default:
				return UNKNOWN;
		}
	}
}
