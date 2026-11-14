package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;

import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

public class DroCannon {
    private static long lastClickAt;
    private static final DroCannonRotation rotation = new DroCannonRotation();

    private static GameObject owned(boolean broken) {
        WorldPoint position = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int coord = Microbot.getClient().getVarpValue(VarPlayer.CANNON_COORD);
            return coord > 0 ? WorldPoint.fromCoord(coord) : null;
        }).orElse(null);
        if (position == null || Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.DROPCANNON) != 4) return null;
        return SceneObjects.getGameObject(o ->
                (broken ? o.getId() == 5 || o.getId() == 14916 || o.getId() == 43028 : o.getId() == 6 || o.getId() == 43027)
                        // OWNEDMCANNON is the south-west tile of its 3x3 footprint;
                        // GameObject reports the centre. A radius can select a neighbour.
                        && position.dx(1).dy(1).equals(o.getWorldLocation()));
    }

    public static boolean repair() {
        GameObject cannon = owned(true);
        return cannon != null && click(cannon, "Repair");
    }

    public static boolean isBroken() { return owned(true) != null; }
    public static boolean isPresent() { return owned(false) != null || owned(true) != null; }

    public static boolean isFiring() {
        GameObject cannon = owned(false);
        return observedRotation(cannon, Microbot.getVarbitPlayerValue(VarPlayer.CANNON_AMMO));
    }

    public static boolean refill() { return refill(15); }

    public static boolean refill(int threshold) {
        GameObject cannon = owned(false);
        if (cannon == null) return false;
        int ammo = Microbot.getVarbitPlayerValue(VarPlayer.CANNON_AMMO);
        boolean rotating = observedRotation(cannon, ammo);
        if (rotating && ammo > threshold) return true;
        boolean supplies = Rs2Inventory.hasItemAmount("Steel cannonball", 1, true)
                || Rs2Inventory.hasItemAmount("Cannonball", 1, true)
                || Rs2Inventory.hasItemAmount("Granite cannonball", 1, true);
        if (!rotating && ammo == 0 && !supplies) return false;
        if (rotating && !supplies) return true;
        // Fire toggles rotation: issue one click, then let the next tick observe the result.
        click(cannon, "Fire");
        return rotating && ammo > 0;
    }

    private static boolean observedRotation(GameObject cannon, int ammo) {
        if (cannon == null) {
            rotation.reset();
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!(cannon.getRenderable() instanceof DynamicObject)) {
                rotation.reset();
                return false;
            }
            DynamicObject dynamic = (DynamicObject) cannon.getRenderable();
            net.runelite.api.Animation animation = dynamic.getAnimation();
            return rotation.observe(Microbot.getClient().getWorld(), cannon.getWorldLocation(), ammo,
                    animation == null ? -1 : animation.getId(), dynamic.getAnimFrame(), dynamic.getAnimCycle(),
                    System.currentTimeMillis());
        }).orElse(false);
    }

    /** Read-only evidence: animation references alone do not establish motion. */
    public static String diagnostic() {
        GameObject cannon = owned(false);
        if (cannon == null) cannon = owned(true);
        GameObject observed = cannon;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int ammo = Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO);
            if (observed == null) return "present=false loaded=" + ammo;
            DynamicObject dynamic = observed.getRenderable() instanceof DynamicObject
                    ? (DynamicObject) observed.getRenderable() : null;
            net.runelite.api.Animation animation = dynamic == null ? null : dynamic.getAnimation();
            return "object=" + observed.getId() + " tile=" + observed.getWorldLocation()
                    + " loaded=" + ammo + " animation=" + (animation == null ? -1 : animation.getId())
                    + " frame=" + (dynamic == null ? -1 : dynamic.getAnimFrame())
                    + " cycle=" + (dynamic == null ? -1 : dynamic.getAnimCycle());
        }).orElse("unavailable");
    }

    private static synchronized boolean click(GameObject cannon, String action) {
        if (System.currentTimeMillis() - lastClickAt < 3000L) return false;
        lastClickAt = System.currentTimeMillis();
        return SceneObjects.interact(cannon, action);
    }
}
