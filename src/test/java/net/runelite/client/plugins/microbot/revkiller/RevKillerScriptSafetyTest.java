package net.runelite.client.plugins.microbot.revkiller;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Player;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevKillerScriptSafetyTest {

    @Test
    void emptyRingSlotIsNotADuelingRing() {
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class)) {
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.RING)).thenReturn(null);

            assertFalse(revKillerScript.isWearingDuelingRing());
        }
    }

    @Test
    void equippedDuelingRingIsDetected() {
        Rs2ItemModel ring = mock(Rs2ItemModel.class);
        when(ring.getName()).thenReturn("Ring of dueling(8)");
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class)) {
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.RING)).thenReturn(ring);

            assertTrue(revKillerScript.isWearingDuelingRing());
        }
    }

    @Test
    void emptyAmmoSlotHasNoAmmo() {
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class)) {
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.AMMO)).thenReturn(null);

            assertFalse(revKillerScript.hasEquippedAmmoAbove(50));
        }
    }

    @Test
    void ammoQuantityIsComparedAgainstThreshold() {
        Rs2ItemModel ammo = mock(Rs2ItemModel.class);
        when(ammo.getQuantity()).thenReturn(51);
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class)) {
            equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.AMMO)).thenReturn(ammo);

            assertTrue(revKillerScript.hasEquippedAmmoAbove(50));
            assertFalse(revKillerScript.hasEquippedAmmoAbove(51));
        }
    }

    @Test
    void playerAttackingUsIsAPkerWithoutReadingRawActorName() {
        Player local = mock(Player.class);
        Rs2PlayerModel attacker = attackerInteractingWith(local);

        assertTrue(isPkerAround(local, attacker));
        verify(local, never()).getName();
    }

    @Test
    void playerAttackingSomeoneElseIsNotAPker() {
        Player local = mock(Player.class);
        Actor other = mock(Actor.class);
        Rs2PlayerModel attacker = attackerInteractingWith(other);

        assertFalse(isPkerAround(local, attacker));
        verify(other, never()).getName();
    }

    private static Rs2PlayerModel attackerInteractingWith(Actor target) {
        Rs2PlayerModel attacker = mock(Rs2PlayerModel.class);
        when(attacker.getName()).thenReturn("Attacker");
        when(attacker.getInteracting()).thenReturn(target);
        return attacker;
    }

    private static boolean isPkerAround(Player local, Rs2PlayerModel attacker) {
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(local);
        Map<KitType, String> gear = new EnumMap<>(KitType.class);
        gear.put(KitType.WEAPON, "Rune scimitar");
        gear.put(KitType.TORSO, "Rune platebody");
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            microbot.when(Microbot::getClient).thenReturn(client);
            pvp.when(Rs2Pvp::isInWilderness).thenReturn(true);
            player.when(Rs2Player::getPlayersInCombatLevelRange).thenReturn(List.of(attacker));
            player.when(() -> Rs2Player.getPlayerEquipmentNames(attacker)).thenReturn(gear);

            return new revKillerScript().isPkerAround();
        }
    }
}
