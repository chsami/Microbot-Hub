package net.runelite.client.plugins.microbot.kebbitmeat;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class KebbitmeatScript extends Script {

	public enum State {
		TRAPPING,
		CUTTING_LOGS,
		WALKING_TO_BANK,
		BANKING,
		WALKING_TO_AREA
	}

	private static final String RAW_WILD_KEBBIT = "Raw wild kebbit";
	private static final String KNIFE = "Knife";
	private static final String DEAD_TREE = "Dead tree";

	private static final String[] KANDARIN_HEADGEAR = {
		"Kandarin headgear 4",
		"Kandarin headgear 3",
		"Kandarin headgear 2",
		"Kandarin headgear 1"
	};

	private static final int FULL_CLAW_TRAP    = 20651; // kebbit fully caught, ready to dismantle
	private static final int ACTIVE_TRAP       = 19217; // trap set, waiting for kebbit
	private static final int INTERMEDIATE_TRAP = 20647; // kebbit triggered trap, transitioning to full
	private static final int EMPTY_BOULDER     = 19215; // no trap set

	private static final int HUNTING_AREA_RADIUS = 25;

	private State currentState = State.TRAPPING;
	private int kebbitsCollected = 0;

	public State getCurrentState() {
		return currentState;
	}

	public int getKebbitsCollected() {
		return kebbitsCollected;
	}

	private KebbitmeatConfig config;

	public boolean run(KebbitmeatConfig config) {
		this.config = config;
		currentState = State.TRAPPING;

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try {
				if (!Microbot.isLoggedIn()) return;
				if (!super.run()) return;

				switch (currentState) {
					case TRAPPING:
						handleTrapping();
						break;
					case CUTTING_LOGS:
						handleCuttingLogs();
						break;
					case WALKING_TO_BANK:
						handleWalkingToBank();
						break;
					case BANKING:
						handleBanking();
						break;
					case WALKING_TO_AREA:
						handleWalkingToArea();
						break;
				}
			} catch (Exception e) {
				Microbot.log("[H] Kebbit Meat error: " + e.getMessage());
			}
		}, 0, 600, TimeUnit.MILLISECONDS);

		return true;
	}

	private void handleTrapping() {
		// 0. If too far from hunting area, walk back
		WorldPoint huntingArea = config.huntingLocation().getWorldPoint();
		if (Rs2Player.getWorldLocation().distanceTo(huntingArea) > HUNTING_AREA_RADIUS) {
			Microbot.log("[H] Kebbit Meat: not in hunting area, walking back");
			currentState = State.WALKING_TO_AREA;
			return;
		}

		// 1. Collect from any full traps first
		List<GameObject> fullTraps = Rs2GameObject.getGameObjects(o -> o.getId() == FULL_CLAW_TRAP);
		GameObject fullTrap = fullTraps.stream()
			.min(Comparator.comparingInt(o -> Rs2Player.getWorldLocation().distanceTo(o.getWorldLocation())))
			.orElse(null);

		if (fullTrap != null) {
			if (Rs2GameObject.interact(fullTrap, "Dismantle")) {
				sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
				kebbitsCollected++;
			}
			sleep(300, 600);
			return;
		}

		// 2. Drop all loot except raw wild kebbit and trap-setting tools
		if (hasLootToDrop()) {
			dropLoot();
			return;
		}

		// 3. Bank when only 4 free slots remain
		if (Rs2Inventory.emptySlotCount() <= 4 && Rs2Inventory.contains(RAW_WILD_KEBBIT)) {
			Microbot.log("[H] Kebbit Meat: inventory nearly full, walking to bank");
			currentState = State.WALKING_TO_BANK;
			return;
		}

		// 4. Set a trap only if no active trap already exists (only 1 allowed at a time)
		if (!Rs2Player.isAnimating()) {
			boolean hasActiveTrap = !Rs2GameObject.getGameObjects(o -> o.getId() == ACTIVE_TRAP || o.getId() == INTERMEDIATE_TRAP).isEmpty();
			if (hasActiveTrap) {
				// While waiting, cut a tree if only 1 log left
				if (countLogs() <= 1) {
					currentState = State.CUTTING_LOGS;
				}
				return;
			}

			// Need logs to set a deadfall trap — cut a dead tree if out
			if (!Rs2Inventory.contains(KNIFE)) return; // can't set trap without knife either
			if (!hasLogs()) {
				Microbot.log("[H] Kebbit Meat: no logs, switching to cutting");
				currentState = State.CUTTING_LOGS;
				return;
			}

			List<GameObject> emptyBoulders = Rs2GameObject.getGameObjects(o -> o.getId() == EMPTY_BOULDER);
			GameObject emptyBoulder = emptyBoulders.stream()
				.min(Comparator.comparingInt(o -> Rs2Player.getWorldLocation().distanceTo(o.getWorldLocation())))
				.orElse(null);

			if (emptyBoulder != null) {
				Rs2GameObject.interact(emptyBoulder, "Set-trap");
				sleepUntil(() -> Rs2Player.isAnimating(), 4000);
				sleepUntil(() -> !Rs2Player.isAnimating(), 8000);
			}
		}
	}

	private void handleCuttingLogs() {
		if (hasLogs()) {
			currentState = State.TRAPPING;
			return;
		}

		GameObject deadTree = Rs2GameObject.get(DEAD_TREE, false);
		if (deadTree == null) {
			Microbot.log("[H] Kebbit Meat: no dead tree found nearby");
			return;
		}

		int logsBefore = countLogs();
		Rs2GameObject.interact(deadTree, "Chop down");
		sleepUntil(() -> Rs2Player.isAnimating(), 2000);
		sleepUntil(() -> !Rs2Player.isAnimating() || countLogs() > logsBefore, 10000);

		if (hasLogs()) {
			Microbot.log("[H] Kebbit Meat: logs obtained, resuming trapping");
			currentState = State.TRAPPING;
		}
	}

	private void handleWalkingToBank() {
		Rs2Bank.walkToBank(BankLocation.AUBURNVALE);
		sleepUntil(() -> Rs2Player.distanceTo(BankLocation.AUBURNVALE.getWorldPoint()) < 6, 30000);
		currentState = State.BANKING;
	}

	private void handleBanking() {
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.openBank();
			sleepUntil(() -> Rs2Bank.isOpen(), 5000);
			if (!Rs2Bank.isOpen()) {
				Microbot.log("[H] Kebbit Meat: failed to open bank, retrying");
				return;
			}
		}

		Rs2Bank.depositAll(RAW_WILD_KEBBIT);
		sleepUntil(() -> Rs2Inventory.count(RAW_WILD_KEBBIT) == 0, 3000);

		// Equip Kandarin headgear if available and not already wearing one (double logs bonus)
		if (!isWearingKandarinHeadgear()) {
			for (String headgear : KANDARIN_HEADGEAR) {
				if (Rs2Bank.hasItem(headgear)) {
					Rs2Bank.withdrawAndEquip(headgear);
					Microbot.log("[H] Kebbit Meat: equipped " + headgear + " for double logs");
					sleep(600, 200);
					break;
				}
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 2000);

		currentState = State.WALKING_TO_AREA;
	}

	private void handleWalkingToArea() {
		WorldPoint huntingArea = config.huntingLocation().getWorldPoint();
		Rs2Walker.walkTo(huntingArea);
		sleepUntil(() -> Rs2Player.distanceTo(huntingArea) < 10, 60000);
		currentState = State.TRAPPING;
	}

	private boolean hasLogs() {
		return Rs2Inventory.items().anyMatch(item -> {
			String name = item.getName();
			return name != null && isLogs(name);
		});
	}

	private int countLogs() {
		return (int) Rs2Inventory.items()
			.filter(item -> item.getName() != null && isLogs(item.getName()))
			.count();
	}

	private boolean isWearingKandarinHeadgear() {
		for (String headgear : KANDARIN_HEADGEAR) {
			if (Rs2Equipment.isWearing(headgear)) return true;
		}
		return false;
	}

	private boolean hasLootToDrop() {
		return Rs2Inventory.items().anyMatch(item -> {
			String name = item.getName();
			return name != null && isDroppable(name);
		});
	}

	private void dropLoot() {
		Rs2Inventory.dropAllExcept(item -> {
			String name = item.getName();
			if (name == null) return false;
			return name.equalsIgnoreCase(RAW_WILD_KEBBIT)
				|| name.equalsIgnoreCase(KNIFE)
				|| isLogs(name);
		});
	}

	private boolean isDroppable(String name) {
		return !name.equalsIgnoreCase(RAW_WILD_KEBBIT)
			&& !name.equalsIgnoreCase(KNIFE)
			&& !isLogs(name);
	}

	private boolean isLogs(String name) {
		if (name == null) return false;
		String lower = name.toLowerCase();
		return lower.equals("logs") || lower.endsWith(" logs");
	}

	@Override
	public void shutdown() {
		super.shutdown();
	}
}
