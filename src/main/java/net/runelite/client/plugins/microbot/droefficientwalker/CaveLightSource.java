package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Actual light providers. Filled unlit sources require a separate observed lighting receipt. */
public final class CaveLightSource {
    public static final int TINDERBOX = ItemID.TINDERBOX;
    private static final List<Integer> PREFERRED = List.of(
            ItemID.WINT_TORCH, ItemID.WINT_TORCH_OFFHAND,
            ItemID.SEERS_HEADBAND_EASY, ItemID.SEERS_HEADBAND_MEDIUM,
            ItemID.SEERS_HEADBAND_HARD, ItemID.SEERS_HEADBAND_ELITE,
            ItemID.SKILLCAPE_FIREMAKING, ItemID.SKILLCAPE_FIREMAKING_TRIMMED, ItemID.SKILLCAPE_MAX,
            ItemID.BULLSEYE_LANTERN_LIT, ItemID.TOG_SAPPHIRE_LANTERN_LIT,
            ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST,
            ItemID.ABYSSAL_LANTERN_NORMAL, ItemID.ABYSSAL_LANTERN_NORMAL_BLUE,
            ItemID.ABYSSAL_LANTERN_NORMAL_RED, ItemID.ABYSSAL_LANTERN_NORMAL_WHITE,
            ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE, ItemID.ABYSSAL_LANTERN_NORMAL_GREEN,
            ItemID.ABYSSAL_LANTERN_OAK, ItemID.ABYSSAL_LANTERN_WILLOW,
            ItemID.ABYSSAL_LANTERN_MAPLE, ItemID.ABYSSAL_LANTERN_YEW,
            ItemID.ABYSSAL_LANTERN_BLISTERWOOD, ItemID.ABYSSAL_LANTERN_MAGIC,
            ItemID.ABYSSAL_LANTERN_REDWOOD, ItemID.CAVE_GOBLIN_MINING_HELMET_LIT,
            ItemID.OIL_LANTERN_LIT, ItemID.CANDLE_LANTERN_LIT, ItemID.CANDLE_LANTERN_BLACK_LIT,
            ItemID.OIL_LAMP_LIT, ItemID.TORCH_LIT, ItemID.LIT_CANDLE, ItemID.LIT_BLACK_CANDLE);
    private static final Set<Integer> LIT = Set.copyOf(PREFERRED);
    private static final List<Integer> UNLIT = List.of(ItemID.BULLSEYE_LANTERN_UNLIT,
            ItemID.TOG_SAPPHIRE_LANTERN_UNLIT, ItemID.BULLSEYE_LANTERN_UNLIT_LUNAR_QUEST,
            ItemID.CAVE_GOBLIN_MINING_HELMET_UNLIT, ItemID.OIL_LANTERN_UNLIT,
            ItemID.CANDLE_LANTERN_UNLIT, ItemID.CANDLE_LANTERN_BLACK_UNLIT,
            ItemID.OIL_LAMP_UNLIT, ItemID.TORCH_UNLIT, ItemID.UNLIT_CANDLE, ItemID.UNLIT_BLACK_CANDLE);

    public static Set<Integer> litItems() { return LIT; }
    public static boolean isSafeLight(int id) { return LIT.contains(id); }
    public static int chooseLit(IntPredicate owned) {
        for (int id : PREFERRED) if (owned.test(id)) return id;
        return -1;
    }
    public static int chooseUnlit(IntPredicate owned) {
        for (int id : UNLIT) if (owned.test(id)) return id;
        return -1;
    }
    public static int litVariant(int unlit) {
        if (unlit == ItemID.BULLSEYE_LANTERN_UNLIT) return ItemID.BULLSEYE_LANTERN_LIT;
        if (unlit == ItemID.TOG_SAPPHIRE_LANTERN_UNLIT) return ItemID.TOG_SAPPHIRE_LANTERN_LIT;
        if (unlit == ItemID.BULLSEYE_LANTERN_UNLIT_LUNAR_QUEST) return ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST;
        if (unlit == ItemID.CAVE_GOBLIN_MINING_HELMET_UNLIT) return ItemID.CAVE_GOBLIN_MINING_HELMET_LIT;
        if (unlit == ItemID.OIL_LANTERN_UNLIT) return ItemID.OIL_LANTERN_LIT;
        if (unlit == ItemID.CANDLE_LANTERN_UNLIT) return ItemID.CANDLE_LANTERN_LIT;
        if (unlit == ItemID.CANDLE_LANTERN_BLACK_UNLIT) return ItemID.CANDLE_LANTERN_BLACK_LIT;
        if (unlit == ItemID.OIL_LAMP_UNLIT) return ItemID.OIL_LAMP_LIT;
        if (unlit == ItemID.TORCH_UNLIT) return ItemID.TORCH_LIT;
        if (unlit == ItemID.UNLIT_CANDLE) return ItemID.LIT_CANDLE;
        if (unlit == ItemID.UNLIT_BLACK_CANDLE) return ItemID.LIT_BLACK_CANDLE;
        return -1;
    }

