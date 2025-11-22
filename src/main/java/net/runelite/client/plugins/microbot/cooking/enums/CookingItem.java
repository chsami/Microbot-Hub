package net.runelite.client.plugins.microbot.cooking.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum CookingItem
{
	// Meat / fish
	RAW_BEEF("raw beef", ItemID.RAW_BEEF, 1, "cooked meat", ItemID.COOKED_MEAT, "burnt meat", ItemID.BURNT_MEAT, CookingAreaType.BOTH),
	GIANT_SEAWEED("giant seaweed", ItemID.GIANT_SEAWEED, 1, "soda ash", ItemID.SODA_ASH, "none", 0, CookingAreaType.BOTH),
	RAW_SHRIMP("raw shrimps", ItemID.RAW_SHRIMP, 1, "shrimps", ItemID.SHRIMP, "burnt shrimp", ItemID.BURNT_SHRIMP, CookingAreaType.BOTH),
	RAW_CHICKEN("raw chicken", ItemID.RAW_CHICKEN, 1, "chicken", ItemID.COOKED_CHICKEN, "burnt chicken", ItemID.BURNT_CHICKEN, CookingAreaType.BOTH),
	RAW_RABBIT("raw rabbit", ItemID.RAW_RABBIT, 1, "rabbit", ItemID.COOKED_RABBIT, "burnt meat", ItemID.BURNT_MEAT, CookingAreaType.BOTH),
	RAW_ANCHOVIES("raw anchovies", ItemID.RAW_ANCHOVIES, 1, "anchovies", ItemID.ANCHOVIES, "burnt fish", ItemID.BURNTFISH1, CookingAreaType.BOTH),
	RAW_SARDINE("raw sardine", ItemID.RAW_SARDINE, 1, "sardine", ItemID.SARDINE, "burnt fish", ItemID.BURNTFISH5, CookingAreaType.BOTH),
	RAW_HERRING("raw herring", ItemID.RAW_HERRING, 5, "herring", ItemID.HERRING, "burnt fish", ItemID.BURNTFISH3, CookingAreaType.BOTH),
	RAW_MACKEREL("raw mackerel", ItemID.RAW_MACKEREL, 10, "mackerel", ItemID.MACKEREL, "burnt fish", ItemID.BURNTFISH3, CookingAreaType.BOTH),
	RAW_TROUT("raw trout", ItemID.RAW_TROUT, 15, "trout", ItemID.TROUT, "burnt fish", ItemID.BURNTFISH2, CookingAreaType.BOTH),
	RAW_COD("raw cod", ItemID.RAW_COD, 18, "cod", ItemID.COD, "burnt fish", ItemID.BURNTFISH2, CookingAreaType.BOTH),
	RAW_PIKE("raw pike", ItemID.RAW_PIKE, 20, "pike", ItemID.PIKE, "burnt fish", ItemID.BURNTFISH2, CookingAreaType.BOTH),
	RAW_SALMON("raw salmon", ItemID.RAW_SALMON, 25, "salmon", ItemID.SALMON, "burnt fish", ItemID.BURNTFISH2, CookingAreaType.BOTH),
	RAW_TUNA("raw tuna", ItemID.RAW_TUNA, 30, "tuna", ItemID.TUNA, "burnt fish", ItemID.BURNTFISH4, CookingAreaType.BOTH),
	RAW_FISHCAKE("raw fishcake", ItemID.HUNDRED_PIRATE_RAW_FISHCAKE, 31, "cooked fishcake", ItemID.HUNDRED_PIRATE_FISHCAKE, "burnt fishcake", ItemID.HUNDRED_PIRATE_BURNED_FISHCAKE, CookingAreaType.BOTH),
	RAW_KARAMBWAN("raw karambwan", ItemID.TBWT_RAW_KARAMBWAN, 30, "cooked karambwan", ItemID.TBWT_COOKED_KARAMBWAN, "burnt karambwan", ItemID.TBWT_BURNT_KARAMBWAN, CookingAreaType.BOTH),
	RAW_LOBSTER("raw lobster", ItemID.RAW_LOBSTER, 40, "lobster", ItemID.LOBSTER, "burnt lobster", ItemID.BURNT_LOBSTER, CookingAreaType.BOTH),
	RAW_BASS("raw bass", ItemID.RAW_BASS, 43, "bass", ItemID.BASS, "burnt fish", ItemID.BURNTFISH4, CookingAreaType.BOTH),
	RAW_SWORDFISH("raw swordfish", ItemID.RAW_SWORDFISH, 45, "swordfish", ItemID.SWORDFISH, "burnt swordfish", ItemID.BURNT_SWORDFISH, CookingAreaType.BOTH),
	RAW_MONKFISH("raw monkfish", ItemID.RAW_MONKFISH, 62, "monkfish", ItemID.MONKFISH, "burnt monkfish", ItemID.BURNT_MONKFISH, CookingAreaType.BOTH),
	RAW_SUNLIGHT_ANTELOPE("raw sunlight antelope", ItemID.HUNTING_ANTELOPESUN_MEAT, 68, "cooked sunlight antelope", ItemID.ANTELOPESUN_COOKED, "burnt antelope", ItemID.BURNT_ANTELOPE, CookingAreaType.BOTH),
	RAW_SHARK("raw shark", ItemID.RAW_SHARK, 80, "shark", ItemID.SHARK, "burnt shark", ItemID.BURNT_SHARK, CookingAreaType.BOTH),
	RAW_SEA_TURTLE("raw sea turtle", ItemID.RAW_SEATURTLE, 82, "sea turtle", ItemID.SEATURTLE, "burnt sea turtle", ItemID.BURNT_SEATURTLE, CookingAreaType.BOTH),
	RAW_ANGLERFISH("raw anglerfish", ItemID.RAW_ANGLERFISH, 84, "anglerfish", ItemID.ANGLERFISH, "burnt anglerfish", ItemID.BURNT_ANGLERFISH, CookingAreaType.BOTH),
	RAW_DARK_CRAB("raw dark crab", ItemID.RAW_DARK_CRAB, 90, "dark crab", ItemID.DARK_CRAB, "burnt dark crab", ItemID.BURNT_DARK_CRAB, CookingAreaType.BOTH),
	RAW_MANTA_RAY("raw manta ray", ItemID.RAW_MANTARAY, 91, "manta ray", ItemID.MANTARAY, "burnt manta ray", ItemID.BURNT_MANTARAY, CookingAreaType.BOTH),
	RAW_MOONLIGHT_ANTELOPE("raw moonlight antelope", ItemID.HUNTING_ANTELOPEMOON_MEAT, 92, "cooked moonlight antelope", ItemID.ANTELOPEMOON_COOKED, "burnt antelope", ItemID.BURNT_ANTELOPE, CookingAreaType.BOTH),
	// Bread
	BREAD_DOUGH("bread dough", ItemID.BREAD_DOUGH, 1, "bread", ItemID.BREAD, "burnt bread", ItemID.BURNT_BREAD, CookingAreaType.RANGE),
	PITTA_DOUGH("pitta dough", ItemID.UNCOOKED_PITTA_BREAD, 58, "pitta bread", ItemID.PITTA_BREAD, "burnt pitta bread", ItemID.BURNT_PITTA_BREAD, CookingAreaType.RANGE),
	// Pies
	UNCOOKED_BERRY_PIE("uncooked berry pie", ItemID.UNCOOKED_REDBERRY_PIE, 10, "redberry pie", ItemID.REDBERRY_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	UNCOOKED_MEAT_PIE("uncooked meat pie", ItemID.UNCOOKED_MEAT_PIE, 20, "meat pie", ItemID.MEAT_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_MUD_PIE("raw mud pie", ItemID.UNCOOKED_MUD_PIE, 29, "mud pie", ItemID.MUD_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	UNCOOKED_APPLE_PIE("uncooked apple pie", ItemID.UNCOOKED_APPLE_PIE, 30, "apple pie", ItemID.APPLE_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_GARDEN_PIE("raw garden pie", ItemID.UNCOOKED_GARDEN_PIE, 34, "garden pie", ItemID.GARDEN_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_FISH_PIE("raw fish pie", ItemID.UNCOOKED_FISH_PIE, 47, "fish pie", ItemID.FISH_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	UNCOOKED_BOTANICAL_PIE("uncooked batanical pie", ItemID.UNCOOKED_BOTANICAL_PIE, 52, "botanical pie", ItemID.BOTANICAL_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	UNCOOKED_MUSHROOM_PIE("uncooked mushroom pie", ItemID.UNCOOKED_MUSHROOM_PIE, 60, "mushroom pie", ItemID.MUSHROOM_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_ADMIRAL_PIE("raw admiral pie", ItemID.UNCOOKED_ADMIRAL_PIE, 70, "admiral pie", ItemID.ADMIRAL_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	UNCOOKED_DRAGONFRUIT_PIE("uncooked dragonfruit pie", ItemID.UNCOOKED_DRAGONFRUIT_PIE, 73, "dragonfruit pie", ItemID.DRAGONFRUIT_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_WILD_PIE("raw wild pie", ItemID.UNCOOKED_WILD_PIE, 85, "wild pie", ItemID.WILD_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	RAW_SUMMER_PIE("raw summer pie", ItemID.UNCOOKED_SUMMER_PIE, 95, "summer pie", ItemID.SUMMER_PIE, "burnt pie", ItemID.BURNT_PIE, CookingAreaType.RANGE),
	// Stews
	UNCOOKED_STEW("uncooked stew", ItemID.UNCOOKED_STEW, 25, "stew", ItemID.STEW, "burnt stew", ItemID.BURNT_STEW, CookingAreaType.BOTH),
	UNCOOKED_CURRY("uncooked curry", ItemID.UNCOOKED_CURRY, 60, "curry", ItemID.CURRY, "burnt curry", ItemID.BURNT_CURRY, CookingAreaType.BOTH),
	// Pizzas
	UNCOOKED_PIZZA("uncooked pizza", ItemID.UNCOOKED_PIZZA, 35, "plain pizza", ItemID.PLAIN_PIZZA, "burnt pizza", ItemID.BURNT_PIZZA, CookingAreaType.RANGE),
	// Cakes
	UNCOOKED_CAKE("uncooked cake", ItemID.UNCOOKED_CAKE, 40, "cake", ItemID.CAKE, "burnt cake", ItemID.BURNT_CAKE, CookingAreaType.RANGE),
	// Vegetable
	POTATO("potato", ItemID.POTATO, 7, "baked potato", ItemID.POTATO_BAKED, "burnt potato", ItemID.POTATO_BURNT, CookingAreaType.RANGE),
	UNCOOKED_EGG("uncooked egg", ItemID.SCRAMBLED_EGG, 13, "scrambled egg", ItemID.SCRAMBLED_EGG, "burnt egg", ItemID.BOWL_EGG_BURNT, CookingAreaType.BOTH),
	SWEETCORN("sweetcorn", ItemID.SWEETCORN, 28, "cooked sweetcorn", ItemID.SWEETCORN_COOKED, "burnt sweetcorn", ItemID.SWEETCORN_BURNT, CookingAreaType.BOTH),
	;

	private final String rawItemName;
	private final int rawItemID;
	private final int levelRequired;
	private final String cookedItemName;
	private final int cookedItemID;
	private final String burntItemName;
	private final int burntItemID;
	private final CookingAreaType cookingAreaType;

	private boolean hasLevelRequired()
	{
		return Rs2Player.getSkillRequirement(Skill.COOKING, this.getLevelRequired());
	}

	public boolean hasRequirements()
	{
		switch (this)
		{
			case RAW_RABBIT:
			case RAW_MACKEREL:
			case RAW_COD:
			case RAW_KARAMBWAN:
			case RAW_FISHCAKE:
			case RAW_BASS:
			case RAW_MONKFISH:
			case RAW_SUNLIGHT_ANTELOPE:
			case RAW_SHARK:
			case RAW_SEA_TURTLE:
			case RAW_ANGLERFISH:
			case RAW_DARK_CRAB:
			case RAW_MANTA_RAY:
			case RAW_MOONLIGHT_ANTELOPE:
			case PITTA_DOUGH:
			case UNCOOKED_CURRY:
			case RAW_MUD_PIE:
			case RAW_GARDEN_PIE:
			case RAW_FISH_PIE:
			case UNCOOKED_BOTANICAL_PIE:
			case UNCOOKED_MUSHROOM_PIE:
			case RAW_ADMIRAL_PIE:
			case UNCOOKED_DRAGONFRUIT_PIE:
			case RAW_WILD_PIE:
			case RAW_SUMMER_PIE:
			case POTATO:
			case UNCOOKED_EGG:
			case SWEETCORN:
				return hasLevelRequired() && Rs2Player.isMember();
			default:
				return hasLevelRequired();
		}
	}
}
