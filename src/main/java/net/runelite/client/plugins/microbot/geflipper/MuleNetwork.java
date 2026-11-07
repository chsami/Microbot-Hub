package net.runelite.client.plugins.microbot.geflipper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Transport for coordinating profit transfers between one or more GE Flipper clients and a Mule client running on
 * the same computer. The listener binds to the loopback address only, so nothing on the network can reach it.
 */
final class MuleNetwork {
    private static final String HEADER = "GEFLIPPER_MULE_V1";
    static final long LEASE_DURATION_MS = 150_000L;
    static final long CONTACT_FRESHNESS_MS = 45_000L;
    static final int MAX_PACKET_BYTES = 4_096;
    private static final int TCP_TIMEOUT_MS = 1_200;
    private static final int LOCALHOST_PROBE_MS = 350;

    private MuleNetwork() { }

    static final class Lease {
        final String traderRsn;
        final int amountGp;
        final long expiresAtMillis;
        private final Object reservation;

        Lease(String traderRsn, int amountGp, long expiresAtMillis) {
            this(traderRsn, amountGp, expiresAtMillis, new Object());
        }

        private Lease(String traderRsn, int amountGp, long expiresAtMillis, Object reservation) {
            this.traderRsn = traderRsn;
            this.amountGp = amountGp;
            this.expiresAtMillis = expiresAtMillis;
            this.reservation = reservation;
        }

        boolean sameReservation(Lease other) { return other != null && reservation == other.reservation; }

        private Lease renewed(int amountGp, long expiresAtMillis) {
            return new Lease(traderRsn, amountGp, expiresAtMillis, reservation);
        }
    }

    static final class MuleReply {
        final boolean ok;
        final boolean ready;
        final String muleRsn;
        final int world;
        final int tileX;
        final int tileY;
        final int plane;
        final String leaseOwner;
        final String resolvedHost;
        final String message;
        final boolean responding;

        MuleReply(boolean ok, boolean ready, String muleRsn, int world,
                  String leaseOwner, String resolvedHost, String message) {
            this(ok, ready, muleRsn, world, MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
                leaseOwner, resolvedHost, message);
        }

        MuleReply(boolean ok, boolean ready, String muleRsn, int world,
                  int tileX, int tileY, int plane,
                  String leaseOwner, String resolvedHost, String message) {
            this(ok, ready, muleRsn, world, tileX, tileY, plane, leaseOwner, resolvedHost, message, true);
        }

        private MuleReply(boolean ok, boolean ready, String muleRsn, int world,
                          int tileX, int tileY, int plane,
                          String leaseOwner, String resolvedHost, String message, boolean responding) {
            this.ok = ok;
            this.ready = ready;
            this.muleRsn = muleRsn == null ? "" : muleRsn;
            this.world = world;
            this.tileX = tileX;
            this.tileY = tileY;
            this.plane = plane;
            this.leaseOwner = leaseOwner == null ? "" : leaseOwner;
            this.resolvedHost = resolvedHost == null ? "" : resolvedHost;
            this.message = message == null ? "" : message;
            this.responding = responding;
        }
    }

    static final class ServerHealth {
        final boolean listening;
        final boolean identityKnown;
        final boolean worldKnown;
        final boolean ready;
        final boolean recentSenderContact;
        final boolean leaseActive;

        ServerHealth(boolean listening, boolean identityKnown, boolean worldKnown, boolean ready,
                     boolean recentSenderContact, boolean leaseActive) {
            this.listening = listening;
            this.identityKnown = identityKnown;
            this.worldKnown = worldKnown;
            this.ready = ready;
            this.recentSenderContact = recentSenderContact;
            this.leaseActive = leaseActive;
        }
    }

    static final class Server implements AutoCloseable {
        private final LongSupplier clock;
        private final String bindHost;
        private volatile int boundPort = -1;
        private volatile boolean standby;
        private volatile boolean ready;
        private volatile String muleRsn = "";
        private volatile boolean ownIdentityKnown;
        private volatile int world;
        private volatile int tileX = MulePolicy.GE_CENTER_X;
        private volatile int tileY = MulePolicy.GE_CENTER_Y;
        private volatile int plane = MulePolicy.GE_CENTER_PLANE;
        private Lease activeLease;
        private long lastSenderContactMillis = -1;
        private ServerSocket tcpServer;
        private Thread tcpThread;

        Server() {
            this(() -> System.nanoTime() / 1_000_000L);
        }

        Server(LongSupplier clock) {
            this(clock, "127.0.0.1");
        }

