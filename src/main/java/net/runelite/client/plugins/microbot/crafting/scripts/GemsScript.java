package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.BoltTips;
import net.runelite.client.plugins.microbot.crafting.enums.Gems;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GemsScript extends Script implements ICraftingScript {

    public static double version = 1.0;

    @Override
    public String getName() {
        return "Gems";
    }

    @Override
    public String getVersion() {
        return String.valueOf(version);
    }

    @Override
    public String getState() {
        return Microbot.status;
    }

    @Override
    public Map<String, String> getCustomProperties() {
        return Collections.emptyMap();
    }


    public boolean run(CraftingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!Rs2Player.getSkillRequirement(Skill.CRAFTING, config.gemType().getLevelRequired())) {
                    Microbot.showMessage("Crafting level too low to craft " + config.gemType().getName());
                    shutdown();
                    return;
                }
                final boolean amethyst = (config.gemType().getName().contains("Amethyst"));
                final String gemName = config.gemType().getName();
                final String uncutGemName = "uncut " + gemName;
                final BoltTips boltTip = config.fletchIntoBoltTips() ? getBoltTip(config.gemType()) : null;
                final boolean fletchCutGems = !amethyst && boltTip != null
                        && Rs2Player.getSkillRequirement(Skill.FLETCHING, boltTip.getFletchingLevelRequired());

                if ((!amethyst && !hasGemsToProcess(Rs2Inventory.hasItem(uncutGemName), Rs2Inventory.hasItem(gemName, true), fletchCutGems))
                        || !Rs2Inventory.hasItem("chisel") || (amethyst && !Rs2Inventory.hasItem(21347))) {
                    Microbot.status = "BANKING";
                    Rs2Bank.openBank();
                    if (Rs2Bank.isOpen()) {
                        Rs2Bank.depositAll("crushed gem");
                        if (fletchCutGems) {
                            Rs2Bank.depositAll(boltTip.getBoltTipName(), true);
                            sleepUntil(() -> !Rs2Inventory.hasItem(boltTip.getBoltTipName(), true) && !Rs2Inventory.hasItem("crushed gem"));
                        } else {
                            Rs2Bank.depositAll(gemName);
                            sleepUntil(() -> !Rs2Inventory.hasItem(gemName) && !Rs2Inventory.hasItem("crushed gem"));
                        }
                        if (Rs2Bank.hasItem(uncutGemName)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, uncutGemName);
                        } else if (fletchCutGems && (Rs2Bank.hasItem(gemName, true) || Rs2Inventory.hasItem(gemName, true))) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, gemName, true);
                        } else if (amethyst && Rs2Bank.hasItem(21347)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(21347);
                        }
                        else
                        {
                            Microbot.showMessage("You've ran out of materials!");
                            shutdown();
                            }
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                    }
                } else {
                    if (amethyst) {
                        Microbot.status = "CUTTING GEMS";
                        Rs2Inventory.use("chisel");
                        Rs2Inventory.use(21347);
                        Rs2Widget.sleepUntilHasWidgetText("How many do you wish to make?", 270, 5, false, 5000);
                        Rs2Widget.clickWidget(config.gemType().getName(),true);
                        Rs2Widget.sleepUntilHasNotWidgetText("How many do you wish to make?", 270, 5, false, 5000);
                        sleep(3000);
                        sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(21347), 30000);
                    } else {
                        if (Rs2Inventory.hasItem(uncutGemName)) {
                            Microbot.status = "CUTTING GEMS";
                            Rs2Inventory.use("chisel");
                            Rs2Inventory.use(uncutGemName);
                            sleep(600);
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            sleep(4000);
                            sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(uncutGemName), 30000);
                        }

                        if (fletchCutGems && Rs2Inventory.hasItem(gemName, true) && Rs2Inventory.hasItem("chisel")) {
                            Microbot.status = "FLETCHING BOLT TIPS";
                            Rs2Inventory.use("chisel");
                            Rs2Inventory.interact(gemName, "Use", true);
                            sleep(600);
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            sleep(4000);
                            sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(gemName, true), 30000);
                        }
                    }
                }
                Microbot.status = "IDLE";
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    static BoltTips getBoltTip(Gems gem) {
        if (gem == null || gem == Gems.NONE) return null;
        return Arrays.stream(BoltTips.values())
                .filter(boltTip -> boltTip.name().equals(gem.name()))
                .findFirst()
                .orElse(null);
    }

    static boolean hasGemsToProcess(boolean hasUncutGems, boolean hasCutGems, boolean fletchCutGems) {
        return hasUncutGems || (fletchCutGems && hasCutGems);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.status = "IDLE";
    }
}