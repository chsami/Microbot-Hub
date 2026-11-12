package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Identifiers, tiles and messages for Phosani's Nightmare and the trip to her. */
public final class DreamData
{
    public static final int INITIAL = 9423;
    /** Needed, worn or carried, to summon a thrall. */
    public static final int BOOK_OF_THE_DEAD = 25818;
    public static final Set<Integer> SHIELD = Set.of(9416, 9417, 9418, 11153);
    public static final Set<Integer> TOTEM_PHASE = Set.of(9419, 9420, 9421, 11155);
    public static final int BLAST = 9422;
    public static final int DESPERATION = 11154;
    public static final int DYING = 9424;
    public static final int ASLEEP = 377;
    public static final int BOSS_SIZE = 5;

    public static final int MAGIC_ATTACK = 8595;
    public static final int RANGED_ATTACK = 8596;
    public static final int MELEE_ATTACK = 8594;
    public static final int CLAWS = 8598;
    public static final int CURSE = 8599;
    public static final int SPORES = 8600;
    public static final int FLOWERS = 8601;
    public static final int HUSKS = 8605;
    public static final int PARASITE = 8606;
    public static final int SURGE_LEAVE = 8607;
    /** She reappears this many ticks after she starts to leave. */
    public static final int REAPPEAR_TICKS = 2;
    /** Not seen back this many ticks after she starts to leave, she is no longer waited for. */
    public static final int REAPPEAR_WAIT_TICKS = 4;
    /** Her reappearance, at the start of a surge lane or back at the centre. */
    public static final int SURGE_RETURN = 8609;
    public static final int SURGE_CHARGE = 8597;
    public static final int BLAST_CHARGE = 8610;
    public static final int DEATH = 8612;
    public static final Set<Integer> CLAW_CHAIN = Set.of(9102, 9103, 9104);
    /** Her specials, claws aside: each phase has three, and she uses each once before any again. */
    public static final Set<Integer> SPECIALS = Set.of(CURSE, SPORES, FLOWERS, HUSKS, PARASITE, SURGE_CHARGE);

    /** Her surge crosses this many tiles, from one side of the arena floor to the other. */
    public static final int SURGE_LENGTH = 14;
    /** The arena is longer north to south, so a lane down her centre column crosses two tiles more. */
    public static final int CENTRE_SURGE_LENGTH = 16;

    /** A rift lands this many ticks after it appears. */
    public static final int RIFT_TICKS = 4;
    public static final int RIFT_GRAPHIC = 1767;
    public static final Set<Integer> SPORES_LOCS = Set.of(37738, 37739);
    public static final Set<Integer> SAFE_FLOWERS = Set.of(37743, 37744, 37745);
    public static final Set<Integer> UNSAFE_FLOWERS = Set.of(37740, 37741, 37742);

    public static final int MAGIC_HUSK = 9466;
    public static final int RANGED_HUSK = 9467;
    public static final Set<Integer> HUSK_IDS = Set.of(MAGIC_HUSK, RANGED_HUSK);
    public static final int HUSK_RISE = 8567;
    public static final int RANGED_HUSK_ATTACK = 8564;
    /** Each husk attacks every four ticks, the missile husk first five ticks after rising. */
    public static final int HUSK_ATTACK_TICKS = 4;
    public static final int RANGED_HUSK_FIRST_TICKS = 5;
    public static final int STRONG_PARASITE = 9468;
    public static final int WEAK_PARASITE = 9469;
    /** A parasite rising out of the player. */
    public static final int PARASITE_RISE = 8561;
    /** A risen parasite's attacks on the player; the magic one also heals her or drains a totem. */
    public static final int PARASITE_RANGED = 8554;
    public static final int PARASITE_MAGIC = 8555;
    public static final int SLEEPWALKER = 9470;
    /** A sleepwalker rising beside a totem. */
    public static final int SLEEPWALKER_RISE = 8572;
    /** Sleepwalkers take their first step this many ticks after they rise. */
    public static final int SLEEPWALKER_WALK_TICKS = 4;
    /** In her third phase they stand longer: their first step comes six or seven ticks after they rise. */
    public static final int THIRD_PHASE_WALK_TICKS = 6;
    public static final int THIRD_PHASE_LATE_WALK_TICKS = 7;
    public static final int[][] SLEEPWALKER_SPAWNS = {{-7, -4}, {-4, -7}, {-7, 8}, {-4, 11}, {8, 11}, {11, 8}, {8, -7}, {11, -4}};
    /** A sleepwalker's animation as she absorbs it. */
    public static final int SLEEPWALKER_ABSORBED = 8571;
    /** Each totem's ready form, the only one that takes magic. */
    public static final Set<Integer> READY_TOTEMS = Set.of(9435, 9438, 9441, 9444);
    /** Each totem's charged form: with all four charged, her blast comes next. */
    public static final Set<Integer> CHARGED_TOTEMS = Set.of(9436, 9439, 9442, 9445);

