package net.runelite.client.plugins.microbot.agility;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.agility.enums.AgilityFoodOption;
import net.runelite.client.plugins.microbot.agility.enums.AgilityPotionOption;

@ConfigGroup("MicroAgility")
@ConfigInformation("Enable the plugin near the start of your selected agility course. <br />" +
	"<b>Course requirements:</b>" +
	"<ul>" +
	"<li> Ape Atoll - Kruk or Ninja greegree equipped. Stamina pots recommended. </li>" +
	"<li>Shayzien Advanced - Crossbow and Mith Grapple equipped.</li>" +
	"</ul>")
public interface MicroAgilityConfig extends Config
{

	String selectedCourse = "course";
	String hitpointsThreshold = "hitpointsThreshold";
	String shouldAlch = "shouldAlch";
	String itemsToAlch = "itemsToAlch";

	@ConfigSection(
		name = "General",
		description = "General",
		position = 0,
		closedByDefault = false
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Banking",
		description = "Banking",
		position = 1,
		closedByDefault = true
	)
	String bankingSection = "banking";

	@ConfigSection(
		name = "Safety",
		description = "Safety",
		position = 2,
		closedByDefault = true
	)
	String safetySection = "safety";

	@ConfigItem(
		keyName = selectedCourse,
		name = "Course",
		description = "Choose your agility course",
		position = 1,
		section = generalSection
	)
	default AgilityCourse agilityCourse()
	{
		return AgilityCourse.CANIFIS_ROOFTOP_COURSE;
	}

	@ConfigItem(
		keyName = hitpointsThreshold,
		name = "Eat at",
		description = "Use food below certain hitpoint percent. If there's no food in the inventory, the script stops. Set to 0 in order to disable.",
		position = 2,
		section = generalSection
	)
	default int hitpoints()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "bankFood",
		name = "Food",
		description = "Food to withdraw when banking. None disables food banking. Auto withdraws the best available configured food.",
		position = 1,
		section = bankingSection
	)
	default AgilityFoodOption bankFood()
	{
		return AgilityFoodOption.NONE;
	}

	@ConfigItem(
		keyName = "foodWithdrawAmount",
		name = "Food amount",
		description = "Target amount of food to have after banking.",
		position = 2,
		section = bankingSection
	)
	@Range(min = 0, max = 28)
	default int foodWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "bankFoodAt",
		name = "Bank under food",
		description = "Bank when inventory food count is at or below this amount. 0 disables food banking triggers.",
		position = 3,
		section = bankingSection
	)
	@Range(min = 0, max = 28)
	default int bankFoodAt()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "bankPotion",
		name = "Potion",
		description = "Potion to withdraw when banking. None disables potion banking.",
		position = 4,
		section = bankingSection
	)
	default AgilityPotionOption bankPotion()
	{
		return AgilityPotionOption.NONE;
	}

	@ConfigItem(
		keyName = "potionWithdrawAmount",
		name = "Potion amount",
		description = "Target amount of selected potion to have after banking.",
		position = 5,
		section = bankingSection
	)
	@Range(min = 0, max = 28)
	default int potionWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "bankOnlyAtCourseStart",
		name = "Bank at course start",
		description = "Only bank from the ground near the course start. This avoids asking the walker to route from rooftops or awkward course positions.",
		position = 6,
		section = bankingSection
	)
	default boolean bankOnlyAtCourseStart()
	{
		return true;
	}

	@ConfigItem(
		keyName = "summerPieWithdrawAmount",
		name = "Summer pie amount",
		description = "Target amount of summer pies to withdraw when banking. 0 disables summer pie banking.",
		position = 7,
		section = bankingSection
	)
	@Range(min = 0, max = 28)
	default int summerPieWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "hpSafetyWait",
		name = "Wait when unsafe",
		description = "If out of food and HP is too low, wait instead of stopping or continuing.",
		position = 1,
		section = safetySection
	)
	default boolean hpSafetyWait()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hpSafetyWaitBelow",
		name = "Wait below HP",
		description = "When out of food, wait if HP percentage is below this value.",
		position = 2,
		section = safetySection
	)
	@Range(min = 1, max = 99)
	default int hpSafetyWaitBelow()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "hpSafetyResumeAt",
		name = "Resume at HP",
		description = "Resume once HP percentage is at or above this value.",
		position = 3,
		section = safetySection
	)
	@Range(min = 1, max = 100)
	default int hpSafetyResumeAt()
	{
		return 50;
	}

	@ConfigItem(
		keyName = shouldAlch,
		name = "Alch",
		description = "Use Low/High Alchemy while doing agility",
		position = 4,
		section = generalSection
	)
	default boolean alchemy()
	{
		return false;
	}

	@ConfigItem(
		keyName = itemsToAlch,
		name = "Items to Alch",
		description = "Enter items to alch, separated by commas (e.g., Rune sword, Dragon dagger, Mithril platebody)",
		position = 5,
		section = generalSection
	)
	default String itemsToAlch()
	{
		return "";
	}

	@ConfigItem(
		keyName = "efficientAlching",
		name = "Efficient Alching",
		description = "Click obstacle first, then alch, then click again (for obstacles 5+ tiles away)",
		position = 6,
		section = generalSection
	)
	default boolean efficientAlching()
	{
		return false;
	}

	@ConfigItem(
		keyName = "skipInefficient",
		name = "Skip Inefficient",
		description = "Only alch when obstacle is 5+ tiles away (skip inefficient alchs)",
		position = 7,
		section = generalSection
	)
	default boolean skipInefficient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alchSkipChance",
		name = "Alch Skip Chance",
		description = "Percentage chance to skip alching on any obstacle (0-100)",
		position = 8,
		section = generalSection
	)
	@Range(min = 0, max = 100)
	default int alchSkipChance()
	{
		return 5;
	}
}
