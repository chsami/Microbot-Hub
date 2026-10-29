package net.runelite.client.plugins.microbot.FourtifyFishing.data;

import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fishing methods. Each names the spot kind it fishes and the menu actions that spot offers, the Fishing level
 * of its first catch, whether it needs a members world, the tool to carry (a rod can be a pearl rod on a members
 * world), the bait to carry (if any), and the items that arrive: the fish it catches. Tools are carried; none of
 * them needs to be wielded.
 * <p>
 * The rows read as: label, spot kind, Fishing level of the first catch, members only, then the {@link Spec}.
 * <p>
 * A row also holds what the method yields per roll, for rating the methods against each other: every {@link Yield}
 * that gives experience, in the order the game rolls them (the best fish first), how many ticks pass between rolls,
 * and (from {@code ChanceData}) the chance that each yield's own roll succeeds at a Fishing level.
 */
@Getter
public enum FishingMethod {
    /** Shrimps from Fishing 1, anchovies from 15. */
    NET("Small net fishing", SpotKind.NET_BAIT, 1, false, new Spec()
            .actions("Small Net", "Net")
            .tool("a small fishing net", ItemID.NET)
            .gives(ItemID.RAW_ANCHOVIES, 40)
            .gives(ItemID.RAW_SHRIMP, 10)
            .rollTicks(5)
            .catches(ItemID.RAW_SHRIMP, ItemID.RAW_ANCHOVIES)),
    /** Sardines from Fishing 5, herring from 10; one bait is used per catch. */
    SEA_BAIT("Bait fishing", SpotKind.NET_BAIT, 5, false, new Spec()
            .actions("Bait")
            .tool("a fishing rod", ItemID.FISHING_ROD)
            .membersTool(ItemID.FISHINGROD_PEARL)
            .baits("fishing bait", ItemID.FISHING_BAIT)
            .gives(ItemID.RAW_HERRING, 30)
            .gives(ItemID.RAW_SARDINE, 20)
            .rollTicks(5)
            .catches(ItemID.RAW_SARDINE, ItemID.RAW_HERRING)),
    /** Trout from Fishing 20, salmon from 30; one feather is used per catch. */
    FLY("Fly fishing", SpotKind.LURE_BAIT, 20, false, new Spec()
            .actions("Lure")
            .tool("a fly fishing rod", ItemID.FLY_FISHING_ROD)
            .membersTool(ItemID.FISHINGROD_PEARL_FLY)
            .baits("feathers", ItemID.FEATHER)
            .gives(ItemID.RAW_SALMON, 70)
            .gives(ItemID.RAW_TROUT, 50)
            .rollTicks(5)
            .catches(ItemID.RAW_TROUT, ItemID.RAW_SALMON)),
    /** Pike from Fishing 25; one bait is used per catch. */
    RIVER_BAIT("Pike fishing", SpotKind.LURE_BAIT, 25, false, new Spec()
            .actions("Bait")
            .tool("a fishing rod", ItemID.FISHING_ROD)
            .membersTool(ItemID.FISHINGROD_PEARL)
            .baits("fishing bait", ItemID.FISHING_BAIT)
            .gives(ItemID.RAW_PIKE, 60)
            .rollTicks(5.2)
            .catches(ItemID.RAW_PIKE)),
    /**
     * Tuna from Fishing 35, swordfish from 50; both free-to-play, with the best harpoon the level and the world allow.
     * Without any harpoon, an account that has learned barehanded fishing fishes tuna from Fishing 55 with Strength 35
     * (swordfish from 70 and 50) on a members world only; see {@link Spec#barehanded}.
     */
    HARPOON("Harpoon fishing", SpotKind.CAGE_HARPOON, 35, false, new Spec()
            .actions("Harpoon")
            .harpoon()
            .barehanded(3757, 55, 35)
            .gives(ItemID.RAW_SWORDFISH, 100).barehandedXp(10)
            .gives(ItemID.RAW_TUNA, 80).barehandedXp(8)
            .rollTicks(6)
            .catches(ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH)),
    /** Lobsters from Fishing 40. */
    CAGE("Cage fishing", SpotKind.CAGE_HARPOON, 40, false, new Spec()
            .actions("Cage")
            .tool("a lobster pot", ItemID.LOBSTER_POT)
            .gives(ItemID.RAW_LOBSTER, 90)
            .rollTicks(5)
            .catches(ItemID.RAW_LOBSTER)),
    /**
     * Raw karambwanji (Fishing 5) stack in one slot, so the catch never fills the inventory; the spot also gives raw
     * shrimps now and then (junk here). Once the inventory is full and holds a karambwanji slot, the spot gives no
     * more shrimps. Dropping the stack would free a slot at most, so it is banked but not dropped. The spot is
     * at the Holy Lake south of Tai Bwo Wannai; the only requirement published for the fish is Fishing 5.
     */
    KARAMBWANJI("Karambwanji fishing", SpotKind.KARAMBWANJI, 5, true, new Spec()
            .actions("Net")
            .tool("a small fishing net", ItemID.NET)
            .gives(ItemID.TBWT_RAW_KARAMBWANJI, 5)
            .gives(ItemID.RAW_SHRIMP, 10)
            .rollTicks(5)
            .catches(ItemID.TBWT_RAW_KARAMBWANJI)
            .stackingCatches()
            .junk(ItemID.RAW_SHRIMP)),
    /**
     * Mackerel from Fishing 16, cod from 23, bass from 46. The big net brings several things at once. Besides the
     * fish it can bring leather boots and gloves, seaweed and oysters, which are junk: dropped with the fish. It can
     * also bring a casket, a big bass and clue bottles, which are never dropped (they are special items the account
     * keeps).
     */
    BIG_NET("Big net fishing", SpotKind.BIG_NET_HARPOON, 16, true, new Spec()
            .actions("Big Net", "Net")
            .tool("a big fishing net", ItemID.BIG_NET)
            .gives(ItemID.RAW_MACKEREL, 20)
            .gives(ItemID.RAW_COD, 45)
            .gives(ItemID.RAW_BASS, 100)
            .independentRolls()
            .rollTicks(5)
            .catches(ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS)
            .junk(ItemID.LEATHER_BOOTS, ItemID.LEATHER_GLOVES, ItemID.SEAWEED, ItemID.OYSTERSHELL)),
    /**
     * Leaping trout (Fishing 48, Strength 15, Agility 15), salmon (58, 30, 30) and sturgeon (70, 45, 45), caught with a
     * barbarian rod. The method asks for the first fish's levels: Fishing 48, Strength 15 and Agility 15. Barbarian
     * Training must have taught the fishing part: its varbit (3758) is above 0 once it has. The rod is not tradeable,
     * and the pearl barbarian rod is its equipable version. Any of fish offcuts, fishing bait and feathers is
     * bait, one used per catch; they all stack. Roe and caviar also work as bait, but only when no other bait is
     * carried and they do not stack, so they are not taken from the bank. The leaping fish are not food and are
     * dropped or banked as the catch.
     */
    BARBARIAN("Barbarian fishing", SpotKind.BARBARIAN, 48, true, new Spec()
            .actions("Use-rod")
            .tool("a barbarian rod", ItemID.BRUT_FISHING_ROD, ItemID.FISHINGROD_PEARL_BRUT)
            .baits("fish offcuts, fishing bait or feathers", ItemID.BRUT_FISH_CUTS, ItemID.FISHING_BAIT, ItemID.FEATHER)
            .skill(Skill.STRENGTH, 15)
            .skill(Skill.AGILITY, 15)
            .varbit(3758, "the fishing part of Barbarian Training learned")
            .gives(ItemID.BRUT_STURGEON, 80).alsoXp(Skill.STRENGTH, 7).alsoXp(Skill.AGILITY, 7)
            .requiresSkill(Skill.STRENGTH, 45).requiresSkill(Skill.AGILITY, 45)
            .gives(ItemID.BRUT_SPAWNING_SALMON, 70).alsoXp(Skill.STRENGTH, 6).alsoXp(Skill.AGILITY, 6)
            .requiresSkill(Skill.STRENGTH, 30).requiresSkill(Skill.AGILITY, 30)
            .gives(ItemID.BRUT_SPAWNING_TROUT, 50).alsoXp(Skill.STRENGTH, 5).alsoXp(Skill.AGILITY, 5)
            .requiresSkill(Skill.STRENGTH, 15).requiresSkill(Skill.AGILITY, 15)
            .rollTicks(5)
            .catches(ItemID.BRUT_SPAWNING_TROUT, ItemID.BRUT_SPAWNING_SALMON, ItemID.BRUT_STURGEON)),
    /**
     * Monkfish from Fishing 62 at the Piscatoris Fishing Colony, which needs the Swan Song quest to reach. Every run of
     * the method, chosen by hand or by Best available, needs the quest done.
     */
    MONKFISH("Monkfish fishing", SpotKind.PISCATORIS, 62, true, new Spec()
            .quest(Quest.SWAN_SONG)
            .actions("Net")
            .tool("a small fishing net", ItemID.NET)
            .gives(ItemID.RAW_MONKFISH, 120)
            .rollTicks(5)
            .catches(ItemID.RAW_MONKFISH)),
    /** Sharks from Fishing 76, with the best harpoon the level and the world allow; barehanded from Fishing 96 with Strength 76. */
    SHARK("Shark fishing", SpotKind.BIG_NET_HARPOON, 76, true, new Spec()
            .actions("Harpoon")
            .harpoon()
            .barehanded(3757, 96, 76)
            .gives(ItemID.RAW_SHARK, 110).barehandedXp(11)
            .rollTicks(6)
            .catches(ItemID.RAW_SHARK)),
    /**
     * Infernal eels (Fishing 80): the oily rod and fishing bait, with ice gloves worn; without them nothing is
     * caught. The spot is inside Mor Ul Rek, which a TzHaar guard opens to a player with a fire cape; the method
     * does not check that, and Best available never picks it.
     */
    INFERNAL_EEL("Infernal eel fishing", SpotKind.INFERNAL_EEL, 80, true, new Spec()
            .notAuto("the way in is a TzHaar guard's dialogue for a fire cape, which no task has and no varbit shows")
            .actions("Bait")
            .tool("an oily fishing rod", ItemID.OILY_FISHING_ROD, ItemID.FISHINGROD_PEARL_OILY)
            .baits("fishing bait", ItemID.FISHING_BAIT)
            .wears("ice gloves or Smiths gloves (i)", ItemID.ICE_GLOVES, ItemID.SMITHING_UNIFORM_GLOVES_ICE)
            .gives(ItemID.INFERNAL_EEL, 95)
            .rollTicks(5)
            .catches(ItemID.INFERNAL_EEL)),
    /**
     * Anglerfish (Fishing 82) take sandworms or diabolic worms, one used per catch. The diabolic worms speed the
     * catch up and lower the experience of each fish, so the cheap sandworms come first in the bank.
     */
    ANGLERFISH("Anglerfish fishing", SpotKind.ANGLER, 82, true, new Spec()
            .actions("Bait")
            .tool("a fishing rod", ItemID.FISHING_ROD)
            .membersTool(ItemID.FISHINGROD_PEARL)
            .baits("sandworms or diabolic worms", ItemID.PISCARILIUS_SANDWORMS, ItemID.DIABOLIC_WORMS)
            .gives(ItemID.RAW_ANGLERFISH, 120)
            .rollTicks(5)
            .catches(ItemID.RAW_ANGLERFISH));