    public static final int PARASITE_VARBIT = 10151;
    public static final int SLEPE_TELEPORT_VARBIT = 12416;

    public static final String CURSED = "The Nightmare has cursed you, shuffling your prayers!";
    public static final String CURSE_ENDED = "You feel the effects of the Nightmare's curse wear off.";
    public static final String DROWSY = "The Nightmare's spores have infected you, making you feel drowsy!";
    public static final String DROWSY_ENDED = "The Nightmare's infection has worn off.";
    public static final String WEAKENED = "The parasite within you has been weakened.";
    public static final String DESPERATE = "The Nightmare lashes out to survive.";
    public static final String KILL_COUNT = "Your Phosani's Nightmare kill count is:";
    /** A powered staff refusing to cast or be wielded, as in "Tumeken's shadow has no charges! You need to...". */
    public static final String NO_CHARGES = " has no charges";
    public static final String WAKING = "The Nightmare begins to wake...";

    public static final int MEDALLION = 22400;
    public static final int SLEPE_SUBOP = 3;
    public static final WorldPoint SLEPE_ARRIVAL = new WorldPoint(3808, 9700, 1);
    public static final WorldPoint POOL = new WorldPoint(3808, 9780, 1);
    public static final Set<Integer> POOL_OBJECTS = Set.of(29706, 29710);

    public static final List<Integer> DUELING_RINGS = List.of(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566);
    public static final WorldPoint FEROX_ARRIVAL = new WorldPoint(3151, 3636, 0);
    public static final WorldPoint FEROX_BANK = new WorldPoint(3130, 3632, 0);
    public static final WorldPoint FEROX_POOL = new WorldPoint(3128, 3635, 0);
    public static final int FEROX_POOL_OBJECT = 39651;
    public static final int FEROX_BANK_CHEST = 26711;

    private DreamData()
    {
    }

    public static boolean inSanctuary(WorldPoint point)
    {
        return point != null && point.getPlane() == 1 && point.getX() >= 3720 && point.getX() <= 3880
            && point.getY() >= 9690 && point.getY() <= 9800;
    }

    public static boolean atFerox(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= 3120 && point.getX() <= 3160
            && point.getY() >= 3620 && point.getY() <= 3645;
    }

    /**
     * The message says one of the named items has no charges, in any case and after an optional "Your" or "The".
     * With no names to read, any such message counts.
     */
    public static boolean noCharges(String message, Collection<String> names)
    {
        String item = unchargedItem(message);
        return item != null && (names.isEmpty() || names.stream().anyMatch(item::equalsIgnoreCase));
    }

    /** The item a no-charges message names, as the game writes it, or null for another message. */
    public static String unchargedItem(String message)
    {
        int at = message.indexOf(NO_CHARGES);
        return at <= 0 ? null : message.substring(0, at).replaceFirst("^(Your|The) ", "");
    }
}
