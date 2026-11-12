package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroCannonTest {
    @Test public void refillIssuesOneToggleAndWaitsForObservedRotation() throws Exception {
        java.lang.reflect.Field lastClick = DroCannon.class.getDeclaredField("lastClickAt");
        lastClick.setAccessible(true); lastClick.setLong(null, 0);
        Client client = mock(Client.class); ClientThread thread = mock(ClientThread.class);
        WorldPoint position = new WorldPoint(3200, 3200, 0);
        when(client.getVarpValue(VarPlayer.CANNON_COORD)).thenReturn((position.getPlane() << 28) | (position.getX() << 14) | position.getY());
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        GameObject cannon = mock(GameObject.class);
        when(cannon.getId()).thenReturn(6); when(cannon.getWorldLocation()).thenReturn(position.dx(1).dy(1));
        GameObject neighbour = mock(GameObject.class);
        when(neighbour.getId()).thenReturn(6);
        when(neighbour.getWorldLocation()).thenReturn(position.dx(-2).dy(1));
        try (MockedStatic<Microbot> dro = mockStatic(Microbot.class);
             MockedStatic<SceneObjects> objects = mockStatic(SceneObjects.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(() -> Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.DROPCANNON)).thenReturn(4);
            objects.when(() -> SceneObjects.getGameObject(any(Predicate.class))).thenAnswer(call ->
                    ((Predicate<GameObject>) call.getArgument(0)).test(neighbour) ? neighbour
                            : ((Predicate<GameObject>) call.getArgument(0)).test(cannon) ? cannon : null);
            objects.when(() -> SceneObjects.interact(cannon, "Fire")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItemAmount("Steel cannonball", 1, true)).thenReturn(true);
            assertFalse(DroCannon.refill()); assertFalse(DroCannon.refill());
            objects.verify(() -> SceneObjects.interact(cannon, "Fire"), times(1));
            for (int broken : new int[] {5, 14916, 43028}) {
                when(cannon.getId()).thenReturn(broken);
                lastClick.setLong(null, 0L);
                objects.when(() -> SceneObjects.interact(cannon, "Repair")).thenReturn(true);
                assertTrue(DroCannon.isBroken());
                assertTrue(DroCannon.repair());
                assertFalse(DroCannon.repair()); // Retry spacing still applies.
            }
            when(cannon.getId()).thenReturn(6);
            DynamicObject rotating = mock(DynamicObject.class);
            Animation animation = mock(Animation.class);
            when(animation.getId()).thenReturn(net.runelite.api.gameval.AnimationID.MCANNON_N_FIRE);
            when(rotating.getAnimation()).thenReturn(animation);
            when(cannon.getRenderable()).thenReturn(rotating);
            dro.when(() -> Microbot.getVarbitPlayerValue(VarPlayer.CANNON_AMMO)).thenReturn(25);
            assertTrue(DroCannon.refill());
            objects.verify(() -> SceneObjects.interact(cannon, "Fire"), times(1));
            // Reproduce a stopped cannon retaining its last animation and loaded ammo.
            java.lang.reflect.Field motion = DroCannon.class.getDeclaredField("rotation");
            motion.setAccessible(true);
            DroCannonRotation tracker = (DroCannonRotation) motion.get(null);
            tracker.reset();
            tracker.observe(client.getWorld(), cannon.getWorldLocation(), 25,
                    net.runelite.api.gameval.AnimationID.MCANNON_N_FIRE, 0, 0,
                    System.currentTimeMillis() - 10_000L);
            assertFalse(DroCannon.isFiring());
            lastClick.setLong(null, 0L);
            assertFalse(DroCannon.refill());
            assertFalse(DroCannon.refill());
            objects.verify(() -> SceneObjects.interact(cannon, "Fire"), times(2));
            objects.verify(() -> SceneObjects.interact(neighbour, "Fire"), never());
            when(cannon.getId()).thenReturn(43027);
            assertTrue(DroCannon.isPresent());
            tracker.reset();
        }
    }
}