    private final String label;
    private final SpotKind spotKind;
    /** The menu actions of the spot's NPC that fish with this method, the first one the NPC has is clicked. */
    private final List<String> actions;
    /** The Fishing level of the first catch. */
    private final int level;
    /** The method works only on a members world. */
    private final boolean members;
    private final List<Integer> catchIds;
    /** Items the method brings that are no catch and are of no use: dropped or banked with the fish, by item ID. */
    private final List<Integer> junkIds;
    /**
     * What one roll can give that gives experience, in the order the game rolls them: the best fish first. A fish
     * is only rolled for by a player who may catch it, and one that is rolled for is rolled after every better one
     * failed (see {@link #isIndependentRolls()}).
     */
    private final List<Yield> yields;
    /**
     * Game ticks between two rolls while fishing (the expected value where it varies), which is the same for every
     * fish of a method. Regular fishing rolls every 5 ticks; a harpoon every 6.
     */
    private final double rollTicks;
    /** Every yield is rolled for on its own, whether or not the others succeed (the big net), not after the better ones failed. */
    private final boolean independentRolls;
    /** Why Best available never picks the method (a gate that cannot be checked), or null when it may. */
    private final String notAutoReason;
    @Getter(AccessLevel.NONE)
    private final Spec spec;

    FishingMethod(String label, SpotKind spotKind, int level, boolean members, Spec spec) {
        this.label = label;
        this.spotKind = spotKind;
        this.level = level;
        this.members = members;
        this.actions = Collections.unmodifiableList(spec.actions);
        this.catchIds = Collections.unmodifiableList(spec.catches);
        this.junkIds = Collections.unmodifiableList(spec.junk);
        this.yields = Collections.unmodifiableList(spec.yields);
        this.rollTicks = spec.rollTicks;
        this.independentRolls = spec.independentRolls;
        this.notAutoReason = spec.notAutoReason;
        this.spec = spec;
    }

