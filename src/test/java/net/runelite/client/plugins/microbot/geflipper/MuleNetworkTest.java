package net.runelite.client.plugins.microbot.geflipper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleNetworkTest {
    private static final int PORT = 43599;

    @Test
    void receiptCannotClearAReplacementReservationEvenForTheSameSender() {
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> 1000L)) {
            server.updateMuleState(true, "TargetMule", 301, 3164, 3487, 0);
            server.handleLine(MuleNetwork.formatRequest("REQUEST_TRADE", "TargetMule", "FirstFlipper", 20_000));
            MuleNetwork.Lease completed = server.activeLease();
            server.handleLine(MuleNetwork.formatRequest("COMPLETE_TRADE", "TargetMule", "FirstFlipper", 0));
            server.handleLine(MuleNetwork.formatRequest("REQUEST_TRADE", "TargetMule", "FirstFlipper", 30_000));
            MuleNetwork.Lease replacement = server.activeLease();
            server.clearLease(completed);
            assertSame(replacement, server.activeLease(), "An old receipt cannot release a new reservation");
            server.clearLease(replacement);
            assertNull(server.activeLease());
        }
    }

    @Test
    void heartbeatRetainsReservationIdentitySoItsVerifiedReceiptCanClearIt() {
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> 1000L)) {
            server.updateMuleState(true, "TargetMule", 301, 3164, 3487, 0);
            server.handleLine(MuleNetwork.formatRequest("REQUEST_TRADE", "TargetMule", "FirstFlipper", 20_000));
            MuleNetwork.Lease observed = server.activeLease();
            server.handleLine(MuleNetwork.formatRequest("HEARTBEAT", "TargetMule", "FirstFlipper", 20_000));
            assertNotSame(observed, server.activeLease());
            assertTrue(observed.sameReservation(server.activeLease()));
            server.clearLease(observed);
            assertNull(server.activeLease());
        }
    }

    @Test
    void identityChangeBetweenProbeAndCommandIsRejected() {
        MuleNetwork.Client client = new MuleNetwork.Client((host, port, line, timeout) -> {
            boolean status = "STATUS".equals(MuleNetwork.parsePacket(line).get("cmd"));
            return reply(true, true, status ? "TargetMule" : "OtherMule", host);
        });

        MuleNetwork.MuleReply result = client.requestLease("auto", PORT, "TargetMule", "TestFlipper", 20_000);

        assertFalse(result.ok);
        assertTrue(result.message.contains("name changed"));
    }

    @Test
    void namedHealthStatusFindsKnownReceiverWithoutAWorldAndDoesNotReserveTrade() throws Exception {
        long[] now = {1_000L};
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> now[0]);
             Peer peer = new Peer(socket -> {
                 server.updateMuleState(false, "TargetMule", 0, 3164, 3487, 0);
                 String request = MuleNetwork.readBoundedLine(socket, 1_000);
                 assertEquals("STATUS", MuleNetwork.parsePacket(request).get("cmd"));
                 assertEquals("TestFlipper", MuleNetwork.parsePacket(request).get("flipper"));
                 socket.getOutputStream().write((server.handleLine(request) + "\n").getBytes(StandardCharsets.UTF_8));
             })) {
            MuleNetwork.MuleReply result = new MuleNetwork.Client().probeHealth(
                "127.0.0.1", peer.port(), "TargetMule", "TestFlipper");
            assertTrue(result.responding);
            assertEquals("TargetMule", result.muleRsn);
            assertEquals(0, result.world);
            assertFalse(result.ok);
            assertNull(server.activeLease(), "Health must never request a reservation");
            assertTrue(server.recentSenderContact(now[0]));
            assertFalse(server.health().worldKnown);
        }
    }

    @Test
    void theMuleListenerOnlyListensOnThisComputer() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 1, loopback)) {
            port = reservation.getLocalPort();
        }
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> 1_000L)) {
            java.lang.reflect.Field bindHost = MuleNetwork.Server.class.getDeclaredField("bindHost");
            bindHost.setAccessible(true);
            assertEquals("127.0.0.1", bindHost.get(server), "Nothing on the network may reach the listener");
            assertTrue(server.ensureStarted(port));
            server.updateMuleState(false, "TargetMule", 301, 3164, 3487, 0);
            assertFalse(server.health().recentSenderContact);
            MuleNetwork.MuleReply reply = new MuleNetwork.Client().probeHealth(
                "127.0.0.1", port, "TargetMule", "TestFlipper");
            assertTrue(reply.ok);
            assertTrue(server.health().listening);
            assertTrue(server.health().recentSenderContact);
            assertNull(server.activeLease());
        }
    }

    @Test
    void unknownReceiverIdentityIsNeverEchoedFromTheProbe() {
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> 1_000L)) {
            server.updateMuleState(false, "", 301, 3164, 3487, 0);
            String reply = server.handleLine(MuleNetwork.formatRequest("STATUS", "TargetMule", "TestFlipper", 0));
            assertEquals("", MuleNetwork.parsePacket(reply).get("mule"));
            assertEquals("false", MuleNetwork.parsePacket(reply).get("ok"));
            assertFalse(server.recentSenderContact(1_000L));
            assertNull(server.activeLease());
            server.updateMuleState(false, "Invalid|Name", 301, 3164, 3487, 0);
            assertFalse(server.health().identityKnown);
        }
    }

    @Test
    void legacyReservationCannotPromoteAnExpectedNameIntoVerifiedOwnIdentity() {
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> 1_000L)) {
            server.updateMuleState(false, "", 301, 3164, 3487, 0);
            // Preserve the legacy direct-command path, but its caller-supplied name is not identity evidence.
            String reservation = server.handleLine(MuleNetwork.formatRequest("REQUEST_TRADE", "TargetMule", "TestFlipper", 20_000));
            assertEquals("true", MuleNetwork.parsePacket(reservation).get("ok"));
            assertNotNull(server.activeLease());
            assertFalse(server.health().identityKnown);
            String status = server.handleLine(MuleNetwork.formatRequest("STATUS", "TargetMule", "TestFlipper", 0));
            assertEquals("", MuleNetwork.parsePacket(status).get("mule"));
            assertEquals("false", MuleNetwork.parsePacket(status).get("ok"));
            assertFalse(server.health().recentSenderContact);
        }
    }

    @Test
    void onlyValidatedNamedStatusMarksContactAndItExpiresWithoutRenewingLease() {
        long[] now = {1_000L};
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> now[0])) {
            server.updateMuleState(false, "TargetMule", 301, 3164, 3487, 0);
            server.handleLine(MuleNetwork.formatRequest("STATUS", "OtherMule", "TestFlipper", 0));
            server.handleLine(MuleNetwork.formatRequest("STATUS", "TargetMule", "", 0));
            server.handleLine(MuleNetwork.formatRequest("STATUS", "", "TestFlipper", 0));
            server.handleLine(MuleNetwork.formatRequest("STATUS", "TargetMule", "TargetMule", 0));
            server.handleLine(MuleNetwork.formatRequest("DISCOVER", "TargetMule", "TestFlipper", 0));
            assertFalse(server.recentSenderContact(now[0]));
            assertNull(server.activeLease());

            server.handleLine(MuleNetwork.formatRequest("REQUEST_TRADE", "TargetMule", "TestFlipper", 20_000));
            long leaseExpiry = server.activeLease().expiresAtMillis;
            now[0] += 5_000L;
            server.handleLine(MuleNetwork.formatRequest("STATUS", "TargetMule", "TestFlipper", 0));
            assertEquals(leaseExpiry, server.activeLease().expiresAtMillis);
            assertTrue(server.recentSenderContact(now[0] + MuleNetwork.CONTACT_FRESHNESS_MS - 1));
            assertFalse(server.recentSenderContact(now[0] + MuleNetwork.CONTACT_FRESHNESS_MS));
            server.close();
            assertFalse(server.health().identityKnown);
            assertFalse(server.health().worldKnown);
            assertFalse(server.health().recentSenderContact);
        }
    }

    @Test
    void automaticHostOnlyAsksThisComputer() {
        List<String> commands = new ArrayList<>();
        String[] localMule = {"TargetMule"};
        MuleNetwork.Client client = new MuleNetwork.Client((host, port, line, timeout) -> {
            commands.add(host + ":" + MuleNetwork.parsePacket(line).get("cmd"));
            return reply(true, false, localMule[0], host);
        });

        MuleNetwork.MuleReply granted = client.requestLease("auto", PORT, "TargetMule", "TestFlipper", 20_000);
        assertTrue(granted.ok);
        assertEquals(List.of("127.0.0.1:STATUS", "127.0.0.1:REQUEST_TRADE"), commands);

        commands.clear();
        localMule[0] = "OtherMule";
        MuleNetwork.MuleReply missing = client.requestLease("auto", PORT, "TargetMule", "TestFlipper", 20_000);
        assertFalse(missing.ok);
        assertTrue(missing.message.contains("this computer"));
        assertEquals(List.of("127.0.0.1:STATUS"), commands);

        commands.clear();
        assertEquals("127.0.0.1", client.probeHealth("auto", PORT, "TargetMule", "TestFlipper").resolvedHost);
        assertEquals(List.of("127.0.0.1:STATUS"), commands);
    }

    @Test
    void lineReaderRejectsOversizedPacketWithoutReadingRemainder() {
        byte[] oversized = new byte[MuleNetwork.MAX_PACKET_BYTES * 4];
        ByteArrayInputStream input = new ByteArrayInputStream(oversized);
        Socket socket = memorySocket(input);

        assertThrows(IOException.class, () -> MuleNetwork.readBoundedLine(socket, 1_000));

        assertEquals(oversized.length - MuleNetwork.MAX_PACKET_BYTES - 1, input.available());
    }

    @Test
    void lineReaderPreservesExactLimitAndCrlfCompatibility() throws Exception {
        String line = "x".repeat(MuleNetwork.MAX_PACKET_BYTES);
        assertEquals(line, MuleNetwork.readBoundedLine(memorySocket(new ByteArrayInputStream(
            (line + "\r\n").getBytes(StandardCharsets.UTF_8))), 1_000));
        assertTrue(MuleNetwork.parsePacket("x".repeat(MuleNetwork.MAX_PACKET_BYTES + 1)).isEmpty());
    }

    @Test
    void tcpClientRejectsOversizedReply() throws Exception {
        try (Peer peer = new Peer(socket -> {
            MuleNetwork.readBoundedLine(socket, 1_000);
            socket.getOutputStream().write(("x".repeat(MuleNetwork.MAX_PACKET_BYTES + 1) + "\n")
                .getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            MuleNetwork.MuleReply result = MuleNetwork.Client.sendTcp("127.0.0.1", peer.port(),
                MuleNetwork.formatRequest("STATUS", "TargetMule", "", 0), 1_000);
            assertFalse(result.ok);
            assertTrue(result.message.startsWith("Connection failed"));
        }
    }

    @Test
    void tcpClientRejectsTrickledReplyDespiteContinuousProgress() throws Exception {
        try (Peer peer = new Peer(socket -> {
            MuleNetwork.readBoundedLine(socket, 1_000);
            try {
                for (byte value : (wireReply(true, "TargetMule", 301) + "\n").getBytes(StandardCharsets.UTF_8)) {
                    socket.getOutputStream().write(value);
                    socket.getOutputStream().flush();
                    TimeUnit.MILLISECONDS.sleep(20);
                }
            } catch (IOException expectedAfterDeadline) {
                // The client closes this connection when the total deadline expires.
            }
        })) {
            MuleNetwork.MuleReply result = MuleNetwork.Client.sendTcp("127.0.0.1", peer.port(),
                MuleNetwork.formatRequest("STATUS", "TargetMule", "", 0), 150);
            assertFalse(result.ok);
            assertTrue(result.message.contains("SocketTimeoutException"));
        }
    }

    @Test
    void tcpClientAcceptsNormalReplyAndRejectsOversizedOutgoingPacket() throws Exception {
        AtomicInteger requestBytes = new AtomicInteger();
        try (Peer peer = new Peer(socket -> {
            String request = MuleNetwork.readBoundedLine(socket, 1_000);
            requestBytes.set(request.length());
            socket.getOutputStream().write((wireReply(true, "TargetMule", 301) + "\n")
                .getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            MuleNetwork.MuleReply result = MuleNetwork.Client.sendTcp("127.0.0.1", peer.port(),
                MuleNetwork.formatRequest("STATUS", "TargetMule", "", 0), 1_000);
            assertTrue(result.ok);
            assertTrue(requestBytes.get() > 0);
        }
        try (Peer peer = new Peer(socket -> assertNull(MuleNetwork.readBoundedLine(socket, 1_000)))) {
            assertFalse(MuleNetwork.Client.sendTcp("127.0.0.1", peer.port(),
                "x".repeat(MuleNetwork.MAX_PACKET_BYTES + 1), 1_000).ok);
        }
    }

    private static MuleNetwork.MuleReply reply(boolean ok, boolean ready, String mule, String host) {
        return new MuleNetwork.MuleReply(ok, ready, mule, 301, "", host, "Test reply");
    }

    private static String wireReply(boolean ok, String mule, int world) {
        return MuleNetwork.formatReply(ok, false, mule, world, 3164, 3487, 0, "", "Test reply");
    }

    private static Socket memorySocket(InputStream input) {
        return new Socket() {
            @Override public InputStream getInputStream() { return input; }
            @Override public void setSoTimeout(int timeout) { }
        };
    }

    @FunctionalInterface
    private interface PeerHandler {
        void handle(Socket socket) throws Exception;
    }

    /** All integration traffic stays on an ephemeral loopback listener. */
    private static final class Peer implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService worker;
        private final Future<?> result;

        Peer(PeerHandler handler) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            server.setSoTimeout(2_000);
            worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "MuleNetwork-Test-Peer");
                thread.setDaemon(true);
                return thread;
            });
            result = worker.submit(() -> {
                try (Socket socket = server.accept()) {
                    handler.handle(socket);
                } catch (Exception failed) {
                    throw new RuntimeException(failed);
                }
            });
        }

        int port() { return server.getLocalPort(); }

        @Override public void close() throws Exception {
            try {
                result.get(3, TimeUnit.SECONDS);
            } finally {
                server.close();
                worker.shutdownNow();
            }
        }
    }
}
