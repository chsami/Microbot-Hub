package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.ChatMessageType;
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

    private boolean fletchUnavailableNoticeShown;
    private boolean bankCutGemsNoticeShown;

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
        fletchUnavailableNoticeShown = false;
        bankCutGemsNoticeShown = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                final boolean amethyst = (config.gemType().getName().contains("Amethyst"));
                final String gemName = config.gemType().getName();
                final String uncutGemName = "uncut " + gemName;
                final BoltTips boltTip = config.fletchIntoBoltTips() ? getBoltTip(config.gemType()) : null;
                final boolean fletchCutGems = !amethyst && boltTip != null
                        && Rs2Player.getSkillRequirement(Skill.FLETCHING, boltTip.getFletchingLevelRequired());
                final int craftingLevel = config.gemType().getLevelRequired();
                final boolean canCut = Rs2Player.getSkillRequirement(Skill.CRAFTING, craftingLevel);
                if (shouldStopForLevels(canCut, fletchCutGems)) {
                    Microbot.showMessage(craftingLevelMessage(gemName, craftingLevel, amethyst, config.fletchIntoBoltTips() ? boltTip : null));
                    shutdown();
                    return;
                }
                if (fletchNoticeNeeded(config.gemType(), config.fletchIntoBoltTips(), amethyst, fletchCutGems) && !fletchUnavailableNoticeShown) {
                    fletchUnavailableNoticeShown = true;
                    showGameMessage(fletchUnavailableMessage(gemName, boltTip));
                }

                if ((!amethyst && !hasGemsToProcess(canCut && Rs2Inventory.hasItem(uncutGemName), Rs2Inventory.hasItem(gemName, true), fletchCutGems))
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
                        if (!canCut && Rs2Inventory.hasItem(uncutGemName)) {
                            Rs2Bank.depositAll(uncutGemName);
                            sleepUntil(() -> !Rs2Inventory.hasItem(uncutGemName));
                        }
                        if (canCut && Rs2Bank.hasItem(uncutGemName)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, uncutGemName);
                        } else if (fletchCutGems && (Rs2Bank.hasItem(gemName, true) || Rs2Inventory.hasItem(gemName, true))) {
                            if (!bankCutGemsNoticeShown && Rs2Bank.hasItem(gemName, true)) {
                                bankCutGemsNoticeShown = true;
                                showGameMessage(bankCutGemsMessage(gemName, canCut, craftingLevel));
                            }
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, gemName, true);
                            if (!sleepUntil(() -> Rs2Inventory.hasItem(gemName, true))) {
                                Microbot.showMessage(noRoomForCutGemsMessage(gemName));
                                shutdown();
                            }
                        } else if (amethyst && Rs2Bank.hasItem(21347)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(21347);
                        }
                        else
                        {
                            Microbot.showMessage(canCut ? outOfMaterialsMessage(gemName, amethyst, fletchCutGems) : outOfCutGemsMessage(gemName, craftingLevel));
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
                        if (canCut && Rs2Inventory.hasItem(uncutGemName)) {
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

    static String fletchUnavailableMessage(String gemName, BoltTips boltTip) {
        if (boltTip == null) {
            return gemName + " cannot be fletched into bolt tips, only cutting gems.";
        }
        return "Fletching level " + boltTip.getFletchingLevelRequired() + " needed for " + boltTip.getGemName() + " bolt tips, only cutting gems.";
    }

    static boolean fletchNoticeNeeded(Gems gem, boolean fletchIntoBoltTips, boolean amethyst, boolean fletchCutGems) {
        return fletchIntoBoltTips && gem != null && gem != Gems.NONE && !amethyst && !fletchCutGems;
    }

    static String craftingLevelMessage(String gemName, int craftingLevel, boolean amethyst, BoltTips unavailableBoltTip) {
        String message = "Crafting level " + craftingLevel + " needed to " + (amethyst ? "craft " : "cut ") + gemName + ".";
        if (unavailableBoltTip != null) {
            message += " Fletching level " + unavailableBoltTip.getFletchingLevelRequired() + " needed for " + unavailableBoltTip.getGemName() + " bolt tips.";
        }
        return message + " Stopping.";
    }

    static String bankCutGemsMessage(String gemName, boolean canCut, int craftingLevel) {
        if (canCut) {
            return "No uncut " + gemName + " left, fletching cut " + gemName + " from your bank into bolt tips.";
        }
        return "Your Crafting level is below " + craftingLevel + ", so uncut " + gemName + " is skipped. Fletching cut " + gemName + " from your bank into bolt tips.";
    }

    static String outOfCutGemsMessage(String gemName, int craftingLevel) {
        return "Out of cut " + gemName + " in your bank. Crafting level " + craftingLevel + " is needed to cut uncut " + gemName + ", so add more cut " + gemName + " to your bank to keep fletching. Stopping.";
    }

    static String noRoomForCutGemsMessage(String gemName) {
        return "No room for cut " + gemName + " in your inventory. Make room in your inventory and start again. Stopping.";
    }

    static boolean shouldStopForLevels(boolean canCut, boolean fletchCutGems) {
        return !canCut && !fletchCutGems;
    }

    static String outOfMaterialsMessage(String gemName, boolean amethyst, boolean fletchCutGems) {
        if (amethyst) {
            return "Out of amethyst in your bank. Stopping.";
        }
        if (fletchCutGems) {
            return "Out of uncut and cut " + gemName + " in your bank. Stopping.";
        }
        return "Out of uncut " + gemName + " in your bank. Stopping.";
    }

    private static void showGameMessage(String message) {
        Microbot.getClientThread().invokeLater(() ->
                Microbot.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.status = "IDLE";
    }
}