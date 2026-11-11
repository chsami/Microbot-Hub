package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectedTransitionAdmissionTest
{
    private final WorldPoint from = new WorldPoint(3000,3000,0), to = new WorldPoint(3000,3001,0);
    private Transport source() { return new Transport(from,to,"Gate crossing",TransportType.TRANSPORT,false,"Open","Gate",123); }
    private Rs2TransportEdge edge(String change) {
        return new Rs2TransportEdge(change.equals("origin")?to:from,change.equals("destination")?from:to,
            change.equals("type")?Rs2TransportType.AGILITY_SHORTCUT:Rs2TransportType.TRANSPORT,
            change.equals("executor")?Rs2TransportExecutor.UNSUPPORTED:Rs2TransportExecutor.OBJECT,
            Rs2TerminalTravelMode.UNSUPPORTED,change.equals("display")?null:"Gate crossing",
            change.equals("action")?"Climb":"Open",change.equals("target")?"Door":"Gate",
            change.equals("object")?124:123,1,false,false,false,0,"",0,List.of());
    }
    @Test void matchesExactCapability() { assertTrue(NetworkTransportSupport.sameTransition(edge(""),source())); }
    @Test void checksExecutor() { assertFalse(NetworkTransportSupport.sameTransition(edge("executor"),source())); }
    @Test void checksType() { assertFalse(NetworkTransportSupport.sameTransition(edge("type"),source())); }
    @Test void checksOrigin() { assertFalse(NetworkTransportSupport.sameTransition(edge("origin"),source())); }
    @Test void checksDestination() { assertFalse(NetworkTransportSupport.sameTransition(edge("destination"),source())); }
    @Test void checksObject() { assertFalse(NetworkTransportSupport.sameTransition(edge("object"),source())); }
    @Test void checksAction() { assertFalse(NetworkTransportSupport.sameTransition(edge("action"),source())); }
    @Test void checksTarget() { assertFalse(NetworkTransportSupport.sameTransition(edge("target"),source())); }
    @Test void checksDisplay() { assertFalse(NetworkTransportSupport.sameTransition(edge("display"),source())); }
    @Test void missingLabelStillHasDiagnosticIdentity() { assertTrue(NetworkTransportSupport.transitionIdentity(edge("display")).contains("object=123")); }
    @Test void supplyRequirementsDoNotEraseMaintainedCapability() {
        Transport paid = source();
        paid.setItemIdRequirements(java.util.Set.of(java.util.Set.of(995)));
        assertTrue(NetworkTransportSupport.sameTransition(edge(""),paid));
    }
}
