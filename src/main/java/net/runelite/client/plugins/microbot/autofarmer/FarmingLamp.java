package net.runelite.client.plugins.microbot.autofarmer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
/** Spends standard random-event lamps and Books of knowledge on Farming. */
final class FarmingLamp {
    private long retryAfter;
    boolean handle() {
        if (System.currentTimeMillis() < retryAfter) return false;
        int rewardId = Rs2Inventory.hasItem(ItemID.MACRO_GENILAMP) ? ItemID.MACRO_GENILAMP
                : Rs2Inventory.hasItem(ItemID.PATTERN_REWARD) ? ItemID.PATTERN_REWARD : -1;
        if (rewardId < 0) return false;
        if (Rs2Dialogue.isInDialogue()) return false;
        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return true; }
        retryAfter = System.currentTimeMillis() + 60000L;
        int before = Rs2Inventory.itemQuantity(rewardId);
        int xp = Microbot.getClient().getSkillExperience(Skill.FARMING);
        if (!Rs2Inventory.interact(rewardId, rewardId == ItemID.MACRO_GENILAMP ? "Rub" : "Read")) return true;
        if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(240, 0), 5000)) return true;
        Boolean farmingVisible = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget choice = Microbot.getClient().getWidget(InterfaceID.Xpreward.FARMING);
            Widget confirm = Microbot.getClient().getWidget(InterfaceID.Xpreward.CONFIRM);
            return choice != null && !choice.isHidden() && confirm != null && !confirm.isHidden();
        }).orElse(false);
        if (!farmingVisible) { Microbot.log("Lamp: Farming selection unavailable; leaving reward untouched"); return true; }
        if (!Rs2Widget.clickWidget(InterfaceID.Xpreward.FARMING)) return true;
        Global.sleep(900, 1400);
        if (!Rs2Widget.isWidgetVisible(240, 0) || !Rs2Widget.clickWidget(InterfaceID.Xpreward.CONFIRM)) return true;
        Global.sleepUntil(() -> Rs2Dialogue.hasContinue() || Rs2Inventory.itemQuantity(rewardId) < before, 5000);
        for (int i = 0; i < 3 && Rs2Dialogue.hasContinue(); i++) {
            Rs2Dialogue.clickContinue(); Global.sleep(650, 1000);
        }
        boolean used = Global.sleepUntil(() -> Rs2Inventory.itemQuantity(rewardId) < before
                && Microbot.getClient().getSkillExperience(Skill.FARMING) > xp, 5000);
        Microbot.log(used ? "Random-event XP reward used on Farming; XP confirmed" : "Lamp result not confirmed; no immediate retry");
        if (used) retryAfter = System.currentTimeMillis() + 2500L;
        return true;
    }
}
