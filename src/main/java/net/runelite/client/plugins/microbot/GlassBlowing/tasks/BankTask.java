package net.runelite.client.plugins.microbot.GlassBlowing.tasks;

import net.runelite.client.plugins.microbot.GlassBlowing.GlassBlowingConfig;
import net.runelite.client.plugins.microbot.GlassBlowing.Task;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class BankTask extends Task {

    GlassBlowingConfig config;

    public BankTask(GlassBlowingConfig glassConfig) {
        super();
        config = glassConfig;
    }


    public boolean accept() {
        return !Rs2Inventory.contains("Molten glass");
    }

    public int execute() {
        if(Rs2Bank.openBank()) {
            sleep(600, 1200);
            if (Rs2Inventory.contains(config.glassProduct().getItemName())) {
                Rs2Bank.depositAll(config.glassProduct().getItemName());
                sleep(600, 1200);
            }
            super.logOnceToChat("Withdrawing molten glass");
            Rs2Bank.withdrawAll("Molten glass");
            sleep(600, 1200);
        }
        Rs2Bank.closeBank();
        while (Rs2Bank.isOpen()){
            sleep(300, 600);
        }
        return 0;
    }
}
