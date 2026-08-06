package net.runelite.client.plugins.microbot.agility.enums;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@Getter
public enum AgilityFoodOption
{
	NONE("None", null, false),
	AUTO("Auto (best available)", null, true),
	DARK_CRAB(Rs2Food.Dark_Crab),
	ROCKTAIL(Rs2Food.ROCKTAIL),
	MANTA_RAY(Rs2Food.MANTA),
	SEA_TURTLE(Rs2Food.SEA_TURTLE),
	TUNA_POTATO(Rs2Food.TUNA_POTATO),
	SHARK(Rs2Food.SHARK),
	COOKED_MOONLIGHT_ANTELOPE(Rs2Food.COOKED_MOONLIGHT_ANTELOPE),
	COOKED_DASHING_KEBBIT(Rs2Food.COOKED_DASHING_KEBBIT),
	COOKED_SUNLIGHT_ANTELOPE(Rs2Food.COOKED_SUNLIGHT_ANTELOPE),
	MONKFISH(Rs2Food.MONKFISH),
	SWORDFISH(Rs2Food.SWORDFISH),
	LOBSTER(Rs2Food.LOBSTER),
	JUG_OF_WINE(Rs2Food.JUG_OF_WINE),
	TUNA(Rs2Food.TUNA),
	SALMON(Rs2Food.SALMON),
	TROUT(Rs2Food.TROUT),
	CAKE(Rs2Food.CAKE);

	private final String displayName;
	private final Rs2Food food;
	private final boolean auto;

	AgilityFoodOption(Rs2Food food)
	{
		this(food.getName(), food, false);
	}

	AgilityFoodOption(String displayName, Rs2Food food, boolean auto)
	{
		this.displayName = displayName;
		this.food = food;
		this.auto = auto;
	}

	public boolean isNone()
	{
		return this == NONE;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
