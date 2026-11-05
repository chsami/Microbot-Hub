package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class SpinFlaxScript extends Script {

    private MagicState state;
    private int castsDone;

    private final AIOMagicPlugin plugin;

    @Inject
    public SpinFlaxScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.CASTING_SPIN_FLAX);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (!Rs2Player.getSkillRequirement(Skill.MAGIC, 76)) {
                    Microbot.showMessage("Spin Flax requires 76 Magic.");
                    shutdown();
                    return;
                }

                if (state == null) {
                    state = MagicState.BANKING;
                    castsDone = 0;
                }

                switch (state) {
                    case BANKING:
                        Microbot.status = "Banking: prepare 25 flax";
                        boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        Rs2Bank.depositAll(ItemID.BOW_STRING);
                        Rs2Inventory.waitForInventoryChanges(1200);

                        boolean hasAstralInv = Rs2Inventory.hasItem(Runes.ASTRAL.getItemId());
                        boolean hasNatureInv = Rs2Inventory.hasItem(Runes.NATURE.getItemId());
                        boolean hasAirInv = Rs2Inventory.hasItem(Runes.AIR.getItemId());

                        boolean hasAstralBank = Rs2Bank.hasItem(Runes.ASTRAL.getItemId());
                        boolean hasNatureBank = Rs2Bank.hasItem(Runes.NATURE.getItemId());
                        boolean hasAirBank = Rs2Bank.hasItem(Runes.AIR.getItemId());

                        if (!hasAstralBank && !hasAstralInv) { Microbot.showMessage("Astral runes not found in bank."); shutdown(); return; }
                        if (!hasNatureBank && !hasNatureInv) { Microbot.showMessage("Nature runes not found in bank."); shutdown(); return; }
                        if (!hasAirBank && !hasAirInv) { Microbot.showMessage("Air runes not found in bank."); shutdown(); return; }

                        if (hasAstralBank) { if (!Rs2Bank.withdrawAll(Runes.ASTRAL.getItemId())) return; Rs2Inventory.waitForInventoryChanges(1200); }
                        if (hasNatureBank) { if (!Rs2Bank.withdrawAll(Runes.NATURE.getItemId())) return; Rs2Inventory.waitForInventoryChanges(1200); }
                        if (hasAirBank)    { if (!Rs2Bank.withdrawAll(Runes.AIR.getItemId())) return;    Rs2Inventory.waitForInventoryChanges(1200); }

                        if (!Rs2Bank.hasBankItem(ItemID.FLAX, 1)) {
                            Microbot.showMessage("No flax in bank.");
                            shutdown();
                            return;
                        }

                        int empty = Rs2Inventory.emptySlotCount();
                        if (empty < 25) {
                            Rs2Bank.depositAll(ItemID.BOW_STRING);
                            Rs2Inventory.waitForInventoryChanges(1200);
                            empty = Rs2Inventory.emptySlotCount();
                            if (empty < 25) return; // wait next tick
                        }

                        if (!Rs2Bank.withdrawX(ItemID.FLAX, 25)) return;
                        Rs2Inventory.waitForInventoryChanges(1200);

                        Rs2Bank.closeBank();
                        castsDone = 0;
                        state = MagicState.CASTING;
                        break;

                    case CASTING:
                        Microbot.status = "Casting: Spin Flax (" + castsDone + "/5)";

                        if (!Rs2Inventory.hasItem(ItemID.FLAX)) {
                            state = MagicState.BANKING;
                            break;
                        }

                        if (!Rs2Magic.hasRequiredRunes(Rs2Spells.SPIN_FLAX)) {
                            Microbot.showMessage("Out of runes for Spin Flax");
                            shutdown();
                            return;
                        }

                        if (castsDone >= 5) {
                            state = MagicState.BANKING;
                            break;
                        }

                        if (!Rs2Magic.cast(Rs2Spells.SPIN_FLAX)) {
                            Microbot.log("Unable to cast Spin Flax");
                            state = MagicState.BANKING;
                            break;
                        }
                        Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
                        castsDone++;
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
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
