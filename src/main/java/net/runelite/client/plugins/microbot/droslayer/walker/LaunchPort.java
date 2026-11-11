package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.plugins.microbot.api.boat.data.PortLocation;

/** User-facing launch choices; native port data remains the source of dock IDs and levels. */
public enum LaunchPort {
    AUTOMATIC(null),
    ALDARIN(PortLocation.ALDARIN),
    ARDOUGNE(PortLocation.ARDOUGNE),
    BRIMHAVEN(PortLocation.BRIMHAVEN),
    CAIRN_ISLE(PortLocation.CAIRN_ISLE),
    CATHERBY(PortLocation.CATHERBY),
    CIVITAS_ILLA_FORTIS(PortLocation.CIVITAS_ILLA_FORTIS),
    CORSAIR_COVE(PortLocation.CORSAIR_COVE),
    DEEPFIN_POINT(PortLocation.DEEPFIN_POINT),
    ENTRANA(PortLocation.ENTRANA),
    ETCETERIA(PortLocation.ETCETERIA),
    HOSIDIUS(PortLocation.HOSIDIUS),
    JATIZO(PortLocation.JATIZO),
    LANDS_END(PortLocation.LANDS_END),
    LUNAR_ISLE(PortLocation.LUNAR_ISLE),
    MUSA_POINT(PortLocation.MUSA_POINT),
    NEITIZNOT(PortLocation.NEITIZNOT),
    PANDEMONIUM(PortLocation.PANDEMONIUM),
    VOID_KNIGHTS_OUTPOST(PortLocation.VOID_KNIGHTS_OUTPOST),
    PISCATORIS(PortLocation.PISCATORIS),
    PORT_KHAZARD(PortLocation.PORT_KHAZARD),
    PORT_PISCARILIUS(PortLocation.PORT_PISCARILIUS),
    PORT_ROBERTS(PortLocation.PORT_ROBERTS),
    PORT_SARIM(PortLocation.PORT_SARIM),
    PORT_TYRAS(PortLocation.PORT_TYRAS),
    PRIFDDINAS(PortLocation.PRIFDDINAS),
    RED_ROCK(PortLocation.RED_ROCK),
    RELLEKKA(PortLocation.RELLEKKA),
    RUINS_OF_UNKAH(PortLocation.RUINS_OF_UNKAH),
    SUMMER_SHORE(PortLocation.SUMMER_SHORE),
    SUNSET_COAST(PortLocation.SUNSET_COAST);

    private final PortLocation port;

    LaunchPort(PortLocation port) { this.port = port; }

    @Override public String toString() {
        if (port == null) return "Closest to target";
        if (port == PortLocation.VOID_KNIGHTS_OUTPOST) return "Pest Control";
        if (port == PortLocation.PANDEMONIUM) return "Pandemonium";
        if (port == PortLocation.SUMMER_SHORE) return "Summer Shore";
        return port.getName();
    }
}
