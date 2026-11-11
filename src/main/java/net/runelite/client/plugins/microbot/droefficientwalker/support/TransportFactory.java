package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/** Reuses the client's TSV parser; keeps endpoint metadata and rune fixes local to this walker. */
public final class TransportFactory {
    private static final Constructor<Transport> ROW = rowConstructor();
    private TransportFactory() {}

    private static Constructor<Transport> rowConstructor() {
        try {
            Constructor<Transport> constructor = Transport.class.getDeclaredConstructor(Map.class, TransportType.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Transport row schema changed", e); }
    }

    public static Transport row(Map<String, String> fields, TransportType type) {
        try {
            Transport transport = ROW.newInstance(fields, type);
            List<TransportItemRequirement> requirements = transport.getItemRequirements().stream().map(r ->
                    new TransportItemRequirement(r.getAlternatives(), r.getStaffAlternatives().stream()
                            .filter(id -> Rs2Staff.byItemId(id) != Rs2Staff.BRYOPHYTAS_STAFF
                                    && Rs2Staff.byItemId(id) != Rs2Staff.NONE).collect(Collectors.toSet()),
                            r.getOffhandAlternatives(), r.isRuneOnly())).collect(Collectors.toList());
            transport.getItemRequirements().clear();
            transport.getItemRequirements().addAll(requirements);
            net.runelite.client.plugins.microbot.droefficientwalker.SailorsAmuletTransports.apply(transport);
            return transport;
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Invalid transport row: " + e.getCause().getMessage(), e.getCause());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot parse transport row", e); }
    }

    public static Transport pair(Transport origin, Transport destination) { return new Paired(origin, destination); }
    public static Transport origin(Transport transport) { return transport instanceof Paired ? ((Paired) transport).origin : transport; }
    public static Transport destination(Transport transport) { return transport instanceof Paired ? ((Paired) transport).destination : transport; }

    private static final class Paired extends Transport {
        private final Transport origin, destination;
        private Paired(Transport origin, Transport destination) {
            super(origin, destination);
            this.origin = TransportFactory.origin(origin);
            this.destination = TransportFactory.destination(destination);
        }
    }
}
