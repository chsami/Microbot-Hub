package net.runelite.client.plugins.microbot.droslayer.walker.support.banking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.support.*;
import net.runelite.client.plugins.microbot.shortestpath.PurchasableItemCatalog;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.RuneFilter;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

@Slf4j
public final class Rs2WalkerBankingPlanner {




    /**
     * Whether a plain {@link TransportType#TRANSPORT} takes part in bank planning.
     *
     * <p>Previously only currency-bearing ones did, so an item-gated obstacle — a machete for a
     * jungle bush, a pickaxe for a rockfall, a Shantay pass — fell through
     * {@link #hasRequiredTransportItems} to its catch-all {@code return true}, was never reported as
     * missing, and was never withdrawn. That contradicted the pathfinder:
     * {@code PathfinderConfig.hasRequiredItems} counts <em>bank</em> contents when
     * {@code useBankItems} is set, so a route was planned through the obstacle on the strength of a
     * banked item the planner then declined to fetch, stranding the walk at the obstacle.
     *
     * <p>This only widens which transports are <em>eligible</em>. Collection is path-scoped —
     * {@link #getTransportsForDestination} pathfinds first and inspects only transports on the
     * resulting route — so an item is fetched solely when the chosen route actually needs it.
     */
    static boolean planningCoversPlainTransport(Transport transport) {
        if (transport == null || transport.getType() != TransportType.TRANSPORT) {
            return false;
        }
        return transport.getCurrencyAmount() > 0
                || (transport.getItemIdRequirements() != null && !transport.getItemIdRequirements().isEmpty());
    }



