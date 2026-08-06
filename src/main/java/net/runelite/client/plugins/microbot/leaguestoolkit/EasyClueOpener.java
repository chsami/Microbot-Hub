package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class EasyClueOpener {

    private static final int SCROLL_BOX_EASY = 24362;
    private static final int CLUE_SCROLL_DIG = 29853;
    private static final int SPADE = 952;

    @Getter
    private String status = "Idle";

    public void reset() {
        status = "Idle";
    }

    public boolean tick(LeaguesToolkitConfig config) {
        if (!Rs2Inventory.hasItem(SPADE)) {
            status = "No spade in inventory";
            return false;
        }

        if (!Rs2Inventory.hasItem(SCROLL_BOX_EASY) && !hasClueScroll()) {
            status = "No scroll boxes or clues left";
            return false;
        }

        int digMin = Math.min(config.clueDigDelayMin(), config.clueDigDelayMax());
        int digMax = Math.max(config.clueDigDelayMin(), config.clueDigDelayMax());
        int actionDelay = config.clueActionDelay();

        if (hasClueScroll()) {
            if (Rs2Inventory.hasItem(CLUE_SCROLL_DIG)) {
                status = "Digging";
                Rs2Inventory.interact(SPADE, "Dig");
                sleep(Rs2Random.between(digMin, digMax));
                sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
                sleep(Rs2Random.between(actionDelay / 2, actionDelay));
                return true;
            } else {
                status = "Dropping non-dig clue";
                dropNonDigClue();
                sleep(Rs2Random.between(actionDelay / 2, actionDelay));
                return true;
            }
        }

        if (Rs2Inventory.hasItem(SCROLL_BOX_EASY)) {
            status = "Opening scroll box";
            Rs2Inventory.interact(SCROLL_BOX_EASY, "Open");
            sleep(Rs2Random.between(actionDelay, actionDelay + 200));
            sleepUntil(this::hasClueScroll, 3000);
            return true;
        }

        status = "Waiting...";
        return true;
    }

    private boolean hasClueScroll() {
        return Rs2Inventory.hasItem("Clue scroll (easy)");
    }

    private void dropNonDigClue() {
        Rs2Inventory.items(item ->
                item.getName() != null
                        && item.getName().toLowerCase().contains("clue scroll")
                        && item.getId() != CLUE_SCROLL_DIG
        ).findFirst().ifPresent(item -> {
            log.info("[EasyClue] Dropping non-dig clue ID={}", item.getId());
            Rs2Inventory.interact(item, "Drop");
        });
    }
}