    static int lightingLevel(int unlit) {
        if (unlit == ItemID.UNLIT_CANDLE || unlit == ItemID.UNLIT_BLACK_CANDLE || unlit == ItemID.TORCH_UNLIT) return 1;
        if (unlit == ItemID.CANDLE_LANTERN_UNLIT || unlit == ItemID.CANDLE_LANTERN_BLACK_UNLIT) return 4;
        if (unlit == ItemID.OIL_LAMP_UNLIT) return 12;
        if (unlit == ItemID.OIL_LANTERN_UNLIT) return 26;
        if (unlit == ItemID.CAVE_GOBLIN_MINING_HELMET_UNLIT) return 65;
        return litVariant(unlit) > 0 ? 49 : Integer.MAX_VALUE;
    }

    static final class Preparation {
        final Map<Integer,Integer> withdrawals;
        final int unlit;
        Preparation(Map<Integer,Integer> withdrawals, int unlit) {
            this.withdrawals = Collections.unmodifiableMap(new LinkedHashMap<>(withdrawals)); this.unlit = unlit;
        }
    }

    /** Owned supplies only; never hands an untradeable/lit item to a buyer. */
    static Preparation prepare(IntUnaryOperator inventory, IntPredicate worn, IntUnaryOperator bank, int firemaking) {
        if (chooseLit(id -> inventory.applyAsInt(id) > 0 || worn.test(id)) > 0)
            return new Preparation(Map.of(), -1);
        int unlit = chooseUnlit(id -> inventory.applyAsInt(id) > 0 && firemaking >= lightingLevel(id));
        if (unlit > 0 && inventory.applyAsInt(TINDERBOX) > 0) return new Preparation(Map.of(), unlit);
        int lit = chooseLit(id -> bank.applyAsInt(id) > 0);
        if (lit > 0) return new Preparation(Map.of(lit, 1), -1);
        if (inventory.applyAsInt(TINDERBOX) <= 0 && bank.applyAsInt(TINDERBOX) <= 0) return null;
        if (unlit < 0) unlit = chooseUnlit(id -> bank.applyAsInt(id) > 0 && firemaking >= lightingLevel(id));
        if (unlit < 0) return null;
        Map<Integer,Integer> missing = new LinkedHashMap<>();
        if (inventory.applyAsInt(unlit) <= 0) missing.put(unlit, 1);
        if (inventory.applyAsInt(TINDERBOX) <= 0) missing.put(TINDERBOX, 1);
        return new Preparation(missing, unlit);
    }

    /** One client-thread snapshot for planning or the committed cave-route preflight. */
    static Preparation prepare(boolean bankAllowed) {
        Map<Integer,Integer> inventory = new HashMap<>();
        Rs2Inventory.all().stream().filter(item -> item != null && !item.isNoted() && item.getQuantity() > 0)
                .forEach(item -> inventory.merge(item.getId(), item.getQuantity(), Integer::sum));
        Set<Integer> worn = TravelEquipment.wornItems().keySet();
        return prepare(id -> inventory.getOrDefault(id, 0), worn::contains,
                bankAllowed ? Rs2Bank::count : id -> 0, Rs2Player.getRealSkillLevel(Skill.FIREMAKING));
    }

    /** Call inside a client-thread state snapshot, before admitting or executing an entrance. */
    public static boolean hasLight() {
        return Rs2Inventory.all().stream().anyMatch(item -> item != null && !item.isNoted()
                && item.getQuantity() > 0 && isSafeLight(item.getId()))
                || TravelEquipment.wornItems().keySet().stream().anyMatch(CaveLightSource::isSafeLight);
    }

    private CaveLightSource() { }
}
