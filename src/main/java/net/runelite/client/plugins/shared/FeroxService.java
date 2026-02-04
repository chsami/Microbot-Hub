package net.runelite.client.plugins.shared;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class FeroxService {

    private static final String FEROX_ENCLAVE = "Ferox Enclave";
    private static final String DRINK = "Drink";
    private static final String POOL_OF_REFRESHMENT = "Pool of Refreshment";
    private static final String RING_OF_DUELING = "Ring of dueling";

    private final Rs2TileObjectCache rs2TileObjectCache;

    @Inject
    public FeroxService(Rs2TileObjectCache rs2TileObjectCache) {
        this.rs2TileObjectCache = rs2TileObjectCache;
    }

    public void teleportToFerox() {
        if (!isNearFerox() && Rs2Equipment.isWearing(RING_OF_DUELING, false)) {
            Rs2Equipment.interact(EquipmentInventorySlot.RING, FEROX_ENCLAVE);
            sleepUntil(FeroxService::isNearFerox);
        }
    }

    public static boolean isNearFerox() {
        return Rs2Player.getWorldLocation().distanceTo(BankLocation.FEROX_ENCLAVE.getWorldPoint()) <= 50;
    }

    public void restoreAtFerox() {
        teleportToFerox();
        drinkFromPoolOfRefreshment();
        sleep(1200, 1800);
    }

    public void drinkFromPoolOfRefreshment() {
        Optional<Rs2TileObjectModel> poolOfRefreshment = getPoolOfRefreshmentObject();

        if (poolOfRefreshment.isPresent()) {
            poolOfRefreshment.get().click(DRINK);
            sleepUntil(this::isPrayerAndRunSufficient, 15000); //takes a bit longer to run to the pool
        }  else {
            log.info("Pool of Refreshment not found.");
        }
    }

    private boolean isPrayerAndRunSufficient() {
        return Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= Rs2Player.getRealSkillLevel(Skill.PRAYER) - 1
                && Rs2Player.getRunEnergy() >= 90;
    }

    private Optional<Rs2TileObjectModel> getPoolOfRefreshmentObject() {
        return Optional.ofNullable(
                rs2TileObjectCache
                        .query()
                        .withName(POOL_OF_REFRESHMENT)
                        .nearestOnClientThread(60)
        );
    }
}
