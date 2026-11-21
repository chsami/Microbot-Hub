package net.runelite.client.plugins.microbot.droefficientwalker.support.poh;

import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.worldmap.TeleportLocationData;
import net.runelite.client.plugins.worldmap.TeleportType;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.api.gameval.VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE;


/**
 *
 */
@Getter
@RequiredArgsConstructor
public enum NexusPortal implements PohTeleport {
    VARROCK(TeleportType.NORMAL_MAGIC, "Varrock", TeleportLocationData.VARROCK.getLocation()),
    VARROCK_GE(TeleportType.NORMAL_MAGIC, "Grand Exchange", TeleportLocationData.VARROCK_GE.getLocation()),
    LUMBRIDGE(TeleportType.NORMAL_MAGIC, "Lumbridge", TeleportLocationData.LUMBRIDGE.getLocation()),
    FALADOR(TeleportType.NORMAL_MAGIC, "Falador", TeleportLocationData.FALADOR.getLocation()),
    CAMELOT(TeleportType.NORMAL_MAGIC, "Camelot", TeleportLocationData.CAMELOT.getLocation()),
    ARDOUGNE(TeleportType.NORMAL_MAGIC, "Ardougne", TeleportLocationData.ARDOUGNE.getLocation()),
    WATCHTOWER(TeleportType.NORMAL_MAGIC, "Watchtower", TeleportLocationData.WATCHTOWER.getLocation()),
    MARIM(TeleportType.NORMAL_MAGIC, "Marim", TeleportLocationData.APE_ATOLL.getLocation()),
    SENNTISTEN(TeleportType.ANCIENT_MAGICKS, "Senntisten", TeleportLocationData.SENNTISTEN.getLocation()),
    KHARYRLL(TeleportType.ANCIENT_MAGICKS, "Kharyrll", TeleportLocationData.KHARYRLL.getLocation()),
    CARRALLANGER(TeleportType.ANCIENT_MAGICKS, "Carrallanger", TeleportLocationData.CARRALLANGER.getLocation()),
    KOUREND(TeleportType.NORMAL_MAGIC, "Kourend Castle", TeleportLocationData.KOUREND.getLocation()),
    WATERBIRTH(TeleportType.LUNAR_MAGIC, "Waterbirth Island", TeleportLocationData.WATERBIRTH.getLocation()),
    ANNAKARL(TeleportType.ANCIENT_MAGICKS, "Annakarl", TeleportLocationData.ANNAKARL.getLocation()),
    GHORROCK(TeleportType.ANCIENT_MAGICKS, "Ghorrock", TeleportLocationData.GHORROCK.getLocation()),
    LUNAR_ISLE(TeleportType.LUNAR_MAGIC, "Lunar Isle", TeleportLocationData.MOONCLAN.getLocation()),
    CATHERBY(TeleportType.LUNAR_MAGIC, "Catherby", TeleportLocationData.CATHERBY.getLocation()),
    FISHING_GUILD(TeleportType.LUNAR_MAGIC, "Fishing Guild", TeleportLocationData.FISHING_GUILD.getLocation()),
    TROLL_STRONGHOLD(TeleportType.OTHER, "Troll Stronghold", TeleportLocationData.TROLLHEIM.getLocation()),
    WEISS(TeleportType.OTHER, "Weiss", TeleportLocationData.WEISS_ICY_BASALT.getLocation()),
    ARCEUUS_LIBRARY(TeleportType.ARCEUUS_MAGIC, "Arceuus Library", TeleportLocationData.ARCEUUS_LIBRARY.getLocation()),
    DRAYNOR_MANOR(TeleportType.ARCEUUS_MAGIC, "Draynor Manor", TeleportLocationData.DRAYNOR_MANOR.getLocation()),
    BATTLEFRONT(TeleportType.ARCEUUS_MAGIC, "Battlefront", TeleportLocationData.BATTLEFRONT.getLocation()),
    MIND_ALTAR(TeleportType.ARCEUUS_MAGIC, "Mind Altar", TeleportLocationData.MIND_ALTAR.getLocation()),
    SALVE_GRAVEYARD(TeleportType.ARCEUUS_MAGIC, "Salve Graveyard", TeleportLocationData.SALVE_GRAVEYARD.getLocation()),
    FENKENSTRAINS_CASTLE(TeleportType.ARCEUUS_MAGIC, "Fenken' Castle", TeleportLocationData.FENKENSTRAINS_CASTLE.getLocation()),
    WEST_ARDOUGNE(TeleportType.ARCEUUS_MAGIC, "West Ardougne", TeleportLocationData.WEST_ARDOUGNE.getLocation()),
    HARMONY_ISLAND(TeleportType.ARCEUUS_MAGIC, "Harmony Island", TeleportLocationData.HARMONY_ISLAND.getLocation()),
    CEMETERY(TeleportType.ARCEUUS_MAGIC, "Cemetery", TeleportLocationData.CEMETERY.getLocation()),
    BARROWS(TeleportType.ARCEUUS_MAGIC, "Barrows", TeleportLocationData.BARROWS.getLocation()),
    APE_ATOLL_DUNGEON(TeleportType.ARCEUUS_MAGIC, "Ape Atoll Dungeon", TeleportLocationData.APE_ATOLL_ARCEUUS.getLocation()),
    CIVITAS_ILLA_FORTIS(TeleportType.NORMAL_MAGIC, "Civitas illa Fortis", TeleportLocationData.CIVITAS_ILLA_FORTIS.getLocation()),
    TROLLHEIM(TeleportType.NORMAL_MAGIC, "Trollheim", PohPortal.TROLLHEIM.getDestination()),
    PADDEWWA(TeleportType.ANCIENT_MAGICKS, "Paddewwa", PohPortal.PADDEWWA.getDestination()),
    LASSAR(TeleportType.ANCIENT_MAGICKS, "Lassar", PohPortal.LASSAR.getDestination()),
    DAREEYAK(TeleportType.ANCIENT_MAGICKS, "Dareeyak", PohPortal.DAREEYAK.getDestination()),
    OURANIA(TeleportType.LUNAR_MAGIC, "Ourania", PohPortal.OURANIA.getDestination()),
    BARBARIAN_OUTPOST(TeleportType.LUNAR_MAGIC, "Barbarian Outpost", PohPortal.BARBARIAN_OUTPOST.getDestination()),
    PORT_KHAZARD(TeleportType.LUNAR_MAGIC, "Port Khazard", PohPortal.PORT_KHAZARD.getDestination()),
    ICE_PLATEAU(TeleportType.LUNAR_MAGIC, "Ice Plateau", PohPortal.ICE_PLATEAU.getDestination()),
    SEERS_VILLAGE(TeleportType.NORMAL_MAGIC, "Seers' Village", TeleportLocationData.CAMELOT_BANK.getLocation()),
    YANILLE(TeleportType.NORMAL_MAGIC, "Yanille", TeleportLocationData.WATCHTOWER_YANILLE.getLocation()),
    RESPAWN(TeleportType.ARCEUUS_MAGIC, "Respawn", null);

