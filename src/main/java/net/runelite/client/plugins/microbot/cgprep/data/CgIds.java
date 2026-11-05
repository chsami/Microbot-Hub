package net.runelite.client.plugins.microbot.cgprep.data;

import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;

/**
 * Mode-independent Gauntlet IDs. Every constant here comes from the client's own gameval tables
 * (verified against microbot 2.6.43), so these are not guesses. Mode-specific IDs (corrupted vs
 * crystal) live in {@link CgMode}.
 *
 * Anything marked TODO(recon) is a behavioural assumption that the CG Recon log must confirm.
 */
public final class CgIds
{
	private CgIds()
	{
	}

	// ------------------------------------------------------------------ varbits (state, no scraping)

	/** 1 while the player is inside a Gauntlet instance (either mode). TODO(recon): confirm values. */
	public static final int VB_PLAYER_IN_GAUNTLET = VarbitID.PLAYER_IN_GAUNTLET;
	/** Set once the Hunllef fight has started (player entered the boss room / was teleported). */
	public static final int VB_BOSS_STARTED = VarbitID.GAUNTLET_BOSS_STARTED;
	/** 1 when the current/selected run is Corrupted. */
	public static final int VB_CORRUPTED = VarbitID.GAUNTLET_CORRUPTED;
	/** Recon: = start-room index (z*7+x) for the run, 0 outside. Not a timer. */
	public static final int VB_START_ROOM_INDEX = VarbitID.GAUNTLET_START;
	/** A reward is waiting in the lobby chest. */
	public static final int VB_REWARD_AVAILABLE = VarbitID.GAUNTLET_REWARD_AVAILABLE;
	/**
	 * Recon-confirmed: despite the gameval names, these hold the START room (set once on entry,
	 * never change while you move). The current room is derived from raw coords via RoomGrid.
	 */
	public static final int VB_START_ROOM_X = VarbitID.GAUNTLET_CURRENT_ROOM_X;
	public static final int VB_START_ROOM_Z = VarbitID.GAUNTLET_CURRENT_ROOM_Z;
	/** GAUNTLET_ROOM_0_FOUND .. GAUNTLET_ROOM_48_FOUND are contiguous (9240..9288). */
	public static final int VB_ROOM_0_FOUND = VarbitID.GAUNTLET_ROOM_0_FOUND;
	public static final int ROOM_GRID = 7;
	/**
	 * The BOSS room is the centre of the 7x7 grid (wiki). The start room is adjacent to it; which
	 * side isn't fixed in our data, so GauntletMaze records the player's room at run start instead.
	 */
	public static final int BOSS_ROOM_X = 3;
	public static final int BOSS_ROOM_Z = 3;
	/** One vial costs 10 shards at the singing bowl ("Sing-crystal"). */
	public static final int VIAL_SHARD_COST = 10;
	/** Pestle on the shard stack crushes exactly 10 shards -> 10 dust = one potion (recon-confirmed). */
	public static final int DUST_PER_POTION = 10;
	/** Tiles per room side (recon-confirmed). */
	public static final int ROOM_SIZE_TILES = 16;

	/** Varbit id for "room n found"; n = z * 7 + x (recon-confirmed; flips when a Node is lit). */
	public static int roomFoundVarbit(int roomX, int roomZ)
	{
		return VB_ROOM_0_FOUND + roomZ * ROOM_GRID + roomX;
	}

	// ------------------------------------------------------------------ interfaces

	/** Prep-timer overlay. TIMER_TEXT holds the "m:ss" countdown. */
	public static final int IF_OVERLAY_TIMER_TEXT = InterfaceID.GauntletOverlay.TIMER_TEXT;
	public static final int IF_MAP_GROUP = InterfaceID.GAUNTLET_MAP;
	/** The recipe BOOK (object GAUNTLET_BOOK), not the bowl's make menu. */
	public static final int IF_RECIPE_BOOK_GROUP = InterfaceID.GAUNTLET_RECIPES;
	// TODO(recon): the singing bowl make-menu group. Expected: the standard skill-multi chatbox
	// interface, in which case Rs2Widget.handleProcessingInterface(itemName) handles it.

	// ------------------------------------------------------------------ shared items (both modes)

