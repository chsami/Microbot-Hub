package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

public final class ArenaDropPolicy {
    private ArenaDropPolicy() { }
    public static boolean insideArena(Client client) {
        if (client==null || client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null
            || client.getTopLevelWorldView()==null || !client.getTopLevelWorldView().isInstance()
            || client.getBoostedSkillLevel(Skill.HITPOINTS)<=0) return false;
        WorldPoint p=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        return p!=null && (InfernoBoundary.templateTile(p.getX(),p.getY(),p.getPlane())
            || p.getPlane()==0 && p.getX()>=1792 && p.getX()<1856 && p.getY()>=3072 && p.getY()<3136);
    }
    public static boolean drop(Client client, BooleanSupplier allowed, int slot, int itemId) {
        if (slot<0 || slot>=28 || itemId<=0 || !allowed.getAsBoolean()) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!allowed.getAsBoolean() || !insideArena(client)) return false;
            var item=Rs2Inventory.getItemInSlot(slot);
            if (item==null || item.getId()!=itemId) return false;

            return allowed.getAsBoolean() && ArenaMenuInput.inventory(client,slot,itemId,"Drop");
        }).orElse(false);
    }
}
