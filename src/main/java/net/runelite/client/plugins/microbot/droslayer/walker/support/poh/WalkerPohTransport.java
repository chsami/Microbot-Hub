package net.runelite.client.plugins.microbot.droslayer.walker.support.poh;

import net.runelite.client.plugins.microbot.util.poh.PohTransport;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;
import net.runelite.client.plugins.microbot.util.poh.data.MountedGlory;
import net.runelite.client.plugins.microbot.util.poh.data.MountedMythical;
import net.runelite.client.plugins.microbot.util.poh.data.MountedDigsite;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.api.gameval.ObjectID;

/**
 * Represents a transport mechanism using the Player-Owned House (POH) teleportation system.
 * This class extends the base Transport class and provides specific implementations
 * for POH teleportation.
 */
public class WalkerPohTransport extends PohTransport {

    @Getter
    private final PohTeleport teleport;

    public WalkerPohTransport(WorldPoint exitPortalPoint, PohTeleport teleport) {
        super(exitPortalPoint, teleport);
        this.teleport = teleport;
        if (teleport == net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox.FORTIS_COLOSSEUM) {
            getVarplayers().add(new net.runelite.client.plugins.microbot.shortestpath.TransportVarPlayer(
                    net.runelite.api.gameval.VarPlayerID.COLOSSEUM_GLORY, 11999,
                    net.runelite.client.plugins.microbot.shortestpath.TransportVarPlayer.Operator.GREATER_THAN));
        } else if (teleport == net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox.TEARS_OF_GUTHIX) {
            getQuests().put(net.runelite.api.Quest.TEARS_OF_GUTHIX, net.runelite.api.QuestState.FINISHED);
        } else if (teleport == net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox.MISCELLANIA) {
            getQuests().put(net.runelite.api.Quest.THRONE_OF_MISCELLANIA, net.runelite.api.QuestState.FINISHED);
        } else if (teleport == net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox.DONDAKAN) {
            getQuests().put(net.runelite.api.Quest.BETWEEN_A_ROCK, net.runelite.api.QuestState.FINISHED);
        }
    }

    /**
     * Executes the Transport's PoH teleportation action.
     *
     * @return true on successful teleportation
     */
    public boolean execute() {
        return net.runelite.client.plugins.microbot.droslayer.walker.MaxPohSupport.executeFacility(teleport);
    }

    @Override
    public WorldPoint getDestination() {
        if (teleport == net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox.FARMING_GUILD)
            return net.runelite.client.plugins.microbot.util.player.Rs2Player.getBoostedSkillLevel(net.runelite.api.Skill.FARMING) >= 45
                    ? new WorldPoint(1248, 3725, 0) : new WorldPoint(1248, 3719, 0);
        return teleport.getDestination();
    }

    private static String portalAction(PohPortal portal) {
        switch (portal) {
            case VARROCK: return "Varrock";
            case GRAND_EXCHANGE: return "Grand Exchange";
            case CAMELOT: return "Camelot";
            case WATCHTOWER: return "Watchtower";
            default: return "Enter";
        }
    }

    @Override
    public int getObjectId() {
        if (teleport instanceof NexusPortal)
            return NexusPortal.PORTAL_IDS[0];
        if (teleport instanceof net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox)
            return ObjectID.POH_JEWELLERY_BOX_1;
        if (teleport instanceof MountedGlory) return ObjectID.POH_TROPHY_AMULETOFGLORY_4;
        if (teleport instanceof MountedMythical) return ((MountedMythical) teleport).getObjectId();
        if (teleport instanceof MountedDigsite) return MountedDigsite.IDS[0];
        if (teleport instanceof MountedXerics) return MountedXerics.IDS[0];
        return teleport instanceof PohPortal ? ((PohPortal) teleport).getObjectIds()[0] : super.getObjectId();
    }

    @Override
    public String getAction() {
        if (teleport instanceof NexusPortal)
            return "Teleport menu";
        if (teleport instanceof net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox)
            return "Teleport menu";
        if (teleport instanceof MountedGlory) return ((MountedGlory) teleport).getDestinationName();
        if (teleport instanceof MountedMythical) return "Teleport";
        if (teleport instanceof MountedDigsite) return ((MountedDigsite) teleport).getDestinationName();
        if (teleport instanceof MountedXerics) return ((MountedXerics) teleport).getDestinationName();
        return teleport instanceof PohPortal ? portalAction((PohPortal) teleport) : super.getAction();
    }

}