        Server(LongSupplier clock, String bindHost) {
            this.clock = Objects.requireNonNull(clock);
            this.bindHost = Objects.requireNonNull(bindHost);
        }

        synchronized boolean ensureStarted(int port) {
            int targetPort = MulePolicy.clampPort(port);
            if (tcpServer != null && !tcpServer.isClosed() && boundPort == targetPort) {
                return true;
            }
            close();
            try {
                ServerSocket tcp = new ServerSocket();
                tcp.setReuseAddress(true);
                tcp.bind(new InetSocketAddress(bindHost, targetPort));
                this.tcpServer = tcp;
                this.boundPort = tcp.getLocalPort();
                this.standby = true;

                Thread acceptThread = new Thread(() -> runTcpLoop(tcp), "Geflipper-Mule-TCP-" + boundPort);
                acceptThread.setDaemon(true);
                acceptThread.start();
                this.tcpThread = acceptThread;
                return true;
            } catch (Exception bindFailed) {
                close();
                return false;
            }
        }

        int boundPort() {
            return boundPort;
        }

        synchronized void updateMuleState(boolean ready, String muleRsn, int world, int tileX, int tileY, int plane) {
            this.standby = true;
            this.ready = ready;
            this.muleRsn = muleRsn == null ? "" : muleRsn.trim();
            this.ownIdentityKnown = !MulePolicy.tradeRequestName(this.muleRsn, "").isEmpty();
            this.world = world;
            if (tileX > 0 && tileY > 0) {
                this.tileX = tileX;
                this.tileY = tileY;
                this.plane = Math.max(0, Math.min(3, plane));
            }
            expireLeaseIfNeeded(clock.getAsLong());
        }

        synchronized Lease activeLease() {
            expireLeaseIfNeeded(clock.getAsLong());
            return activeLease;
        }

        synchronized void clearLease(Lease expected) {
            // Receipt processing runs alongside TCP completion/new-reservation packets.
            // Renewals retain their identity; a replacement reservation must survive an old receipt.
            if (expected != null && expected.sameReservation(activeLease)) activeLease = null;
        }

        synchronized boolean recentSenderContact(long nowMillis) {
            return lastSenderContactMillis >= 0 && nowMillis >= lastSenderContactMillis
                && nowMillis - lastSenderContactMillis < CONTACT_FRESHNESS_MS;
        }

        synchronized ServerHealth health() {
            long now = clock.getAsLong();
            expireLeaseIfNeeded(now);
            return new ServerHealth(tcpServer != null && !tcpServer.isClosed()
                && tcpThread != null && tcpThread.isAlive(), ownIdentityKnown, world > 0,
                ready, recentSenderContact(now), activeLease != null);
        }

        private void expireLeaseIfNeeded(long nowMillis) {
            if (activeLease != null && nowMillis >= activeLease.expiresAtMillis) {
                activeLease = null;
            }
        }

