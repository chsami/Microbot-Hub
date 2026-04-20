package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class WealthyCitizenThiever {

    private static final String NPC_NAME = "Wealthy citizen";
    private static final String COIN_POUCH = "Coin pouch";
    private static final int COIN_POUCH_ID = 28822;

    @Getter
    private String status = "Idle";

    private boolean pickpocketing = false;

    public void reset() {
        status = "Idle";
        pickpocketing = false;
    }

    public boolean tick(LeaguesToolkitConfig config) {
        // Check if inventory is full (excluding coin pouches which stack)
        if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(COIN_POUCH)) {
            status = "Inventory full";
            return false;
        }

        // Open coin pouches if at threshold (use itemQuantity for stack count)
        int pouchCount = Rs2Inventory.itemQuantity(COIN_POUCH_ID);
        if (pouchCount >= config.coinPouchThreshold()) {
            status = "Opening " + pouchCount + " coin pouches";
            pickpocketing = false;
            Rs2Inventory.interact(COIN_POUCH, "Open-all");
            sleepUntil(() -> !Rs2Inventory.hasItem(COIN_POUCH), 3000);
            sleep(200, 400);
            return true;
        }

        // If already pickpocketing (Larcenist auto-repickpockets), just monitor
        if (pickpocketing && (Rs2Player.isAnimating() || Rs2Player.isInteracting())) {
            status = "Pickpocketing... (" + pouchCount + "/" + config.coinPouchThreshold() + " pouches)";
            return true;
        }

        // Not actively pickpocketing — click the NPC once to start
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                .where(n -> {
                    String name = Microbot.getClientThread()
                            .runOnClientThreadOptional(n::getName).orElse(null);
                    return NPC_NAME.equalsIgnoreCase(name);
                })
                .nearest();

        if (npc == null) {
            status = "No Wealthy citizen found nearby";
            pickpocketing = false;
            return true;
        }

        status = "Starting pickpocket on Wealthy citizen";
        Rs2Npc.pickpocket(npc.getNpc());
        pickpocketing = true;
        sleep(600, 900);

        return true;
    }
}