    public static boolean hasRequiredTransportItems(Transport transport) {
        if (transport == null) {
            return false;
        }

        if (transport.getType() == TransportType.FAIRY_RING) {
            return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF)
                    || net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment.isWearing(ItemID.DRAMEN_STAFF)
                    || Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                    || net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                    || Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM
                || transport.getType() == TransportType.TELEPORTATION_SPELL
                || transport.getType() == TransportType.CANOE
                || transport.getType() == TransportType.BOAT
                || transport.getType() == TransportType.CHARTER_SHIP
                || transport.getType() == TransportType.SHIP
                || transport.getType() == TransportType.MINECART
                || transport.getType() == TransportType.MAGIC_CARPET
                || planningCoversPlainTransport(transport)) {
            if (transport.getType() == TransportType.TELEPORTATION_SPELL && transport.getDisplayInfo() != null) {
                if (!transport.getItemRequirements().isEmpty()) {
                    return TransportItemRequirement.selectProviders(
                            transport.getItemRequirements(),
                            Rs2WalkerBankingPlanner::carriedRequirementItemQuantity,
                            itemId -> net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId),
                            itemId -> net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId))
                            .isPresent();
                }
                String spellName = transport.getDisplayInfo().contains(":")
                        ? transport.getDisplayInfo().split(":")[0].trim()
                        : transport.getDisplayInfo().trim();
                boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
                String displayInfo = hasMultipleDestination
                        ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                        : transport.getDisplayInfo();
                log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
                Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
                return Rs2Magic.hasRequiredRunes(rs2Spell);
            }
            if (isCurrencyBasedTransport(transport.getType())
                    && (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty())
                    && transport.getCurrencyName() != null
                    && !transport.getCurrencyName().isEmpty()
                    && transport.getCurrencyAmount() > 0) {
                int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
                return Rs2Inventory.itemQuantity(currencyItemId) >= transport.getCurrencyAmount();
            }
            if (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) {
                return true;
            }

            return transport.getItemRequirements().stream()
                    .allMatch(requirement -> requirement.isSatisfiedBy(
                            Rs2WalkerBankingPlanner::carriedItemQuantity));
        }

        return true;
    }


	private static boolean isSpellTransport(Rs2TransportEdge transport)
	{
		return transport.getType() == Rs2TransportType.TELEPORTATION_SPELL;
	}


	private static String spellLookupName(String displayInfo)
	{
		return displayInfo.contains(":")
			? displayInfo.split(":", 2)[0].trim().toLowerCase(Locale.ROOT)
			: displayInfo.trim().toLowerCase(Locale.ROOT);
	}


	/**
	 * Build one atomic preparation contract for the exact selected edges.
	 *
	 * <p>Rune quantities include the inventory, rune pouch, equipped providers and combination runes.
	 * Bank rune quantities are kept separate from raw bank stacks because a semantic contribution
	 * still has to resolve to a concrete item that can actually be withdrawn.</p>
	 */
	public static Rs2TransportLoadout getMissingTransportEdgeLoadout(
		List<Rs2TransportEdge> transports)
	{
		Map<Integer, Integer> carriedRunes = runeQuantities(
			RuneFilter.builder().includeBank(false).build());
		Map<Integer, Integer> bankRunes = runeQuantities(RuneFilter.builder()
			.includeInventory(false)
			.includeEquipment(false)
			.includeRunePouch(false)
			.includeBank(true)
			.build());
		return getMissingTransportEdgeLoadout(
			transports,
			Rs2Bank::count,
			Rs2WalkerBankingPlanner::carriedItemQuantity,
			itemId -> carriedRunes.getOrDefault(itemId, carriedItemQuantity(itemId)),
			itemId -> bankRunes.getOrDefault(itemId, safeQuantity(Rs2Bank::count, itemId)),
			net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment::isWearing);
	}


	/** Pure source-aware selection seam used by banking regressions. */
	static Rs2TransportLoadout getMissingTransportEdgeLoadout(
		List<Rs2TransportEdge> transports,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider,
		IntUnaryOperator carriedRequirementQuantityProvider,
		IntUnaryOperator bankRequirementQuantityProvider,
		IntPredicate equippedItemProvider)
	{
		if (transports == null)
		{
			return Rs2TransportLoadout.empty();
		}
		Map<Integer, Integer> withdrawals = new LinkedHashMap<>();
        Map<Integer, Integer> consumed = new LinkedHashMap<>();
		LinkedHashSet<Integer> equipmentItemIds = new LinkedHashSet<>();
		for (Rs2TransportEdge transport : transports)
		{
			if (isSpellTransport(transport) && transport.getItemRequirements().isEmpty())
			{
				for (Map.Entry<Integer, Integer> rune : getSpellRuneRequirements(transport).entrySet())
				{
					int bankQuantity = safeQuantity(bankQuantityProvider, rune.getKey());
					if (bankQuantity < rune.getValue())
					{
						return Rs2TransportLoadout.unavailable();
					}
					withdrawals.merge(rune.getKey(), rune.getValue(), Integer::sum);
				}
				continue;
			}

			if (isCurrencyBasedTransport(transport.getType())
				&& transport.getCurrencyAmount() > 0
				&& transport.getItemRequirements().isEmpty())
			{
				int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
				if (currencyItemId > 0)
				{
					withdrawals.merge(
						currencyItemId, transport.getCurrencyAmount(), Integer::sum);
				}
				continue;
			}

			List<Rs2TransportItemRequirement> requirements = transport.getItemRequirements();
            boolean houseRingStaff = net.runelite.client.plugins.microbot.droslayer.walker.MaxPohSupport.requiresStaff(transport);
			if (houseRingStaff || (transport.getType() == Rs2TransportType.FAIRY_RING
				&& requirements.isEmpty()
				&& Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1))
			{
                int staff = -1;
                for (int candidate : new int[]{ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF})
                    if (equippedItemProvider.test(candidate)) { staff = candidate; break; }
                if (staff < 0) for (int candidate : new int[]{ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF})
                    if (safeQuantity(carriedQuantityProvider,candidate) > 0) { staff = candidate; break; }
                if (staff < 0) for (int candidate : new int[]{ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF})
                    if (safeQuantity(bankQuantityProvider,candidate) > 0) { staff = candidate; break; }
                if (staff < 0 || !addProviderPreparation(staff,withdrawals,equipmentItemIds,
                    bankQuantityProvider,carriedQuantityProvider,equippedItemProvider))
                    return Rs2TransportLoadout.unavailable();
                if (!houseRingStaff) continue;
                // House entry supplies remain separate; the ring staff is equipment, never consumed by a tablet leg.
                requirements = requirements.stream().filter(r -> r.getStaffAlternatives().isEmpty())
                    .collect(Collectors.toList());
			}

			Rs2TransportItemRequirement.ProviderSelection providers =
				Rs2TransportItemRequirement.selectEquipmentProviders(
					requirements,
					itemId -> safeSum(
						safeQuantity(carriedRequirementQuantityProvider, itemId),
						safeQuantity(bankRequirementQuantityProvider, itemId)),
					itemId -> safeSum(
						safeQuantity(carriedQuantityProvider, itemId),
						safeQuantity(bankQuantityProvider, itemId)) > 0,
					itemId -> safeSum(
						safeQuantity(carriedQuantityProvider, itemId),
						safeQuantity(bankQuantityProvider, itemId)) > 0)
					.orElse(null);
			if (providers == null)
			{
				return Rs2TransportLoadout.unavailable();
			}
			if (!addProviderPreparation(
				providers.getStaffItemId(), withdrawals, equipmentItemIds,
				bankQuantityProvider, carriedQuantityProvider, equippedItemProvider)
				|| !addProviderPreparation(
				providers.getOffhandItemId(), withdrawals, equipmentItemIds,
				bankQuantityProvider, carriedQuantityProvider, equippedItemProvider))
			{
				return Rs2TransportLoadout.unavailable();
			}

			for (Rs2TransportItemRequirement requirement : requirements)
			{
                boolean providedByEquipment = requirement.getStaffAlternatives().contains(providers.getStaffItemId())
                    || requirement.getOffhandAlternatives().contains(providers.getOffhandItemId());
                if (!providedByEquipment && (transport.isConsumable() || requirement.isRuneOnly())
                    && requirement.getAlternatives().values().stream().allMatch(quantity -> quantity > 0)) {
                    if (!allocateConsumable(requirement.getAlternatives(), consumed, withdrawals,
                        carriedRequirementQuantityProvider, bankQuantityProvider)) return Rs2TransportLoadout.unavailable();
                    continue;
                }
				if (requirement.isSatisfiedBy(carriedRequirementQuantityProvider) || providedByEquipment)
				{
					continue;
				}
				if (!addPreferredRequirement(
					withdrawals,
					requirement.getAlternatives(),
					transport.getType(),
					bankQuantityProvider,
					carriedRequirementQuantityProvider))
				{
					return Rs2TransportLoadout.unavailable();
				}
			}
		}
		if (withdrawals.isEmpty() && equipmentItemIds.isEmpty())
		{
			return Rs2TransportLoadout.empty();
		}
		for (Map.Entry<Integer, Integer> withdrawal : withdrawals.entrySet())
		{
			if (safeQuantity(bankQuantityProvider, withdrawal.getKey()) < withdrawal.getValue())
			{
				return Rs2TransportLoadout.unavailable();
			}
		}
		return new Rs2TransportLoadout(
			withdrawals, new ArrayList<>(equipmentItemIds), true);
	}



    /** Count a carried tablet/rune once across the entire journey, not once per edge. */
    private static boolean allocateConsumable(Map<Integer,Integer> alternatives, Map<Integer,Integer> consumed,
        Map<Integer,Integer> withdrawals, IntUnaryOperator carried, IntUnaryOperator bank) {
        int selected = -1;
        int selectedTotal = 0;
        int selectedMissing = Integer.MAX_VALUE;
        for (Map.Entry<Integer,Integer> alternative : alternatives.entrySet()) {
            int id = alternative.getKey();
            long total = (long) consumed.getOrDefault(id,0) + alternative.getValue();
            long missing = Math.max(0L,total-safeQuantity(carried,id));
            if (total <= Integer.MAX_VALUE && missing <= safeQuantity(bank,id) && missing < selectedMissing) {
                selected=id; selectedTotal=(int)total; selectedMissing=(int)missing;
            }
        }
        if (selected < 0) return false;
        consumed.put(selected,selectedTotal);
        if (selectedMissing > 0) withdrawals.merge(selected,selectedMissing,Math::max);
        return true;
    }


	private static boolean addProviderPreparation(
		int itemId,
		Map<Integer, Integer> withdrawals,
		Set<Integer> equipmentItemIds,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider,
		IntPredicate equippedItemProvider)
	{
		if (itemId <= 0 || equippedItemProvider.test(itemId))
		{
			return true;
		}
		equipmentItemIds.add(itemId);
		if (safeQuantity(carriedQuantityProvider, itemId) > 0)
		{
			return true;
		}
		if (safeQuantity(bankQuantityProvider, itemId) <= 0)
		{
			return false;
		}
		withdrawals.merge(itemId, 1, Math::max);
		return true;
	}


	private static boolean addPreferredRequirement(
		Map<Integer, Integer> requested,
		Map<Integer, Integer> alternatives,
		Rs2TransportType transportType,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider)
	{
		Integer preferredItemId = null;
		int preferredBankQuantity = 0;
		int preferredDeficit = Integer.MAX_VALUE;
		for (Map.Entry<Integer, Integer> alternative : alternatives.entrySet())
		{
			int itemId = alternative.getKey();
			int requiredQuantity = alternative.getValue();
			if (requiredQuantity == 0)
			{
				continue;
			}
			int bankQuantity = safeQuantity(bankQuantityProvider, itemId);
			int deficit = Math.max(0,
				requiredQuantity - safeQuantity(carriedQuantityProvider, itemId));
			boolean satisfies = bankQuantity >= deficit;
			boolean preferredSatisfies = preferredItemId != null
				&& preferredBankQuantity >= preferredDeficit;
			if (preferredItemId == null
				|| satisfies && !preferredSatisfies
				|| satisfies == preferredSatisfies && deficit < preferredDeficit
				|| satisfies == preferredSatisfies && deficit == preferredDeficit
					&& bankQuantity > preferredBankQuantity)
			{
				preferredItemId = itemId;
				preferredBankQuantity = bankQuantity;
				preferredDeficit = deficit;
			}
		}

		if (preferredItemId == null)
		{
			return false;
		}
		if (preferredBankQuantity < preferredDeficit)
		{
			PurchasableItemCatalog.PurchasableItem purchasable = alternatives.keySet().stream()
				.map(PurchasableItemCatalog::byItemId)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
			int currencyItemId = purchasable == null
				? -1 : getCurrencyItemId(purchasable.costCurrencyName);
			if (currencyItemId > 0)
			{
				int requiredQuantity = alternatives.get(purchasable.itemId);
				int itemsNeeded = isCurrencyBasedTransport(transportType)
					? 1 : requiredQuantity;
				requested.merge(
					currencyItemId, purchasable.costAmount * itemsNeeded, Integer::sum);
				return true;
			}
			return false;
		}
		if (preferredDeficit > 0)
		{
			requested.merge(preferredItemId, preferredDeficit, Integer::sum);
		}
		return true;
	}


	private static Map<Integer, Integer> runeQuantities(RuneFilter filter)
	{
		Map<Integer, Integer> quantities = new HashMap<>();
		TravelRunes.getRunes(filter).forEach((rune, quantity) ->
			quantities.put(rune.getItemId(), quantity));
		return quantities;
	}


	private static int safeSum(int first, int second)
	{
		long sum = (long) Math.max(0, first) + Math.max(0, second);
		return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}


	private static int safeQuantity(IntUnaryOperator provider, int itemId)
	{
		try
		{
			return Math.max(0, provider.applyAsInt(itemId));
		}
		catch (Exception exception)
		{
			log.debug("Could not check bank for item {}: {}", itemId, exception.getMessage());
			return 0;
		}
	}


    private static int carriedItemQuantity(int itemId) {
        int quantity = Rs2Inventory.itemQuantity(itemId);
        quantity += net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment.wornItems().getOrDefault(itemId, 0);
        return quantity;
    }


	private static int carriedRequirementItemQuantity(int itemId)
	{
		Runes rune = Runes.byItemId(itemId);
		if (rune == null)
		{
			return carriedItemQuantity(itemId);
		}
		return TravelRunes.getRunes().getOrDefault(rune, 0);
	}



	private static Map<Integer, Integer> getSpellRuneRequirements(Rs2TransportEdge transport)
	{
		Map<Integer, Integer> runeRequirements = new HashMap<>();
		if (!isSpellTransport(transport) || transport.getDisplayInfo() == null)
		{
			return runeRequirements;
		}
		try
		{
			Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(spellLookupName(transport.getDisplayInfo()));
			if (rs2Spell == null)
			{
				return runeRequirements;
			}
			Rs2Magic.getRequiredRunes(rs2Spell, 1, true).forEach((rune, quantity) ->
				runeRequirements.put(rune.getItemId(), quantity));
		}
		catch (Exception exception)
		{
			log.warn("Error getting spell rune requirements for transport '{}': {}",
				transport.getDisplayInfo(), exception.getMessage());
		}
		return runeRequirements;
	}


    /** Package-private so the planning tests can select the same rows this collector accepts. */
    static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT
                || transportType == TransportType.CHARTER_SHIP
                || transportType == TransportType.SHIP
                || transportType == TransportType.MINECART
                || transportType == TransportType.MAGIC_CARPET
                || transportType == TransportType.TRANSPORT;
    }


	static boolean isCurrencyBasedTransport(Rs2TransportType transportType)
	{
		return transportType == Rs2TransportType.BOAT
			|| transportType == Rs2TransportType.CHARTER_SHIP
			|| transportType == Rs2TransportType.SHIP
			|| transportType == Rs2TransportType.MINECART
			|| transportType == Rs2TransportType.MAGIC_CARPET
			|| transportType == Rs2TransportType.TRANSPORT;
	}


    private static int getCurrencyItemId(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return -1;
        }

        String currency = currencyName.trim().toLowerCase();
        switch (currency) {
            case "coins":
                return ItemID.COINS;
            case "ecto-token":
                return ItemID.ECTOTOKEN;
            default:
                log.warn("Unknown currency type: {}", currencyName);
                return -1;
        }
    }
}