        synchronized String handleLine(String rawLine) {
            Map<String, String> req = parsePacket(rawLine);
            if (req.isEmpty()) {
                return formatReply(false, false, "", 0, tileX, tileY, plane, "", "Invalid request");
            }
            long now = clock.getAsLong();
            expireLeaseIfNeeded(now);
            String cmd = req.getOrDefault("cmd", "STATUS");
            String flipper = req.getOrDefault("flipper", "").trim();
            String expectedMule = req.getOrDefault("mule", "").trim();
            int amount = parsePositiveInt(req.get("amount"));
            String currentLeaseOwner = activeLease == null ? "" : activeLease.traderRsn;

            if (!expectedMule.isEmpty() && !muleRsn.isEmpty() && !MulePolicy.sameRsn(expectedMule, muleRsn)) {
                return formatReply(false, ready, muleRsn, world, tileX, tileY, plane, currentLeaseOwner,
                    "Mule RSN mismatch (expected " + expectedMule + " but mule is " + muleRsn + ")");
            }

            String effectiveRsn = !muleRsn.isEmpty() ? muleRsn : expectedMule;

            if ("DISCOVER".equals(cmd) || "STATUS".equals(cmd)) {
                String namedSender = MulePolicy.tradeRequestName(flipper, "");
                boolean knownIdentity = ownIdentityKnown;
                if ("STATUS".equals(cmd) && knownIdentity && !expectedMule.isEmpty()
                    && MulePolicy.sameRsn(expectedMule, muleRsn) && !namedSender.isEmpty()
                    && !MulePolicy.sameRsn(namedSender, muleRsn)) {
                    lastSenderContactMillis = now;
                }
                boolean available = standby && knownIdentity && world > 0;
                // A status reply must advertise our own known identity, never echo the caller's expectation.
                return formatReply(available, ready, knownIdentity ? muleRsn : "", world, tileX, tileY, plane, currentLeaseOwner,
                    ready ? "Mule ready" : (available ? "Mule on standby (logged out until needed)" : "Mule needs identity/world setup"));
            }
            if ("REQUEST_TRADE".equals(cmd)) {
                if (flipper.isEmpty() || amount < MulePolicy.MIN_TRANSFER_GP) {
                    return formatReply(false, ready, effectiveRsn, world, tileX, tileY, plane, currentLeaseOwner,
                        "Invalid flipper or amount");
                }
                if (!standby || effectiveRsn.isEmpty() || world <= 0) {
                    return formatReply(false, false, effectiveRsn, world, tileX, tileY, plane, currentLeaseOwner,
                        "Mule has no RSN or world configured yet");
                }
                if (MulePolicy.sameRsn(flipper, effectiveRsn)) {
                    return formatReply(false, ready, effectiveRsn, world, tileX, tileY, plane, currentLeaseOwner,
                        "Flipper and Mule cannot be the same account");
                }
                if (activeLease != null && !MulePolicy.sameRsn(activeLease.traderRsn, flipper)) {
                    return formatReply(false, ready, effectiveRsn, world, tileX, tileY, plane, activeLease.traderRsn,
                        "Mule is busy trading with " + activeLease.traderRsn);
                }
                if (muleRsn.isEmpty() && !expectedMule.isEmpty()) {
                    muleRsn = expectedMule;
                }
                activeLease = activeLease == null ? new Lease(flipper, amount, now + LEASE_DURATION_MS)
                    : activeLease.renewed(amount, now + LEASE_DURATION_MS);
                return formatReply(true, ready, effectiveRsn, world, tileX, tileY, plane, activeLease.traderRsn,
                    ready ? "Lease granted" : "Lease granted; mule logging in");
            }
            if ("HEARTBEAT".equals(cmd)) {
                if (activeLease != null && MulePolicy.sameRsn(activeLease.traderRsn, flipper)) {
                    int updatedAmount = amount >= MulePolicy.MIN_TRANSFER_GP ? amount : activeLease.amountGp;
                    activeLease = activeLease.renewed(updatedAmount, now + LEASE_DURATION_MS);
                    return formatReply(true, ready, effectiveRsn, world, tileX, tileY, plane, activeLease.traderRsn,
                        "Lease renewed");
                }
                return formatReply(false, ready, effectiveRsn, world, tileX, tileY, plane, currentLeaseOwner,
                    "Lease expired or not owned");
            }
            if ("COMPLETE_TRADE".equals(cmd) || "ABORT_TRADE".equals(cmd)) {
                if (activeLease != null && MulePolicy.sameRsn(activeLease.traderRsn, flipper)) {
                    activeLease = null;
                }
                return formatReply(true, ready, effectiveRsn, world, tileX, tileY, plane, "", "Lease released");
            }
            return formatReply(false, ready, effectiveRsn, world, tileX, tileY, plane, currentLeaseOwner, "Unknown command");
        }

        private void runTcpLoop(ServerSocket server) {
            while (!server.isClosed() && !Thread.currentThread().isInterrupted()) {
                try (Socket client = server.accept()) {
                    String line = readBoundedLine(client, TCP_TIMEOUT_MS);
                    String response = handleLine(line);
                    writeBoundedLine(client, response);
                } catch (Exception ignored) {
                    if (server.isClosed()) break;
                }
            }
        }

        @Override
        public synchronized void close() {
            boundPort = -1;
            activeLease = null;
            standby = false;
            ready = false;
            lastSenderContactMillis = -1;
            muleRsn = "";
            ownIdentityKnown = false;
            world = 0;
            if (tcpServer != null) {
                try { tcpServer.close(); } catch (Exception ignored) { }
                tcpServer = null;
            }
            if (tcpThread != null) {
                tcpThread.interrupt();
                tcpThread = null;
            }
        }
    }

    @FunctionalInterface
    interface TcpTransport {
        MuleReply send(String host, int port, String request, int timeoutMs);
    }

    static final class Client {
        /** The Mule always runs on this computer. */
        static final String LOCAL_HOST = "127.0.0.1";
        private final TcpTransport tcp;

