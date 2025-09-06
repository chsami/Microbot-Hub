package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class LunarBucketsScript extends Script {
    private LunarBucketsState state = LunarBucketsState.STARTUP;
    private final LunarBucketsPlugin plugin;

    private static final Spell HUMIDIFY_SPELL = new Spell() {
        @Override public MagicAction getMagicAction() { return MagicAction.HUMIDIFY; }
        @Override public HashMap<Runes, Integer> getRequiredRunes() {
            return new HashMap<>() {{
                put(Runes.ASTRAL, 1);
                put(Runes.WATER, 3);
                put(Runes.FIRE, 1);
            }};
        }
        @Override public Rs2Spellbook getSpellbook() { return Rs2Spellbook.LUNAR; }
        @Override public int getRequiredLevel() { return 68; }
    };

    @Inject
    public LunarBucketsScript(LunarBucketsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(LunarBucketsConfig config) {
        state = LunarBucketsState.STARTUP;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                switch (state) {
                    case STARTUP:
                        Microbot.status = "Startup";
                        handleStartup();
                        break;
                    case BANKING:
                        Microbot.status = "Banking";
                        handleBanking();
                        break;
                    case CASTING:
                        Microbot.status = "Casting";
                        handleCasting();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        state = LunarBucketsState.STARTUP;
        super.shutdown();
    }

    private boolean hasSteamStaffEquipped() {
        return Rs2Equipment.isWearing(ItemID.STEAM_BATTLESTAFF) ||
			Rs2Equipment.isWearing(ItemID.MYSTIC_STEAM_BATTLESTAFF) ||
			Rs2Equipment.isWearing(ItemID.TWINFLAME_STAFF);
    }

    private void handleStartup() {
        if (!hasSteamStaffEquipped()) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                return;
            }
            if (Rs2Bank.hasItem(ItemID.STEAM_BATTLESTAFF)) {
				Rs2Bank.withdrawAndEquip(ItemID.STEAM_BATTLESTAFF);
            } else if (Rs2Bank.hasItem(ItemID.MYSTIC_STEAM_BATTLESTAFF)) {
				Rs2Bank.withdrawAndEquip(ItemID.MYSTIC_STEAM_BATTLESTAFF);
            } else if (Rs2Bank.hasItem(ItemID.TWINFLAME_STAFF)) {
				Rs2Bank.withdrawAndEquip(ItemID.TWINFLAME_STAFF);
			}
			else {
                Microbot.showMessage("No steam staff in bank");
                shutdown();
                return;
            }
            return;
        }

        if (!Rs2Inventory.hasItem(ItemID.ASTRALRUNE)) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                return;
            }
            if (Rs2Bank.hasItem(ItemID.ASTRALRUNE)) {
                Rs2Bank.withdrawAll(ItemID.ASTRALRUNE);
                sleepUntil(() -> Rs2Inventory.hasItem(ItemID.ASTRALRUNE));
            } else {
                Microbot.showMessage("No astral runes in bank");
                shutdown();
                return;
            }
            return;
        }
        state = LunarBucketsState.BANKING;
    }

    private void handleBanking() {
		if (!Rs2Bank.openBank()) return;

        Rs2Bank.depositAllExcept(ItemID.ASTRALRUNE);
		sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(ItemID.BUCKET_WATER));

		if (Rs2Bank.hasItem(ItemID.BUCKET_EMPTY)) {
			Rs2Bank.withdrawAll(ItemID.BUCKET_EMPTY);
			sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(ItemID.BUCKET_EMPTY));
		} else {
			Microbot.showMessage("No more buckets to fill.");
			shutdown();
			return;
		}

        Rs2Bank.closeBank();
        state = LunarBucketsState.CASTING;
    }

    private void handleCasting() {
		Rs2Tab.switchTo(InterfaceTab.MAGIC);

        Rs2Magic.cast(HUMIDIFY_SPELL);

        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(ItemID.BUCKET_WATER));

        plugin.recordCast();

        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();

        state = LunarBucketsState.BANKING;
    }
}
