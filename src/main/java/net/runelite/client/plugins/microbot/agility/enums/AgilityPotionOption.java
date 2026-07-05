package net.runelite.client.plugins.microbot.agility.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;

@Getter
public enum AgilityPotionOption
{
	NONE("None", false),
	STAMINA("Stamina potion", true,
		ItemID._4DOSESTAMINA,
		ItemID._3DOSESTAMINA,
		ItemID._2DOSESTAMINA,
		ItemID._1DOSESTAMINA),
	EXTENDED_STAMINA("Extended stamina potion", true,
		ItemID._4DOSE2STAMINA,
		ItemID._3DOSE2STAMINA,
		ItemID._2DOSE2STAMINA,
		ItemID._1DOSE2STAMINA),
	SUPER_ENERGY("Super energy potion", false,
		ItemID._4DOSE2ENERGY,
		ItemID._3DOSE2ENERGY,
		ItemID._2DOSE2ENERGY,
		ItemID._1DOSE2ENERGY),
	ENERGY("Energy potion", false,
		ItemID._4DOSE1ENERGY,
		ItemID._3DOSE1ENERGY,
		ItemID._2DOSE1ENERGY,
		ItemID._1DOSE1ENERGY),
	EXTREME_ENERGY("Extreme energy potion", false,
		ItemID._4DOSE3ENERGY,
		ItemID._3DOSE3ENERGY,
		ItemID._2DOSE3ENERGY,
		ItemID._1DOSE3ENERGY),
	STAMINA_MIX("Stamina mix", true,
		ItemID.BRUTAL_2DOSESTAMINA,
		ItemID.BRUTAL_1DOSESTAMINA),
	SUPER_ENERGY_MIX("Super energy mix", false,
		ItemID.BRUTAL_2DOSE2ENERGY,
		ItemID.BRUTAL_1DOSE2ENERGY),
	ENERGY_MIX("Energy mix", false,
		ItemID.BRUTAL_2DOSE1ENERGY,
		ItemID.BRUTAL_1DOSE1ENERGY);

	private final String displayName;
	private final boolean staminaEffect;
	private final int[] itemIdsHighToLow;

	AgilityPotionOption(String displayName, boolean staminaEffect, int... itemIdsHighToLow)
	{
		this.displayName = displayName;
		this.staminaEffect = staminaEffect;
		this.itemIdsHighToLow = itemIdsHighToLow;
	}

	public boolean isNone()
	{
		return this == NONE;
	}

	public boolean matches(int itemId)
	{
		return Arrays.stream(itemIdsHighToLow).anyMatch(id -> id == itemId);
	}

	public int[] getItemIdsLowToHigh()
	{
		int[] ids = Arrays.copyOf(itemIdsHighToLow, itemIdsHighToLow.length);
		for (int left = 0, right = ids.length - 1; left < right; left++, right--)
		{
			int temp = ids[left];
			ids[left] = ids[right];
			ids[right] = temp;
		}
		return ids;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