        Client() {
            this(Client::sendTcp);
        }

        Client(TcpTransport tcp) {
            this.tcp = Objects.requireNonNull(tcp);
        }

        /** Read-only reachability check: never requests, renews or releases a trade lease. */
        MuleReply probeHealth(String configuredHost, int port, String expectedMuleRsn, String flipperRsn) {
            if (MulePolicy.tradeRequestName(expectedMuleRsn, "").isEmpty()) {
                return noResponse("", "Mule identity is required");
            }
            MulePolicy.HostPort hp = MulePolicy.parseHostAndPort(configuredHost, port);
            String request = formatRequest("STATUS", expectedMuleRsn, flipperRsn, 0);
            if (!isAutoHost(hp.host)) return tcp.send(hp.host, hp.port, request, TCP_TIMEOUT_MS);

            MuleReply local = tcp.send(LOCAL_HOST, hp.port, request, LOCALHOST_PROBE_MS);
            if (knownMatchingMule(local, expectedMuleRsn)) return local;
            return local != null && local.responding ? local : noResponse("", "No matching Mule responded");
        }

        MuleReply probeStatus(String configuredHost, int port, String expectedMuleRsn) {
            return sendCommand("STATUS", configuredHost, port, expectedMuleRsn, "", 0);
        }

        MuleReply requestLease(String configuredHost, int port, String expectedMuleRsn, String flipperRsn, int amountGp) {
            return sendCommand("REQUEST_TRADE", configuredHost, port, expectedMuleRsn, flipperRsn, amountGp);
        }

        MuleReply heartbeat(String configuredHost, int port, String expectedMuleRsn, String flipperRsn, int amountGp) {
            return sendCommand("HEARTBEAT", configuredHost, port, expectedMuleRsn, flipperRsn, amountGp);
        }

        MuleReply releaseLease(String configuredHost, int port, String expectedMuleRsn, String flipperRsn, boolean completed) {
            return sendCommand(completed ? "COMPLETE_TRADE" : "ABORT_TRADE",
                configuredHost, port, expectedMuleRsn, flipperRsn, 0);
        }

        MuleReply sendCommand(String cmd, String configuredHost, int port,
                              String expectedMuleRsn, String flipperRsn, int amountGp) {
            MulePolicy.HostPort hp = MulePolicy.parseHostAndPort(configuredHost, port);
            int targetPort = hp.port;
            String host = resolveHost(hp.host, targetPort, expectedMuleRsn);
            if (host.isEmpty()) {
                return new MuleReply(false, false, "", 0, "", "", "No Mule found on this computer (port " + targetPort + ")");
            }
            MuleReply reply = tcp.send(host, targetPort,
                formatRequest(cmd, expectedMuleRsn, flipperRsn, amountGp), TCP_TIMEOUT_MS);
            if (!matchesMule(reply, expectedMuleRsn)) {
                if (reply.ok) {
                    return new MuleReply(false, false, "", 0, "", host, "Mule name changed; checking again");
                }
            }
            return reply;
        }

        private String resolveHost(String configuredHost, int port, String expectedMuleRsn) {
            if (!isAutoHost(configuredHost)) {
                return configuredHost.trim();
            }
            MuleReply local = tcp.send(LOCAL_HOST, port, formatRequest("STATUS", expectedMuleRsn, "", 0), LOCALHOST_PROBE_MS);
            return availableMule(local, expectedMuleRsn) ? LOCAL_HOST : "";
        }

        private static boolean isAutoHost(String configuredHost) {
            if (configuredHost == null) return true;
            String trimmed = configuredHost.trim();
            return trimmed.isEmpty() || "auto".equalsIgnoreCase(trimmed);
        }

        private static boolean matchesMule(MuleReply reply, String expectedMuleRsn) {
            return reply != null && !reply.muleRsn.trim().isEmpty()
                && (expectedMuleRsn == null || expectedMuleRsn.trim().isEmpty()
                    || MulePolicy.sameRsn(expectedMuleRsn, reply.muleRsn));
        }

        private static boolean availableMule(MuleReply reply, String expectedMuleRsn) {
            // A logged-out receiver is available on standby; it need not be ready to trade yet.
            return matchesMule(reply, expectedMuleRsn) && reply.ok && reply.world > 0;
        }

        private static boolean knownMatchingMule(MuleReply reply, String expectedMuleRsn) {
            return reply != null && reply.responding && matchesMule(reply, expectedMuleRsn);
        }

