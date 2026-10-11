package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class AntiVenomAction implements ZulrahAction {

    @Override
    public int order() {
        return 300;
    }

    @Override
    public String key() {
        return "antivenom";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        int poison = Microbot.getClient().getVarpValue(VarPlayerID.POISON);
        return poison >= ZulrahHelpers.VENOM_THRESHOLD && ZulrahHelpers.nextPoisonDamage(poison) > 10;
    }

    @Override
    public Object execute(ZulrahState state) {
        log.debug("Drinking anti poison to reduce venom damage");
        return Rs2Player.drinkAntiPoisonPotion();
    }
}