	public static final int RAW_PADDLEFISH = ItemID.GAUNTLET_RAW_FOOD;
	public static final int PADDLEFISH = ItemID.GAUNTLET_FOOD;
	public static final int BURNT_PADDLEFISH = ItemID.GAUNTLET_BURNT_FOOD;
	public static final int CORRUPTED_PADDLEFISH = ItemID.GAUNTLET_COMBO_FOOD_HM;
	public static final int PESTLE = ItemID.GAUNTLET_PESTLE;
	public static final int VIAL_WATER = ItemID.GAUNTLET_VIAL_WATER;
	/** Also seen as an empty vial in Corrupted (by-product), counted alongside the shared vial. */
	public static final int VIAL_EMPTY_ALT = ItemID.GAUNTLET_VIAL_EMPTY_HM;
	public static final int POTION_UNF = ItemID.GAUNTLET_POTION_UNFINISHED;
	public static final int EGNIOL_1 = ItemID.GAUNTLET_POTION_1;
	public static final int EGNIOL_2 = ItemID.GAUNTLET_POTION_2;
	public static final int EGNIOL_3 = ItemID.GAUNTLET_POTION_3;
	public static final int EGNIOL_4 = ItemID.GAUNTLET_POTION_4;
	public static final int[] EGNIOL_ALL = {EGNIOL_4, EGNIOL_3, EGNIOL_2, EGNIOL_1};

	// ------------------------------------------------------------------ boss (both modes listed)

	public static final Set<Integer> HUNLLEF_IDS = Set.of(
		NpcID.CRYSTAL_HUNLLEF_MELEE, NpcID.CRYSTAL_HUNLLEF_RANGED, NpcID.CRYSTAL_HUNLLEF_MAGIC, NpcID.CRYSTAL_HUNLLEF_DEATH,
		NpcID.CRYSTAL_HUNLLEF_MELEE_HM, NpcID.CRYSTAL_HUNLLEF_RANGED_HM, NpcID.CRYSTAL_HUNLLEF_MAGIC_HM, NpcID.CRYSTAL_HUNLLEF_DEATH_HM);
	/**
	 * Recon-confirmed: the NPC id encodes the boss's current overhead and NpcChanged fires on every
	 * switch (RANGED id = overhead sprite 1 / Protect from Missiles, MAGIC id = sprite 2). The MELEE
	 * id is only seen before the fight starts, DEATH on death.
	 */
	public static final Set<Integer> HUNLLEF_PROTECT_MELEE = Set.of(NpcID.CRYSTAL_HUNLLEF_MELEE, NpcID.CRYSTAL_HUNLLEF_MELEE_HM);
	public static final Set<Integer> HUNLLEF_PROTECT_RANGED = Set.of(NpcID.CRYSTAL_HUNLLEF_RANGED, NpcID.CRYSTAL_HUNLLEF_RANGED_HM);
	public static final Set<Integer> HUNLLEF_PROTECT_MAGIC = Set.of(NpcID.CRYSTAL_HUNLLEF_MAGIC, NpcID.CRYSTAL_HUNLLEF_MAGIC_HM);
	public static final Set<Integer> TORNADO_IDS = Set.of(NpcID.CRYSTAL_HUNLLEF_CRYSTALS, NpcID.CRYSTAL_HUNLLEF_CRYSTALS_HM);

	public static final int ANIM_HUNLLEF_SWITCH_TO_MAGIC = AnimationID.HUNLLEF_ATTACK_TRANSITION_MAGIC;
	public static final int ANIM_HUNLLEF_SWITCH_TO_RANGED = AnimationID.HUNLLEF_ATTACK_TRANSITION_RANGED;
	public static final int ANIM_HUNLLEF_SWITCH_TO_MAGIC_LOW = AnimationID.HUNLLEF_ATTACK_TRANSITION_MAGIC_LOWPRIO;
	public static final int ANIM_HUNLLEF_SWITCH_TO_RANGED_LOW = AnimationID.HUNLLEF_ATTACK_TRANSITION_RANGED_LOWPRIO;
	public static final int ANIM_HUNLLEF_ATTACK_RANGED = AnimationID.HUNLLEF_ATTACK_RANGED;
	public static final int ANIM_HUNLLEF_ATTACK_MELEE = AnimationID.HUNLLEF_ATTACK_MELEE;
	/** Tornado summon. Counts as one of the 4 attacks per style; tornadoes spawn 3 ticks later. */
	public static final int ANIM_HUNLLEF_SPECIAL = AnimationID.HUNLLEF_ATTACK_SPECIAL;