    private final TeleportType type;
    private final String text;
    private final WorldPoint location;

    private final int duration = 6;

    @Override
    public String displayInfo() {
        return "NexusTeleport -> " + text;
    }

    @Override
    public WorldPoint getDestination() {
        return this == RESPAWN ? respawnDestination() : location;
    }

    public WorldPoint getLocation() {
        return getDestination();
    }

    private static WorldPoint respawnDestination() {
        int[] flags = respawnSelectionVarbits();
        WorldPoint[] landings = {new WorldPoint(3095, 3469, 0), new WorldPoint(2964, 3378, 0),
                new WorldPoint(2757, 3479, 0), new WorldPoint(1682, 3135, 0),
                new WorldPoint(3151, 3636, 0), new WorldPoint(1630, 3674, 0)};
        WorldPoint selected = null;
        for (int i = 0; i < flags.length; i++) {
            int value = Microbot.getVarbitValue(flags[i]);
            if (value == 0) continue;
            if (value != 1 || selected != null) return null;
            selected = landings[i];
        }
        // No flag is ambiguous between Lumbridge and Prifddinas, not a default landing.
        return selected;
    }

    public static int[] respawnSelectionVarbits() {
        return new int[]{VarbitID.EDGEVILLE_SPAWN, VarbitID.FALADOR_SPAWN, VarbitID.CAMELOT_SPAWN,
                VarbitID.CIVITAS_SPAWN, VarbitID.WILDERNESS_SPAWN, VarbitID.KOUREND_SPAWN};
    }

    public int varbitValue() {
        // Saved-slot values are cache enum 1377 keys, not Java enum ordinals.
        switch (this) {
            case VARROCK: return 1;
            case VARROCK_GE: return 1;
            case SENNTISTEN: return 7;
            case MARIM: return 8;
            case LUNAR_ISLE: return 10;
            case FISHING_GUILD: return 13;
            case ANNAKARL: return 14;
            case TROLL_STRONGHOLD: return 15;
            case GHORROCK: return 17;
            case CARRALLANGER: return 18;
            case SEERS_VILLAGE: return 154;
            case YANILLE: return 156;
            case RESPAWN: return 40;
            default: return ordinal();
        }
    }


    @Override
    public boolean execute() {
        return NexusActions.usePortalNexus(this);
    }

