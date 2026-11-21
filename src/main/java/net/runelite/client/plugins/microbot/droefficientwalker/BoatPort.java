package net.runelite.client.plugins.microbot.droefficientwalker;

/** Last known boat location is information, independent of the desired launch port. */
public enum BoatPort {
    UNKNOWN_LOST(null),
    ALDARIN(LaunchPort.ALDARIN),
    ARDOUGNE(LaunchPort.ARDOUGNE),
    BRIMHAVEN(LaunchPort.BRIMHAVEN),
    CAIRN_ISLE(LaunchPort.CAIRN_ISLE),
    CATHERBY(LaunchPort.CATHERBY),
    CIVITAS_ILLA_FORTIS(LaunchPort.CIVITAS_ILLA_FORTIS),
    CORSAIR_COVE(LaunchPort.CORSAIR_COVE),
    DEEPFIN_POINT(LaunchPort.DEEPFIN_POINT),
    ENTRANA(LaunchPort.ENTRANA),
    ETCETERIA(LaunchPort.ETCETERIA),
    HOSIDIUS(LaunchPort.HOSIDIUS),
    JATIZO(LaunchPort.JATIZO),
    LANDS_END(LaunchPort.LANDS_END),
    LUNAR_ISLE(LaunchPort.LUNAR_ISLE),
    MUSA_POINT(LaunchPort.MUSA_POINT),
    NEITIZNOT(LaunchPort.NEITIZNOT),
    PANDEMONIUM(LaunchPort.PANDEMONIUM),
    VOID_KNIGHTS_OUTPOST(LaunchPort.VOID_KNIGHTS_OUTPOST),
    PISCATORIS(LaunchPort.PISCATORIS),
    PORT_KHAZARD(LaunchPort.PORT_KHAZARD),
    PORT_PISCARILIUS(LaunchPort.PORT_PISCARILIUS),
    PORT_ROBERTS(LaunchPort.PORT_ROBERTS),
    PORT_SARIM(LaunchPort.PORT_SARIM),
    PORT_TYRAS(LaunchPort.PORT_TYRAS),
    PRIFDDINAS(LaunchPort.PRIFDDINAS),
    RED_ROCK(LaunchPort.RED_ROCK),
    RELLEKKA(LaunchPort.RELLEKKA),
    RUINS_OF_UNKAH(LaunchPort.RUINS_OF_UNKAH),
    SUMMER_SHORE(LaunchPort.SUMMER_SHORE),
    SUNSET_COAST(LaunchPort.SUNSET_COAST);

    private final LaunchPort port;

    BoatPort(LaunchPort port) { this.port = port; }

    String portKey() { return port == null ? "" : port.portKey(); }

    static BoatPort fromLegacy(String text) {
        String key = PortCatalog.normalize(text);
        for (BoatPort choice : values()) if (choice.portKey().equals(key)) return choice;
        return UNKNOWN_LOST;
    }

    public String fullName() { return port == null ? "Unknown / lost" : port.fullName(); }

    @Override public String toString() { return port == null ? "Unknown / lost" : port.toString(); }
}