	public static final Set<Integer> PROJ_HUNLLEF_MAGIC = Set.of(SpotanimID.CRYSTAL_HUNLLEF_MAGIC_TRAVEL, SpotanimID.CRYSTAL_HUNLLEF_MAGIC_TRAVEL_HM);
	public static final Set<Integer> PROJ_HUNLLEF_RANGED = Set.of(SpotanimID.CRYSTAL_HUNLLEF_RANGE_TRAVEL, SpotanimID.CRYSTAL_HUNLLEF_RANGE_TRAVEL_HM);
	/** The prayer-disabling orb. */
	public static final Set<Integer> PROJ_HUNLLEF_PRAYER_DISABLE = Set.of(SpotanimID.CRYSTAL_HUNLLEF_PRAYER_TRAVEL, SpotanimID.CRYSTAL_HUNLLEF_PRAYER_TRAVEL_HM);

	/** Damaging floor: WARNING = about to hurt, HIT = hurting now. GROUND objects (recon-confirmed). */
	public static final Set<Integer> FLOOR_WARNING = Set.of(ObjectID.PRIF_GAUNTLET_FLOOR_TILE_01_WARNING, ObjectID.PRIF_GAUNTLET_FLOOR_TILE_01_WARNING_HM);
	public static final Set<Integer> FLOOR_HIT = Set.of(ObjectID.PRIF_GAUNTLET_FLOOR_TILE_01_HIT, ObjectID.PRIF_GAUNTLET_FLOOR_TILE_01_HIT_HM);

	// ------------------------------------------------------------------ room nodes, lobby, blacklist

	/** "Node" objects next to doorways; option "Light" opens the adjacent room (recon-confirmed ids). */
	public static final Set<Integer> NODE_UNLIT = Set.of(
		ObjectID.PRIF_GAUNTLET_DOOR_WALL_UNLIT_01, ObjectID.PRIF_GAUNTLET_DOOR_WALL_UNLIT_02,
		ObjectID.PRIF_GAUNTLET_DOOR_WALL_UNLIT_01_HM, ObjectID.PRIF_GAUNTLET_DOOR_WALL_UNLIT_02_HM);
	public static final String NODE_OPTION = "Light";

	/** Teleport Platform ("Exit") abandons the run. NEVER interact with these. */
	public static final Set<Integer> NEVER_CLICK = Set.of(ObjectID.GAUNTLET_EXIT, ObjectID.GAUNTLET_EXIT_HM);

	/** Lobby entrance: "Enter" (normal) / "Enter-corrupted" (Corrupted) - see CgMode.entranceOption(). */
	public static final int OBJ_LOBBY_ENTRANCE = ObjectID.GAUNTLET_ENTRANCE;
	public static final int OBJ_LOBBY_DEPOSIT_BOX = ObjectID.GAUNTLET_DEPOSIT_BOX;
	/** Reward Chest (recon: 37341 "Open" while a reward waits, after normal and Corrupted runs alike). */
	public static final int OBJ_LOBBY_CHEST = ObjectID.GAUNTLET_CHEST;
	public static final int OBJ_LOBBY_CHEST_HM = ObjectID.GAUNTLET_CHEST_HM;
	public static final String BARRIER_OPTION = "Quick-pass";
	public static final String CHEST_OPTION = "Open";

	// ------------------------------------------------------------------ demi-boss projectiles (prep fights)

	public static final int PROJ_DRAGON_MAGIC_HM = SpotanimID.CRYSTAL_DRAGON_MAGIC_TRAVEL_HM;
	public static final int PROJ_DARK_BEAST_RANGED_HM = SpotanimID.CRYSTAL_DARK_BEAST_RANGE_TRAVEL_HM;

	// ------------------------------------------------------------------ skilling animations (corrupted tools)

	public static final int ANIM_MINING_HM = AnimationID.HUMAN_MINING_GAUNTLET_PICKAXE_HM;
	public static final int ANIM_WOODCUTTING_HM = AnimationID.HUMAN_WOODCUTTING_GAUNTLET_AXE_HM;
	public static final int ANIM_FISHING_HM = AnimationID.HUMAN_HARPOON_GAUNTLET_HM;
}