    public final static Integer[] PORTAL_IDS = {
            // Portal-style Nexus (standalone portal object)
            ObjectID.POH_NEXUS_PORTAL_1, ObjectID.POH_NEXUS_PORTAL_2, ObjectID.POH_NEXUS_PORTAL_3, ObjectID.POH_NEXUS_PORTAL_LEAGUE_5,
            // Teleportation Chamber variants (the Nexus that appears inside a decorated room)
            ObjectID.POH_TELENEXUS_1, ObjectID.POH_TELENEXUS_2_MIDDLE, ObjectID.POH_TELENEXUS_2_SIDE,
            ObjectID.POH_TELENEXUS_2_CORNER, ObjectID.POH_TELENEXUS_3,
            // Amulet-topped Nexus variants
            ObjectID.POH_NEXUS_4_AMULET, ObjectID.POH_NEXUS_5_AMULET
    };

    public static boolean isNexusPortal(GameObject go) {
        if (go == null) return false;
        for (int id : PORTAL_IDS) {
            if (id == go.getId()) return true;
        }
        return false;
    }

    public static List<NexusPortal> getAvailableTeleports() {
        List<NexusPortal> teleports = new ArrayList<>();
        NexusPortal[] destinations = values();
        for (int varbit : VARBITS) {
            int value = Microbot.getVarbitValue(varbit);
            if (value <= 0) continue;
            if (value == 151 || value == 154 || value == 156) value -= 150;

            if (value == 1) {
                teleports.add(NexusPortal.VARROCK);
                if (Microbot.getVarbitValue(VARROCK_DIARY_MEDIUM_COMPLETE) == 1) {
                    teleports.add(NexusPortal.VARROCK_GE);
                }
                continue;
            }
            if (value == 4 && Microbot.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1) {
                teleports.add(SEERS_VILLAGE);
            }
            if (value == 6 && Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE) == 1) {
                teleports.add(YANILLE);
            }
            // Unrecognised alternative keys must not alias an unrelated base spell.
            if (value >= 150) continue;
            for (NexusPortal destination : destinations) {
                if (destination.varbitValue() == value) {
                    if (destination.getDestination() != null) teleports.add(destination);
                    break;
                }
            }
        }
        return teleports;
    }

    public static final int[] VARBITS = new int[]{
            VarbitID.POH_NEXUS_TELE_1,
            VarbitID.POH_NEXUS_TELE_2,
            VarbitID.POH_NEXUS_TELE_3,
            VarbitID.POH_NEXUS_TELE_4,
            VarbitID.POH_NEXUS_TELE_5,
            VarbitID.POH_NEXUS_TELE_6,
            VarbitID.POH_NEXUS_TELE_7,
            VarbitID.POH_NEXUS_TELE_8,
            VarbitID.POH_NEXUS_TELE_9,
            VarbitID.POH_NEXUS_TELE_10,
            VarbitID.POH_NEXUS_TELE_11,
            VarbitID.POH_NEXUS_TELE_12,
            VarbitID.POH_NEXUS_TELE_13,
            VarbitID.POH_NEXUS_TELE_14,
            VarbitID.POH_NEXUS_TELE_15,
            VarbitID.POH_NEXUS_TELE_16,
            VarbitID.POH_NEXUS_TELE_17,
            VarbitID.POH_NEXUS_TELE_18,
            VarbitID.POH_NEXUS_TELE_19,
            VarbitID.POH_NEXUS_TELE_20,
            VarbitID.POH_NEXUS_TELE_21,
            VarbitID.POH_NEXUS_TELE_22,
            VarbitID.POH_NEXUS_TELE_23,
            VarbitID.POH_NEXUS_TELE_24,
            VarbitID.POH_NEXUS_TELE_25,
            VarbitID.POH_NEXUS_TELE_26,
            VarbitID.POH_NEXUS_TELE_27,
            VarbitID.POH_NEXUS_TELE_28,
            VarbitID.POH_NEXUS_TELE_29,
            VarbitID.POH_NEXUS_TELE_30,
            VarbitID.POH_NEXUS_TELE_31,
            VarbitID.POH_NEXUS_TELE_32,
            VarbitID.POH_NEXUS_TELE_33,
            VarbitID.POH_NEXUS_TELE_34,
            VarbitID.POH_NEXUS_TELE_35,
            VarbitID.POH_NEXUS_TELE_36,
            VarbitID.POH_NEXUS_TELE_37,
            VarbitID.POH_NEXUS_TELE_38,
            VarbitID.POH_NEXUS_TELE_39,
            VarbitID.POH_NEXUS_TELE_40,
            VarbitID.POH_NEXUS_TELE_41,
            VarbitID.POH_NEXUS_TELE_42,
            VarbitID.POH_NEXUS_TELE_43,
            VarbitID.POH_NEXUS_TELE_44,
            VarbitID.POH_NEXUS_TELE_45,
    };

    @Override
    public String toString() {
        return getText();
    }

    static NexusPortal fromVarbitValue(int value) {
        if (value <= 0) return null;
        for (NexusPortal destination : values()) {
            if (destination.varbitValue() == value) return destination;
        }
        return null;
    }
}