        static MuleReply sendTcp(String host, int port, String requestLine, int timeoutMs) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Math.max(1, timeoutMs));
                writeBoundedLine(socket, requestLine);
                return parseReply(readBoundedLine(socket, timeoutMs), host);
            } catch (Exception failed) {
                return noResponse(host, "Connection failed: " + failed.getClass().getSimpleName());
            }
        }

        private static MuleReply parseReply(String line, String host) {
            Map<String, String> map = parsePacket(line);
            if (map.isEmpty()) {
                return noResponse(host, "Invalid reply from mule");
            }
            int tx = parsePositiveInt(map.get("x"));
            int ty = parsePositiveInt(map.get("y"));
            int tp = parsePositiveInt(map.get("plane"));
            if (tx <= 0 || ty <= 0) {
                tx = MulePolicy.GE_CENTER_X;
                ty = MulePolicy.GE_CENTER_Y;
                tp = MulePolicy.GE_CENTER_PLANE;
            }
            return new MuleReply(
                "true".equalsIgnoreCase(map.get("ok")),
                "true".equalsIgnoreCase(map.get("ready")),
                map.getOrDefault("mule", ""),
                parsePositiveInt(map.get("world")),
                tx, ty, tp,
                map.getOrDefault("lease", ""), host, map.getOrDefault("msg", ""));
        }
    }

    static MuleReply noResponse(String host, String message) {
        return new MuleReply(false, false, "", 0, MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y,
            MulePolicy.GE_CENTER_PLANE, "", host, message, false);
    }

    /** Fixed storage and a total read deadline prevent both oversized and trickled packets. */
    static String readBoundedLine(Socket socket, int timeoutMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMs));
        InputStream input = socket.getInputStream();
        byte[] bytes = new byte[MAX_PACKET_BYTES];
        int count = 0;
        while (true) {
            socket.setSoTimeout(remainingMillis(deadline, System.nanoTime()));
            int value = input.read();
            if (deadline - System.nanoTime() <= 0) throw new SocketTimeoutException("Mule packet deadline exceeded");
            if (value < 0) return count == 0 ? null : new String(bytes, 0, count, StandardCharsets.UTF_8);
            if (value == '\n' || value == '\r') return new String(bytes, 0, count, StandardCharsets.UTF_8);
            if (count >= bytes.length) throw new IOException("Mule packet is too large");
            bytes[count++] = (byte) value;
        }
    }

    private static int remainingMillis(long deadline, long now) throws SocketTimeoutException {
        long remaining = deadline - now;
        if (remaining <= 0) throw new SocketTimeoutException("Mule packet deadline exceeded");
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
    }

    private static void writeBoundedLine(Socket socket, String line) throws IOException {
        if (line.length() > MAX_PACKET_BYTES) throw new IOException("Mule packet is too large");
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PACKET_BYTES) throw new IOException("Mule packet is too large");
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().write('\n');
        socket.getOutputStream().flush();
    }

    private static String sanitizeField(String value) {
        if (value == null) return "";
        return value.replace('|', ' ').replace('=', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            int val = Integer.parseInt(raw.trim());
            return Math.max(0, val);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    static String formatRequest(String cmd, String expectedMuleRsn, String flipper, int amount) {
        return HEADER
            + "|cmd=" + sanitizeField(cmd)
            + "|mule=" + sanitizeField(expectedMuleRsn)
            + "|flipper=" + sanitizeField(flipper)
            + "|amount=" + Math.max(0, amount);
    }

    static String formatReply(boolean ok, boolean ready, String muleRsn, int world,
                              int x, int y, int plane, String lease, String msg) {
        return HEADER
            + "|ok=" + ok
            + "|ready=" + ready
            + "|mule=" + sanitizeField(muleRsn)
            + "|world=" + Math.max(0, world)
            + "|x=" + Math.max(0, x)
            + "|y=" + Math.max(0, y)
            + "|plane=" + Math.max(0, Math.min(3, plane))
            + "|lease=" + sanitizeField(lease)
            + "|msg=" + sanitizeField(msg);
    }

    static Map<String, String> parsePacket(String line) {
        if (line == null || line.length() > MAX_PACKET_BYTES) return Collections.emptyMap();
        String trimmed = line.trim();
        if (!trimmed.startsWith(HEADER + "|")) return Collections.emptyMap();
        String[] parts = trimmed.split("\\|");
        Map<String, String> map = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                map.put(parts[i].substring(0, eq).trim(), parts[i].substring(eq + 1).trim());
            }
        }
        return map;
    }
}