    /** Best available may pick the method: every gate it has can be read from the game. */
    public boolean isAuto() {
        return notAutoReason == null;
    }

    /**
     * The chance that the roll of one yield succeeds at a real Fishing level: its own roll, before the better fish
     * of the spot had their turn. {@code harpoonRate} is the catch rate in percent of the harpoon fished with (see
     * {@link Harpoon}; 100 for every other tool, and for fishing barehanded). 0 below the first level the fish
     * allows. Published values: {@code ChanceData}.
     */
    public double rollChance(Yield yield, int fishingLevel, int harpoonRate) {
        return Chances.roll(this, yield.itemId, harpoonRate, fishingLevel);
    }

    /** The first Fishing level at which the yield can be rolled for, with a harpoon or any other tool (not barehanded). */
    public int firstLevel(Yield yield) {
        return Chances.firstLevel(this, yield.itemId);
    }

    /** How many more Fishing levels fishing the method's fish barehanded needs than with a harpoon (0 when it has no such way). */
    public int barehandedExtraLevels() {
        return spec.bareVarbit > 0 ? spec.bareFishing - level : 0;
    }

    /** What one catch of the yield gives in every skill: Fishing, and Strength and Agility for the leaping fish. */
    public static final class Yield {
        private final int itemId;
        private final double fishingXp;
        private final Map<Skill, Double> otherXp = new EnumMap<>(Skill.class);
        private double barehandedXp;
        private final Map<Skill, Integer> skills = new EnumMap<>(Skill.class);

