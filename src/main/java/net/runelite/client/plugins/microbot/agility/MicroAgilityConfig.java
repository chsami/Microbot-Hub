package net.runelite.client.plugins.microbot.agility;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.agility.enums.AgilityEquipmentOption;
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
		name = "Course",
		description = "Choose which agility course to run.",
		position = 0,
		closedByDefault = false
	)
	String courseSection = "courseSection";

	@ConfigSection(
		name = "Alching",
		description = "Optional alching while running the course.",
		position = 1,
		closedByDefault = true
	)
	String alchingSection = "alching";

	@ConfigSection(
		name = "Banking",
		description = "Optional equipment and boosted-course banking.",
		position = 2,
		closedByDefault = true
	)
	String bankingSection = "banking";

	@ConfigSection(
		name = "Food",
		description = "Food, eating, and food banking options.",
		position = 3,
		closedByDefault = true
	)
	String foodSection = "food";

	@ConfigSection(
		name = "Safety",
		description = "Extra protection for low health and empty supplies.",
		position = 4,
		closedByDefault = true
	)
	String safetySection = "safety";

	@ConfigSection(
		name = "Potions",
		description = "Run energy potion banking and drinking options.",
		position = 5,
		closedByDefault = true
	)
	String potionsSection = "potions";

	@ConfigItem(
		keyName = selectedCourse,
		name = "Course",
		description = "Choose the agility course to run.",
		position = 1,
		section = courseSection
	)
	default AgilityCourse agilityCourse()
	{
		return AgilityCourse.CANIFIS_ROOFTOP_COURSE;
	}

	@ConfigItem(
		keyName = hitpointsThreshold,
		name = "Eat at",
		description = "Eat when your HP falls below this percent. Set to 0 to disable auto-eating.",
		position = 1,
		section = foodSection
	)
	default int hitpoints()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "bankFood",
		name = "Food",
		description = "Food to take from the bank. None turns food banking off. Auto picks the best supported food available.",
		position = 2,
		section = foodSection
	)
	default AgilityFoodOption bankFood()
	{
		return AgilityFoodOption.NONE;
	}

	@ConfigItem(
		keyName = "foodWithdrawAmount",
		name = "Food amount",
		description = "How much food to carry after banking.",
		position = 3,
		section = foodSection
	)
	@Range(min = 0, max = 28)
	default int foodWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "bankFoodAt",
		name = "Bank at food",
		description = "Go to the bank when your food count is at or below this number. Set to 0 to disable this trigger.",
		position = 4,
		section = foodSection
	)
	@Range(min = 0, max = 28)
	default int bankFoodAt()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "bankPotion",
		name = "Potion",
		description = "Run energy potion to take from the bank and drink when needed. None turns this off.",
		position = 1,
		section = potionsSection
	)
	default AgilityPotionOption bankPotion()
	{
		return AgilityPotionOption.NONE;
	}

	@ConfigItem(
		keyName = "potionWithdrawAmount",
		name = "Potion amount",
		description = "How many of the selected potion to carry after banking. A 1-dose and 4-dose potion both count as one potion.",
		position = 2,
		section = potionsSection
	)
	@Range(min = 0, max = 28)
	default int potionWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "potionDrinkAt",
		name = "Drink potion at",
		description = "Drink the selected potion when run energy reaches this percent or lower. Set to 0 to disable auto-drinking.",
		position = 3,
		section = potionsSection
	)
	@Range(min = 0, max = 100)
	default int potionDrinkAt()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "bankEquipment",
		name = "Equipment",
		description = "Optional graceful or agility cape setup to withdraw and wear while banking.",
		position = 1,
		section = bankingSection
	)
	default AgilityEquipmentOption bankEquipment()
	{
		return AgilityEquipmentOption.NONE;
	}

	@ConfigItem(
		keyName = "summerPieWithdrawAmount",
		name = "Summer pie amount",
		description = "How many summer pies to carry after banking. Set to 0 if you do not want to bank for pies.",
		position = 2,
		section = bankingSection
	)
	@Range(min = 0, max = 28)
	default int summerPieWithdrawAmount()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "useSummerPies",
		name = "Use summer pies",
		description = "Use summer pies to boost for courses you can reach with a +5 Agility boost. This also lets the script eat another pie if the boost drops on-course.",
		position = 3,
		section = bankingSection
	)
	default boolean useSummerPies()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hpSafetyWait",
		name = "Wait for HP",
		description = "If you have no usable food and HP is low, wait for health to regenerate before continuing.",
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
		description = "Start waiting when you have no usable food and HP falls below this percent.",
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
		description = "Start running the course again once HP reaches this percent.",
		position = 3,
		section = safetySection
	)
	@Range(min = 1, max = 100)
	default int hpSafetyResumeAt()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "logoutWhenOutOfSupplies",
		name = "Logout when supplies run out",
		description = "If configured food or summer pies are gone, stop the script and log out. Course-level mistakes still only stop the script.",
		position = 4,
		section = safetySection
	)
	default boolean logoutWhenOutOfSupplies()
	{
		return false;
	}

	@ConfigItem(
		keyName = shouldAlch,
		name = "Alch",
		description = "Cast alchemy spells while running the course.",
		position = 1,
		section = alchingSection
	)
	default boolean alchemy()
	{
		return false;
	}

	@ConfigItem(
		keyName = itemsToAlch,
		name = "Items to Alch",
		description = "Items to alch, separated by commas. Example: Rune sword, Dragon dagger, Mithril platebody.",
		position = 2,
		section = alchingSection
	)
	default String itemsToAlch()
	{
		return "";
	}

	@ConfigItem(
		keyName = "efficientAlching",
		name = "Efficient Alching",
		description = "For obstacles at least 5 tiles away, click the obstacle, alch, then click the obstacle again.",
		position = 3,
		section = alchingSection
	)
	default boolean efficientAlching()
	{
		return false;
	}

	@ConfigItem(
		keyName = "skipInefficient",
		name = "Skip Inefficient",
		description = "Only alch when the next obstacle is far enough away to avoid slowing the course down.",
		position = 4,
		section = alchingSection
	)
	default boolean skipInefficient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alchSkipChance",
		name = "Alch Skip Chance",
		description = "Chance to skip an alch opportunity. 0 always alchs, 100 never alchs.",
		position = 5,
		section = alchingSection
	)
	@Range(min = 0, max = 100)
	default int alchSkipChance()
	{
		return 5;
	}
}
