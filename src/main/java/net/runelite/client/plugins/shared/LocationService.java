package net.runelite.client.plugins.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import javax.inject.Singleton;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@RequiredArgsConstructor
@Singleton
public class LocationService {

    private static final String FEROX_ENCLAVE = "Ferox Enclave";
    private static final int POH_PORTAL_ID = 4525;

    @Inject
    private final Rs2TileObjectCache rs2TileObjectCache;

    public void teleportToFerox() {
        if (!isNearFerox() && Rs2Equipment.isWearing("Ring of dueling", false)) {
            Rs2Equipment.interact(EquipmentInventorySlot.RING, FEROX_ENCLAVE);
            sleepUntil(LocationService::isNearFerox);
        }
    }

    public void teleportToHouse() {
        log.info("Teleporting to house.");
        Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
        sleepUntil(Rs2Player::isAnimating);
        sleepUntil(() -> !Rs2Player.isAnimating());
        sleep(600, 1200);
        log.info("Waiting until POH portal can be found.");
        sleepUntil(() -> rs2TileObjectCache.query()
                .where(IEntity::isReachable)
                .where(object -> object.getId() == POH_PORTAL_ID)
                .nearestOnClientThread(40) != null);
    }

    public static boolean isNearFerox() {
        return Rs2Player.getWorldLocation().distanceTo(BankLocation.FEROX_ENCLAVE.getWorldPoint()) <= 50;
    }
}