        private Yield(int itemId, double fishingXp) {
            this.itemId = itemId;
            this.fishingXp = fishingXp;
        }

        public int getItemId() {
            return itemId;
        }

        public double getFishingXp() {
            return fishingXp;
        }

        /** Experience in the other skills a catch gives, such as Strength and Agility for a leaping fish. */
        public Map<Skill, Double> getOtherXp() {
            return Collections.unmodifiableMap(otherXp);
        }

        /** Strength experience a catch gives on top, when the fish is caught barehanded; 0 for no such way. */
        public double getBarehandedXp() {
            return barehandedXp;
        }

        /** The real levels of other skills the fish asks for (Strength and Agility for a leaping fish), besides Fishing. */
        public Map<Skill, Integer> getSkills() {
            return Collections.unmodifiableMap(skills);
        }

        /** Experience of one catch in every skill the method trains, with the barehanded bonus when {@code barehanded}. */
        public double totalXp(boolean barehanded) {
            double total = fishingXp + (barehanded ? barehandedXp : 0);
            for (double xp : otherXp.values()) {
                total += xp;
            }
            return total;
        }
    }

    /**
     * Everything to carry: the tool, then the bait (when it uses some), then the gear to wear (when it needs some;
     * the activity puts it on once carried). The bait stacks and is used up one per catch, so {@code baitAmount}
     * of it is taken from the bank at a time and the need is met while any is left. The baits of one method are
     * listed in the order they are taken from the bank, and all of them stack.
     */
    public List<NeededItem> needs(int baitAmount) {
        List<NeededItem> needs = new ArrayList<>();
        if (spec.harpoon) {
            NeededItem harpoon = Harpoon.need();
            needs.add(spec.bareVarbit > 0 ? harpoon.orInstead(barehandedText(), this::canFishBarehanded) : harpoon);
        } else {
            List<Integer> tools = new ArrayList<>(spec.toolIds);
            tools.addAll(spec.membersToolIds);
            needs.add(new NeededItem(spec.toolLabel, 1, tools, (state, id) -> !spec.membersToolIds.contains(id) || state.isMembersWorld()));
        }
        if (!spec.baitIds.isEmpty()) {
            needs.add(new NeededItem(baitAmount + " " + spec.baitLabel, baitAmount, spec.baitIds, (state, id) -> true).stacks().usedUp());
        }
        if (!spec.wornIds.isEmpty()) {
            needs.add(NeededItem.wear(spec.wornLabel, spec.wornIds, (state, id) -> true));
        }
        return needs;
    }

