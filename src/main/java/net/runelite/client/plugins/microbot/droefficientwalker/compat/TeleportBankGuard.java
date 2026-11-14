package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** A bank-close request is not proof that a teleport can use the game canvas. */
public final class TeleportBankGuard {
    private TeleportBankGuard(){}
    public static boolean ready(){
        if(!Rs2Bank.isOpen())return true;
        if(Microbot.getClientThread()!=null&&Microbot.getClientThread().isClientThread())return false;
        Rs2Bank.closeBank();return !Rs2Bank.isOpen();
    }
}
