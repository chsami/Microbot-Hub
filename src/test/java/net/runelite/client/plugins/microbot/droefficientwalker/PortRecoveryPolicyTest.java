package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PortRecoveryPolicyTest {
    @Test public void lostBoatWithTabletSelectsPestForTheReportedSouthernDestination() {
        EfficientSailingConfig config=new EfficientSailingConfig() {
            @Override public BoatPort currentBoatPort() {return BoatPort.UNKNOWN_LOST;}
            @Override public LaunchPort launchPort() {return LaunchPort.AUTOMATIC;}
            @Override public boolean useSummonBoatTablet() {return true;}
        };
        List<PortCatalog.Port> ports=PortCatalog.shortlist(config,99,new SeaPoint(3167,3487),new SeaPoint(2633,2544),false,true);
        assertEquals("VOID_KNIGHTS_OUTPOST",ports.get(0).key);
        assertEquals(new WorldPoint(2658,2659,0),PortCatalog.landGateway(ports.get(0).key));
        assertNotEquals(new WorldPoint(ports.get(0).sea.x,ports.get(0).sea.y,0),PortCatalog.landGateway(ports.get(0).key));
    }
    @Test
    public void retiredPortSelectorsCannotOverrideClosestTabletPort() {
        EfficientSailingConfig config = new EfficientSailingConfig() {
            @Override public BoatPort currentBoatPort() { return BoatPort.SUMMER_SHORE; }
            @Override public LaunchPort launchPort() { return LaunchPort.PORT_KHAZARD; }
            @Override public boolean useSummonBoatTablet() { return true; }
        };
        assertEquals("", PortCatalog.forcedPort(config));
        List<PortCatalog.Port> ports = PortCatalog.shortlist(config, 99, new SeaPoint(2685, 3162), new SeaPoint(2700, 3100), false);
        assertTrue(ports.size()>1);
        assertEquals("PORT_KHAZARD", ports.get(0).key);
    }

    @Test
    public void retiredKnownLocationDoesNotConstrainEitherRecoveryMode() {
        EfficientSailingConfig tablet = new EfficientSailingConfig() {
            @Override public BoatPort currentBoatPort() { return BoatPort.SUMMER_SHORE; }
            @Override public boolean useSummonBoatTablet() { return true; }
        };
        assertEquals("", PortCatalog.forcedPort(tablet));
        EfficientSailingConfig noRelocation = new EfficientSailingConfig() {
            @Override public BoatPort currentBoatPort() { return BoatPort.SUMMER_SHORE; }
            @Override public boolean allowRecovery() { return false; }
        };
        assertEquals("", PortCatalog.forcedPort(noRelocation));
    }

    @Test
    public void legacyAutoKeepsLandCostWhileExplicitTotalAccountsForOceanLeg() {
        PortCatalog.Port summer = PortCatalog.byKey("SUMMER_SHORE");
        PortCatalog.Port khazard = PortCatalog.byKey("PORT_KHAZARD");
        PortCatalog.Candidate autoSummer = candidate(summer, 75, 593, EfficientSailingConfig.PortMode.AUTO);
        PortCatalog.Candidate autoKhazard = candidate(khazard, 100, 20, EfficientSailingConfig.PortMode.AUTO);
        assertTrue(autoSummer.total < autoKhazard.total);
        PortCatalog.Candidate totalSummer = candidate(summer, 75, 593, EfficientSailingConfig.PortMode.TOTAL_JOURNEY);
        PortCatalog.Candidate totalKhazard = candidate(khazard, 100, 20, EfficientSailingConfig.PortMode.TOTAL_JOURNEY);
        assertTrue(totalKhazard.total < totalSummer.total);
        assertEquals(141.1, totalSummer.total, .0001); // 75*.3 + 593*.6/3; both terms are seconds.
    }

    @Test
    public void automaticPortChoiceUsesTheDestinationWhileCurrentBoatLocationIsSeparate() {
        SeaPoint origin = PortCatalog.byKey("SUMMER_SHORE").sea;
        SeaPoint destination = PortCatalog.byKey("PORT_KHAZARD").sea;
        EfficientSailingConfig local = new EfficientSailingConfig() {
            @Override public int portCandidates() { return 1; }
        };
        assertEquals("PORT_KHAZARD", PortCatalog.shortlist(local, 99, origin, destination, false).get(0).key);
        EfficientSailingConfig target = new EfficientSailingConfig() {
            @Override public PortMode portMode() { return PortMode.DESTINATION_NEAREST; }
            @Override public int portCandidates() { return 1; }
        };
        assertEquals("PORT_KHAZARD", PortCatalog.shortlist(target, 99, origin, destination, false).get(0).key);
    }

    @Test
    public void reportedElsewhereMessageResolvesKnownPortWithoutSelectingALaunch() {
        assertEquals("SUMMER_SHORE", BoardingMessage.dockedElsewhere(
                "You cannot board your boat as it is currently docked at the Summer Shore.").get());
        assertEquals("PORT_KHAZARD", BoardingMessage.dockedElsewhere(
                "You cannot board your boat as it is currently docked at Port Khazard.").get());
        assertFalse(BoardingMessage.dockedElsewhere("Your boat is now docked.").isPresent());
        assertFalse(BoardingMessage.dockedElsewhere(
                "You cannot board your boat as it is currently docked at an unknown island.").isPresent());
        assertFalse(BoardingMessage.dockedElsewhere(null).isPresent());
    }

    private PortCatalog.Candidate candidate(PortCatalog.Port port, double land, double sea, EfficientSailingConfig.PortMode mode) {
        return new PortCatalog.Candidate(port, new WorldPoint(2685, 3162, 0), land, sea,
                "fixture", true, mode, 3, 20);
    }
}