    /**
     * The account can fish this method with no tool: it has learned barehanded fishing (its varbit is above 0: Otto
     * Godblessed teaches it and the miniquest's own task is to fish this way), is on a members world, and has the
     * real (unboosted) Fishing and Strength levels of the method's first catch barehanded. Barehanded fishing needs
     * 20 more Fishing levels than the same fish with a harpoon, and the harpoon's level in Strength.
     */
    public boolean canFishBarehanded(GameState state) {
        return spec.bareVarbit > 0 && state.isMembersWorld() && state.varbit(spec.bareVarbit) > 0
                && state.level(Skill.FISHING) >= spec.bareFishing && state.level(Skill.STRENGTH) >= spec.bareStrength;
    }

    private String barehandedText() {
        return "barehanded fishing learned (from Otto Godblessed) with Fishing " + spec.bareFishing + " and Strength " + spec.bareStrength
                + " on a members world";
    }

    /**
     * What must hold before the method can start: the Fishing level, a members world when it needs one, the other
     * real (unboosted) skill levels it asks for, the progress varbit when it has one (above 0), and the quests it
     * needs finished. Both modes check all of these.
     */
    public List<Requirement> requirements() {
        List<Requirement> requirements = new ArrayList<>();
        requirements.add(Requirement.level(Skill.FISHING, level));
        if (members) {
            requirements.add(new Requirement("a members world", GameState::isMembersWorld));
        }
        spec.skills.forEach((skill, minimum) -> requirements.add(Requirement.level(skill, minimum)));
        if (spec.varbit > 0) {
            requirements.add(new Requirement(spec.varbitText, state -> state.varbit(spec.varbit) > 0));
        }
        spec.quests.forEach(quest -> requirements.add(Requirement.quest(quest)));
        return requirements;
    }

    /** The quests the requirements read, to be asked for in the surroundings. */
    public Set<Quest> quests() {
        return Collections.unmodifiableSet(spec.quests);
    }

    /** The varbits the requirements read, to be asked for in the surroundings. */
    public Set<Integer> varbits() {
        Set<Integer> ids = new LinkedHashSet<>();
        if (spec.varbit > 0) {
            ids.add(spec.varbit);
        }
        if (spec.bareVarbit > 0) {
            ids.add(spec.bareVarbit);
        }
        return ids;
    }

    /**
     * What the method drops when the inventory is full, by item ID: the junk, and the fish unless they stack (a stack
     * is one slot, which dropping would only waste).
     */
    public Set<Integer> dropIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        if (!spec.stackingCatches) {
            ids.addAll(catchIds);
        }
        ids.addAll(junkIds);
        return ids;
    }

    /** What the method banks when the inventory is full, by item ID: the fish and the junk. */
    public Set<Integer> bankIds() {
        Set<Integer> ids = new LinkedHashSet<>(catchIds);
        ids.addAll(junkIds);
        return ids;
    }

    @Override
    public String toString() {
        return label;
    }

    /** The parts of a row that are lists, written as a chain of calls. */
    public static final class Spec {
        private final List<String> actions = new ArrayList<>();
        private String toolLabel;
        private final List<Integer> toolIds = new ArrayList<>();
        private final List<Integer> membersToolIds = new ArrayList<>();
        private boolean harpoon;
        private String baitLabel;
        private final List<Integer> baitIds = new ArrayList<>();
        private final List<Integer> catches = new ArrayList<>();
        private final List<Integer> junk = new ArrayList<>();
        private String wornLabel;
        private final List<Integer> wornIds = new ArrayList<>();
        private boolean stackingCatches;
        private final Map<Skill, Integer> skills = new LinkedHashMap<>();
        private int varbit;
        private String varbitText;
        private int bareVarbit;
        private int bareFishing;
        private int bareStrength;
        private final List<Yield> yields = new ArrayList<>();
        private double rollTicks;
        private boolean independentRolls;
        private final Set<Quest> quests = new LinkedHashSet<>();
        private String notAutoReason;

        /** The spot menu actions that fish, in the order to prefer. */
        Spec actions(String... actions) {
            this.actions.addAll(Arrays.asList(actions));
            return this;
        }

        /** The tool to carry: any of these, best first. */
        Spec tool(String label, Integer... itemIds) {
            this.toolLabel = label;
            this.toolIds.addAll(Arrays.asList(itemIds));
            return this;
        }

        /** The tool is a harpoon: the best of the {@link Harpoon}s the level and the world allow. */
        Spec harpoon() {
            this.harpoon = true;
            return this;
        }

        /**
         * Tools of the same speed that count only on a members world, such as the pearl rods (equipable rods that
         * work as the plain ones). Carried or worn, as the plain tool.
         */
        Spec membersTool(Integer... itemIds) {
            this.membersToolIds.addAll(Arrays.asList(itemIds));
            return this;
        }

        /** The bait to carry, used up one per catch: any of these, in the order to take them from the bank. All must stack. */
        Spec baits(String label, Integer... itemIds) {
            this.baitLabel = label;
            this.baitIds.addAll(Arrays.asList(itemIds));
            return this;
        }

        /** A real level of another skill the method asks for. */
        Spec skill(Skill skill, int level) {
            this.skills.put(skill, level);
            return this;
        }

        /** A varbit that must be above 0 (a part of a quest or miniquest learned), and what it says. */
        Spec varbit(int id, String text) {
            this.varbit = id;
            this.varbitText = text;
            return this;
        }

        /**
         * The harpoon is not needed once the account has learned barehanded fishing (the varbit is above 0) and has
         * the real Fishing and Strength levels of the first catch fished that way, on a members world. A harpoon the
         * account has is still used and still fetched from the bank: barehanded is only the way out of needing one.
         */
        Spec barehanded(int varbit, int fishingLevel, int strengthLevel) {
            this.bareVarbit = varbit;
            this.bareFishing = fishingLevel;
            this.bareStrength = strengthLevel;
            return this;
        }

        /** Gear that must be worn to catch anything: any of these, best first. Put on when carried. */
        Spec wears(String label, Integer... itemIds) {
            this.wornLabel = label;
            this.wornIds.addAll(Arrays.asList(itemIds));
            return this;
        }

        /** The catches stack in one inventory slot, so they never fill the inventory. */
        Spec stackingCatches() {
            this.stackingCatches = true;
            return this;
        }

        /** Junk the method brings, dropped by item ID when the inventory is full. Never a special item (see Rewards). */
        Spec junk(Integer... itemIds) {
            this.junk.addAll(Arrays.asList(itemIds));
            return this;
        }

        /**
         * A yield of one roll that gives {@code fishingXp} Fishing experience per catch: list them in the order the game
         * rolls them, the best fish first. The calls below add to the last one listed.
         */
        Spec gives(int itemId, double fishingXp) {
            yields.add(new Yield(itemId, fishingXp));
            return this;
        }

        /** Experience in another skill that a catch of the last yield gives. */
        Spec alsoXp(Skill skill, double xp) {
            last().otherXp.put(skill, xp);
            return this;
        }

        /** A real level of another skill that the last yield's fish asks for, besides its Fishing level. */
        Spec requiresSkill(Skill skill, int level) {
            last().skills.put(skill, level);
            return this;
        }

        /** Strength experience that a catch of the last yield gives on top when caught barehanded. */
        Spec barehandedXp(double xp) {
            last().barehandedXp = xp;
            return this;
        }

        /** Game ticks between two rolls (the expected value where it varies). */
        Spec rollTicks(double ticks) {
            this.rollTicks = ticks;
            return this;
        }

        /** The yields are rolled for on their own, not one after the other failed. */
        Spec independentRolls() {
            this.independentRolls = true;
            return this;
        }

        /** The method needs the quest finished, in every mode (an area that belongs to one spot is a gate in GateData instead). */
        Spec quest(Quest quest) {
            this.quests.add(quest);
            return this;
        }

        /** Best available never picks the method, for this reason (a gate no task meets and no varbit shows). */
        Spec notAuto(String reason) {
            this.notAutoReason = reason;
            return this;
        }

        private Yield last() {
            return yields.get(yields.size() - 1);
        }

        /** The fish the method catches, lowest level first. */
        Spec catches(Integer... itemIds) {
            this.catches.addAll(Arrays.asList(itemIds));
            return this;
        }
    }
}
