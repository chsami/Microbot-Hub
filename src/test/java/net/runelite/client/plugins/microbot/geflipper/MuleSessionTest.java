package net.runelite.client.plugins.microbot.geflipper;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleSessionTest {

    @Test
    void policyParsesHumanFriendlyGpFormatsAndComputesTransferExcess() {
        assertEquals(50_000_000, MulePolicy.parseGp("50M"));
        assertEquals(2_500_000, MulePolicy.parseGp("2.5m"));
        assertEquals(500_000, MulePolicy.parseGp("500k"));
        assertEquals(50_000_000, MulePolicy.parseGp("50,000,000 gp"));
        assertEquals(123_456, MulePolicy.parseGp("123456"));
        assertEquals(-1, MulePolicy.parseGp(""));
        assertEquals(-1, MulePolicy.parseGp("abc"));

        assertEquals(0, MulePolicy.calculateTransferAmount(49_999_999L, 50_000_000, 30_000_000));
        assertEquals(20_000_000, MulePolicy.calculateTransferAmount(50_000_000L, 50_000_000, 30_000_000));
        assertEquals(45_000_000, MulePolicy.calculateTransferAmount(75_000_000L, 50_000_000, 30_000_000));
        assertEquals(20_000_000, MulePolicy.calculateTransferAmount(50_000_000L, 30_000_000, 30_000_000));
        assertEquals(0, MulePolicy.calculateTransferAmount(30_000_000L, 30_000_000, 30_000_000));
        assertEquals(0, MulePolicy.calculateTransferAmount(30_000_999L, 30_000_000, 30_000_000));
        assertEquals(1_000, MulePolicy.calculateTransferAmount(30_001_000L, 30_000_000, 30_000_000));
    }

    @Test
    void liquidationEvaluatesPortfolioWealthVersusLooseCash() {
        assertTrue(MulePolicy.shouldLiquidateForMule(70_000_000L, 10_000_000L, false, 50_000_000, 30_000_000));
        assertFalse(MulePolicy.shouldLiquidateForMule(40_000_000L, 10_000_000L, false, 50_000_000, 30_000_000));
        assertFalse(MulePolicy.shouldLiquidateForMule(70_000_000L, 50_000_000L, false, 50_000_000, 30_000_000));
        assertTrue(MulePolicy.shouldLiquidateForMule(70_000_000L, 50_000_000L, true, 50_000_000, 30_000_000));
    }

    @Test
    void policyExtractsAndNormalizesTradePartnerNames() {
        assertEquals("god mule 1", MulePolicy.extractTradePartner("Trading With: God\u00a0Mule 1"));
        assertEquals("god mule 1", MulePolicy.extractTradePartner("Trading with:<br><col=ffffff>God_Mule 1</col>"));
        assertTrue(MulePolicy.sameRsn("God_Mule\u00a01", "god mule 1"));
    }

    @Test
    void lanServerAndClientCoordinateSingleActiveLeaseAcrossMultipleFlippers() throws Exception {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        long[] now = {1_000L};
        try (MuleNetwork.Server server = new MuleNetwork.Server(() -> now[0])) {
            assertTrue(server.ensureStarted(freePort));
            server.updateMuleState(true, "MyMule", 301, MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE);

            MuleNetwork.Client client1 = new MuleNetwork.Client();
            MuleNetwork.Client client2 = new MuleNetwork.Client();

            // Wrong mule name is rejected
            MuleNetwork.MuleReply badMule = client1.requestLease("127.0.0.1", freePort, "WrongMule", "FlipperOne", 20_000_000);
            assertFalse(badMule.ok);

            // FlipperOne acquires lease using 'auto' localhost resolution
            MuleNetwork.MuleReply granted = client1.requestLease("auto", freePort, "MyMule", "FlipperOne", 20_000_000);
            assertTrue(granted.ok);
            assertTrue(granted.ready);
            assertEquals("MyMule", granted.muleRsn);
            assertEquals(301, granted.world);

            // FlipperTwo is told the mule is busy while FlipperOne holds the lease
            MuleNetwork.MuleReply busy = client2.requestLease("127.0.0.1", freePort, "MyMule", "FlipperTwo", 15_000_000);
            assertFalse(busy.ok);
            assertTrue(busy.message.contains("FlipperOne"));

            // FlipperOne completes trade and releases lease; FlipperTwo can now acquire it
            assertTrue(client1.releaseLease("127.0.0.1", freePort, "MyMule", "FlipperOne", true).ok);
            MuleNetwork.MuleReply granted2 = client2.requestLease("127.0.0.1", freePort, "MyMule", "FlipperTwo", 15_000_000);
            assertTrue(granted2.ok);
            assertEquals("FlipperTwo", server.activeLease().traderRsn);
        }
    }

    @Test
    void cashFloorSenderSendsTheExcessKeepsThirtyMillionAndReturnsWithNormalCooldown() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");

        // Start on world 330 with 50M: the single 30M cash floor means send 20M and keep 30M.
        port.view = snapshot(101L, "FlipperAcc", 330, true, true, false, false,
            50_000_000, 0, false, false, "", 0, false, 0);

        MuleSession.SenderSettings settings = new MuleSession.SenderSettings(
            true, 30_000_000, 30_000_000, "BankMule", "auto", 43599, 0, MulePolicy.TileSpec.auto());

        // Debounce period (5s): returns false so normal flipping continues until lease is acquired
        assertFalse(session.tickSender(settings, false, 0L));
        assertTrue(session.tickSender(settings, false, 5_000L));
        assertEquals(MuleSession.Phase.PREPARING_CASH, session.phase());

        // PREPARING_CASH closes GE interface
        assertTrue(session.tickSender(settings, false, 6_000L));
        assertEquals(1, port.closedInterfaces);
        port.view = snapshot(101L, "FlipperAcc", 330, true, false, false, false,
            50_000_000, 0, false, false, "", 0, false, 0);
        assertTrue(session.tickSender(settings, false, 7_000L));
        assertEquals(MuleSession.Phase.HOPPING_TO_MULE, session.phase());

        // HOPPING_TO_MULE hops to 301 and then transitions through WALKING_TO_MULE to OPENING_TRADE
        assertTrue(session.tickSender(settings, false, 8_000L));
        assertEquals(301, port.lastHoppedWorld);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            50_000_000, 0, false, false, "", 0, false, 0);
        assertTrue(session.tickSender(settings, false, 9_000L));
        assertEquals(MuleSession.Phase.WALKING_TO_MULE, session.phase());
        assertTrue(session.tickSender(settings, false, 9_500L));
        assertEquals(MuleSession.Phase.OPENING_TRADE, session.phase());

        // OPENING_TRADE trades BankMule and enters OFFERING_COINS when Trademain opens
        assertTrue(session.tickSender(settings, false, 10_000L));
        assertEquals("BankMule", port.lastTradedPlayer);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            50_000_000, 0, true, false, "bankmule", 0, false, 0);
        assertTrue(session.tickSender(settings, false, 11_000L));
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());

        // OFFERING_COINS offers 20M and then accepts first screen
        assertTrue(session.tickSender(settings, false, 12_000L));
        assertEquals(20_000_000, port.lastOfferedCoins);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, true, false, "bankmule", 20_000_000, false, 0);
        assertTrue(session.tickSender(settings, false, 13_000L));
        assertEquals(1, port.acceptedMain);

        // Second trade screen opens -> transitions to CONFIRMING_TRADE and accepts confirm
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, true, "bankmule", 20_000_000, false, 0);
        assertTrue(session.tickSender(settings, false, 14_000L));
        assertEquals(MuleSession.Phase.CONFIRMING_TRADE, session.phase());
        assertTrue(session.tickSender(settings, false, 15_000L));
        assertEquals(1, port.acceptedConfirm);

        // Trade closes with 30M remaining -> transitions to RETURNING and hops back to 330
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, false, "", 0, false, 0);
        assertTrue(session.tickSender(settings, false, 16_000L));
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertEquals(MuleSession.Phase.RETURNING, session.phase());

        assertTrue(session.tickSender(settings, false, 17_000L));
        assertEquals(330, port.lastHoppedWorld);
        port.view = snapshot(101L, "FlipperAcc", 330, true, false, false, false,
            30_000_000, 0, false, false, "", 0, false, 0);
        assertFalse(session.tickSender(settings, false, 18_000L));
        assertEquals(MuleSession.Phase.IDLE, session.phase());

        // More cash arriving after the return must still honor the existing success cooldown and debounce.
        port.view = senderView(330, false);
        assertFalse(session.tickSender(settings, false, 19_000L));
        assertEquals(1, port.requestLeaseCalls);
        long retryAt = 16_000L + MuleSession.COOLDOWN_AFTER_SUCCESS_MS;
        assertFalse(session.tickSender(settings, false, retryAt - 1));
        assertFalse(session.tickSender(settings, false, retryAt));
        assertEquals(1, port.requestLeaseCalls);
        assertTrue(session.tickSender(settings, false, retryAt + MuleSession.ELIGIBLE_DEBOUNCE_MS));
        assertEquals(2, port.requestLeaseCalls);
        assertEquals(20_000_000, port.lastRequestedAmount);
    }

    @Test
    void nonliquidPortfolioExcessRequestsOnlyItsSurplusGpWithoutStartingATrade() {
        for (int cash : new int[]{0, 29_000_000, 30_000_000}) {
            FakePort port = new FakePort();
            MuleSession session = new MuleSession(port);
            port.view = new MuleSession.Snapshot(true, 101L, "FlipperAcc", 330,
                MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
                true, true, false, false, cash, 0, 100_000_000L, true,
                false, false, "", 0, false, 0);

            assertFalse(session.tickSender(senderSettings(true), false, 0L));
            assertFalse(session.tickSender(senderSettings(true), false, 60_000L));
            assertTrue(session.liquidationRequested(), "Above-limit invested capital needs preparation, not a cash-only trigger");
            assertEquals(70_000_000, session.liquidationTargetGp());
            assertEquals(MuleSession.Phase.IDLE, session.phase());
            assertEquals(0, port.requestLeaseCalls);
            assertEquals(0, port.closedInterfaces, "Normal flipping must retain control of the GE");
        }
    }

    @Test
    void totalWorkingCapitalTransfersExcessEvenWhenLooseGpIsBelowTheConfiguredLimit() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = capitalView(5_000_000, 55_000_000L, true, true);

        assertFalse(session.tickSender(senderSettings(true, 50_000_000), false, 0L));
        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 5_000L));
        assertEquals(5_000_000, port.lastRequestedAmount);
        assertEquals(MuleSession.Phase.PREPARING_CASH, session.phase());
        assertFalse(session.liquidationRequested());
    }

    @Test
    void preparationEndsAsSoonAsFullExcessIsLiquidWithoutSellingTheKeptCapital() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = capitalView(10_000_000, 75_000_000L, true, true);
        assertFalse(session.tickSender(senderSettings(true, 50_000_000), false, 0L));
        assertTrue(session.liquidationRequested());
        assertEquals(25_000_000, session.liquidationTargetGp());
        assertEquals(0, port.requestLeaseCalls);

        port.view = capitalView(25_000_000, 75_000_000L, true, true);
        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 600L));
        assertEquals(25_000_000, port.lastRequestedAmount);
        assertFalse(session.liquidationRequested());
        assertEquals(0, session.liquidationTargetGp());
    }

    @Test
    void preparationTimesOutWithoutSilentlySendingPartialCash() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.view = capitalView(10_000_000, 75_000_000L, true, true);
        session.tickSender(senderSettings(true, 50_000_000), false, 0L);
        assertTrue(session.liquidationRequested());

        assertFalse(session.tickSender(senderSettings(true, 50_000_000), false, MuleSession.LIQUIDATE_TIMEOUT_MS + 1));
        assertFalse(session.liquidationRequested());
        assertEquals(0, port.requestLeaseCalls);
        assertTrue(session.status().contains("did not become liquid"));
        assertFalse(session.tickSender(senderSettings(true, 50_000_000), false, MuleSession.LIQUIDATE_TIMEOUT_MS + 2_000));
        assertFalse(session.liquidationRequested(), "Timed-out preparation must honor its normal cooldown");
    }

    @Test
    void unsafeValuationCannotStartATransferButASafePartialLowerBoundCan() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.view = capitalView(5_000_000, 55_000_000L, false, false);
        session.tickSender(senderSettings(true, 50_000_000), false, 0L);
        session.tickSender(senderSettings(true, 50_000_000), false, 5_000L);
        assertEquals(0, port.requestLeaseCalls);
        assertFalse(session.liquidationRequested());

        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = capitalView(5_000_000, 55_000_000L, true, false);
        assertFalse(session.tickSender(senderSettings(true, 50_000_000), false, 6_000L));
        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 11_000L));
        assertEquals(5_000_000, port.lastRequestedAmount);
    }

    @Test
    void investedCapitalIsRetainedWhileTheLiquidExcessPassesBothTradeScreens() {
        FakePort port = new FakePort();
        MuleSession session = senderWithInvestedCapitalAtOpenTrade(port);
        MuleSession.SenderSettings settings = senderSettings(true, 50_000_000);
        session.tickSender(settings, false, 11_000L);
        assertEquals(5_000_000, port.lastOfferedCoins);

        port.view = investedTradeView(301, 0, 50_000_000L, true, false, 5_000_000);
        session.tickSender(settings, false, 12_000L);
        assertEquals(1, port.acceptedMain, "The kept 50M can be invested, rather than loose GP");
        port.view = investedTradeView(301, 0, 50_000_000L, false, true, 5_000_000);
        session.tickSender(settings, false, 13_000L);
        session.tickSender(settings, false, 14_000L);
        assertEquals(1, port.acceptedConfirm);

        port.view = investedTradeView(301, 0, 50_000_000L, false, false, 0);
        session.tickSender(settings, false, 15_000L);
        assertEquals(5_000_000L, session.totalTransferredGp());
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void verifiedCapitalDropCancelsBeforeAcceptanceEvenWithEnoughTransferCash() {
        FakePort port = new FakePort();
        MuleSession session = senderWithInvestedCapitalAtOpenTrade(port);
        port.view = investedTradeView(301, 5_000_000, 54_000_000L, true, false, 0);

        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L);
        assertEquals(0, port.lastOfferedCoins, "A low reading pauses every trade step at once");
        assertEquals(0, port.acceptedMain);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());

        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L + MuleSession.CAPITAL_RECHECK_MS);
        assertEquals(0, port.lastOfferedCoins);
        assertEquals(0, port.acceptedMain);
        assertEquals(1, port.closedInterfaces);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void aBriefLowReadingAfterTheHopDoesNotSendTheFlipperHome() {
        // Straight after a world hop the game shows every GE slot as empty, so the invested 50M disappears
        // from the estimate for a moment. That must not cancel the transfer and hop the Flipper back.
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        MuleSession.SenderSettings settings = senderSettings(true, 50_000_000);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = investedTradeView(330, 5_000_000, 55_000_000L, false, false, 0);
        session.tickSender(settings, false, 0L);
        session.tickSender(settings, false, 5_000L);
        session.tickSender(settings, false, 6_000L);
        session.tickSender(settings, false, 7_000L);
        assertEquals(301, port.lastHoppedWorld);

        port.view = investedTradeView(301, 5_000_000, 5_000_000L, false, false, 0);
        assertTrue(session.tickSender(settings, false, 8_000L));
        assertEquals(MuleSession.Phase.HOPPING_TO_MULE, session.phase(), "Held, not cancelled");
        assertTrue(session.tickSender(settings, false, 9_000L));
        assertEquals(301, port.lastHoppedWorld, "No hop back home");

        port.view = investedTradeView(301, 5_000_000, 55_000_000L, false, false, 0);
        session.tickSender(settings, false, 9_600L);
        assertEquals(MuleSession.Phase.WALKING_TO_MULE, session.phase());
        session.tickSender(settings, false, 10_200L);
        assertEquals(MuleSession.Phase.OPENING_TRADE, session.phase());
        assertEquals(301, port.lastHoppedWorld);
    }

    @Test
    void aLowReadingThatStartsOverOnANewWorldIsTimedFromThatWorld() {
        FakePort port = new FakePort();
        MuleSession session = senderWithInvestedCapitalAtOpenTrade(port);
        MuleSession.SenderSettings settings = senderSettings(true, 50_000_000);
        port.view = investedTradeView(301, 5_000_000, 5_000_000L, true, false, 0);
        session.tickSender(settings, false, 11_000L);
        port.view = investedTradeView(302, 5_000_000, 5_000_000L, false, false, 0);
        session.tickSender(settings, false, 11_000L + MuleSession.CAPITAL_RECHECK_MS);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase(),
            "A reading on a different world has its own grace period");
        session.tickSender(settings, false, 11_000L + 2 * MuleSession.CAPITAL_RECHECK_MS);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void investedAssetValueCannotReplaceTheGpNeededForTheLockedTransfer() {
        FakePort port = new FakePort();
        MuleSession session = senderWithInvestedCapitalAtOpenTrade(port);
        port.view = investedTradeView(301, 4_000_000, 55_000_000L, true, false, 0);

        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L);
        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L + MuleSession.CAPITAL_RECHECK_MS);
        assertEquals(0, port.lastOfferedCoins);
        assertEquals(0, port.acceptedMain);
        assertEquals(1, port.closedInterfaces);
    }

    @Test
    void cashFloorRequiresAtLeastOneThousandExcessBeforeDebouncingALease() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = cashFloorView(30_000_000);
        assertFalse(session.tickSender(senderSettings(true), false, 0L));
        assertFalse(session.tickSender(senderSettings(true), false, 10_000L));
        assertEquals(0, port.requestLeaseCalls);

        port.view = cashFloorView(30_000_999);
        assertFalse(session.tickSender(senderSettings(true), false, 11_000L));
        assertEquals(0, port.requestLeaseCalls);

        port.view = cashFloorView(30_001_000);
        assertFalse(session.tickSender(senderSettings(true), false, 12_000L));
        assertFalse(session.tickSender(senderSettings(true), false, 16_999L));
        assertEquals(0, port.requestLeaseCalls);
        assertTrue(session.tickSender(senderSettings(true), false, 17_000L));
        assertEquals(1, port.requestLeaseCalls);
        assertEquals(1_000, port.lastRequestedAmount);
        assertFalse(session.liquidationRequested());
    }

    @Test
    void raisingCashFloorCancelsTheOldAmountBeforeOfferingCoinsAndReturnsHome() {
        FakePort port = new FakePort();
        MuleSession session = senderAtOpenTrade(port);
        MuleSession.SenderSettings higherFloor = senderSettings(true, 40_000_000);

        assertTrue(session.tickSender(higherFloor, false, 11_000L));
        assertEquals(0, port.lastOfferedCoins, "The old 20M transfer would leave less than the new 40M floor");
        assertEquals(0, port.acceptedMain);
        assertEquals(0, port.acceptedConfirm);
        assertEquals(1, port.closedInterfaces);
        assertEquals(List.of(false), port.releasedLeases);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());

        port.view = senderView(301, false);
        assertTrue(session.tickSender(higherFloor, false, 12_000L));
        assertEquals(330, port.lastHoppedWorld);
        port.view = senderView(330, false);
        assertFalse(session.tickSender(higherFloor, false, 13_000L));
        assertFalse(session.tickSender(higherFloor, false, 20_000L));
        assertEquals(1, port.requestLeaseCalls, "Floor-change cancellation retains the abort cooldown");
    }

    @Test
    void loweringCashFloorCancelsTheExistingOfferInsteadOfChangingItsAmountInPlace() {
        FakePort port = new FakePort();
        MuleSession session = senderAtOpenTrade(port);
        assertTrue(session.tickSender(senderSettings(true), false, 11_000L));
        assertEquals(20_000_000, port.lastOfferedCoins);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, true, false, "BankMule", 20_000_000, false, 0);

        assertTrue(session.tickSender(senderSettings(true, 20_000_000), false, 12_000L));
        assertEquals(20_000_000, port.lastOfferedCoins, "A new cash floor must not rewrite an in-flight offer");
        assertEquals(0, port.acceptedMain);
        assertEquals(0, port.acceptedConfirm);
        assertEquals(1, port.closedInterfaces);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertEquals(0, session.totalTransferredGp());
    }

    @Test
    void recipientOrEndpointChangeCancelsBeforeOfferingAndReleasesTheOriginalReservation() {
        for (MuleSession.SenderSettings changed : List.of(
            new MuleSession.SenderSettings(true, 30_000_000, 30_000_000, "OtherMule", "auto", 43599, 0, MulePolicy.TileSpec.auto()),
            new MuleSession.SenderSettings(true, 30_000_000, 30_000_000, "BankMule", "auto", 43600, 0, MulePolicy.TileSpec.auto()),
            new MuleSession.SenderSettings(true, 30_000_000, 30_000_000, "BankMule", "192.0.2.3", 43599, 0, MulePolicy.TileSpec.auto()),
            new MuleSession.SenderSettings(true, 30_000_000, 30_000_000, "BankMule", "auto", 43599, 302, MulePolicy.TileSpec.auto()))) {
            FakePort port = new FakePort();
            MuleSession session = senderAtOpenTrade(port);
            session.tickSender(changed, false, 11_000L);
            assertEquals(0, port.lastOfferedCoins);
            assertEquals(0, port.acceptedMain);
            assertEquals(0, port.acceptedConfirm);
            assertEquals(MuleSession.Phase.RETURNING, session.phase());
            assertEquals(List.of("127.0.0.1:43599:BankMule:false"), port.releaseTargets);
            assertEquals(1, port.closedInterfaces);
        }
    }

    @Test
    void recipientChangeAfterCommittedTradeStillBooksAndReleasesTheOriginalEndpoint() {
        FakePort port = new FakePort();
        MuleSession session = senderAtOpenTrade(port);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, true, "BankMule", 20_000_000, false, 0);
        session.tickSender(senderSettings(true), false, 11_000L);
        session.tickSender(senderSettings(true), false, 12_000L);
        assertEquals(1, port.acceptedConfirm);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, false, "", 0, false, 0);
        MuleSession.SenderSettings changed = new MuleSession.SenderSettings(true, 30_000_000, 30_000_000,
            "OtherMule", "192.0.2.3", 43600, 0, MulePolicy.TileSpec.auto());
        session.tickSender(changed, false, 13_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertEquals(List.of("127.0.0.1:43599:BankMule:true"), port.releaseTargets);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void recipientChangeOnTheFinalScreenDeclinesWithoutConfirmingThePreviousMule() {
        FakePort port = new FakePort();
        MuleSession session = senderAtOpenTrade(port);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, true, "BankMule", 20_000_000, false, 0);
        session.tickSender(senderSettings(true), false, 11_000L);
        assertEquals(MuleSession.Phase.CONFIRMING_TRADE, session.phase());
        MuleSession.SenderSettings changed = new MuleSession.SenderSettings(true, 30_000_000, 30_000_000,
            "OtherMule", "auto", 43599, 0, MulePolicy.TileSpec.auto());
        session.tickSender(changed, false, 12_000L);
        assertEquals(0, port.acceptedConfirm);
        assertEquals(1, port.closedInterfaces);
        assertEquals(List.of(false), port.releasedLeases);
    }

    @Test
    void floorChangeAfterCommittedTradeStillRecordsTheTransferAndReturnsSafely() {
        FakePort port = new FakePort();
        MuleSession session = senderAtOpenTrade(port);
        session.tickSender(senderSettings(true), false, 11_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, true, false, "BankMule", 20_000_000, false, 0);
        session.tickSender(senderSettings(true), false, 12_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, true, "BankMule", 20_000_000, false, 0);
        session.tickSender(senderSettings(true), false, 13_000L);
        session.tickSender(senderSettings(true), false, 14_000L);
        assertEquals(1, port.acceptedConfirm);

        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            30_000_000, 0, false, false, "", 0, false, 0);
        MuleSession.SenderSettings higherFloor = senderSettings(true, 40_000_000);
        long completedAt = 13_000L + MuleSession.PHASE_TIMEOUT_MS + 1;
        assertTrue(session.tickSender(higherFloor, false, completedAt));
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertEquals(List.of(true), port.releasedLeases, "A committed transfer cannot be cancelled retroactively");
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertTrue(session.tickSender(higherFloor, false, completedAt + 1_000L));
        assertEquals(330, port.lastHoppedWorld);
        port.view = snapshot(101L, "FlipperAcc", 330, true, false, false, false,
            30_000_000, 0, false, false, "", 0, false, 0);
        assertFalse(session.tickSender(higherFloor, false, completedAt + 2_000L));
        assertFalse(session.tickSender(higherFloor, false, completedAt + 2_000L + MuleSession.COOLDOWN_AFTER_SUCCESS_MS));
        assertEquals(1, port.requestLeaseCalls, "The new floor applies to future eligibility");
    }

    @Test
    void cashDropBeforeOfferingCancelsRatherThanSpendBelowTheWorkingFloor() {
        FakePort port = new FakePort();
        MuleSession session = senderWithFiftyMillionFloorAtOpenTrade(port);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            45_000_000, 0, true, false, "BankMule", 0, false, 0);

        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 11_000L));
        assertEquals(0, port.lastOfferedCoins);
        assertEquals(0, port.closedInterfaces, "Paused while it re-checks");
        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 11_000L + MuleSession.CAPITAL_RECHECK_MS));
        assertEquals(0, port.lastOfferedCoins);
        assertEquals(0, port.acceptedMain);
        assertEquals(0, port.acceptedConfirm);
        assertEquals(1, port.closedInterfaces);
        assertEquals(List.of(false), port.releasedLeases);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void cashDropWhileCoinsAreOfferedCancelsBeforeAcceptingTheTrade() {
        FakePort port = new FakePort();
        MuleSession session = senderWithFiftyMillionFloorAtOpenTrade(port);
        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L);
        assertEquals(5_000_000, port.lastOfferedCoins);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            40_000_000, 0, true, false, "BankMule", 5_000_000, false, 0);

        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 12_000L));
        assertEquals(0, port.acceptedMain, "Never accepts while the reading is low");
        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 12_000L + MuleSession.CAPITAL_RECHECK_MS));
        assertEquals(0, port.acceptedMain);
        assertEquals(0, port.acceptedConfirm);
        assertEquals(1, port.closedInterfaces);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
    }

    @Test
    void ordinaryOfferedCoinsAreIncludedOnceSoTheTransferDoesNotConsumeTheFloor() {
        FakePort port = new FakePort();
        MuleSession session = senderWithFiftyMillionFloorAtOpenTrade(port);
        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            50_000_000, 0, true, false, "BankMule", 5_000_000, false, 0);

        assertTrue(session.tickSender(senderSettings(true, 50_000_000), false, 12_000L));
        assertEquals(1, port.acceptedMain);
        assertEquals(0, port.closedInterfaces);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            50_000_000, 0, false, true, "BankMule", 5_000_000, false, 0);
        session.tickSender(senderSettings(true, 50_000_000), false, 13_000L);
        session.tickSender(senderSettings(true, 50_000_000), false, 14_000L);
        assertEquals(1, port.acceptedConfirm);
    }

    @Test
    void muleReceiverAcceptsAuthorizedTradeAndDeclinesStrangers() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.activeLease = new MuleNetwork.Lease("FlipperAcc", 20_000_000, 100_000L);
        MuleSession.ReceiverSettings settings = new MuleSession.ReceiverSettings(
            43599, 0, MulePolicy.TileSpec.auto(), "BankMule");

        // Stranger trades the mule -> declined immediately
        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, true, false, "random stranger", 0, false, 20_000_000);
        session.tickReceiver(settings, 10_000L);
        assertEquals(1, port.declined);

        // Authorized FlipperAcc offers 20M -> accepted on main and confirm screens
        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, false, false, "", 0, false, 0);
        session.tickReceiver(settings, 11_000L);

        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, true, false, "flipperacc", 0, false, 20_000_000);
        session.tickReceiver(settings, 12_000L);
        assertEquals(1, port.acceptedMain);

        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, false, true, "flipperacc", 0, false, 20_000_000);
        session.tickReceiver(settings, 13_000L);
        assertEquals(1, port.acceptedConfirm);

        // Trade finishes and inventory coins increase by 20M -> recorded in totalTransferredGp
        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            25_000_000, 0, false, false, "", 0, false, 0);
        session.tickReceiver(settings, 14_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertNull(port.activeLease);
    }

    @Test
    void finishRequestReturnsSenderHomeInsteadOfRepeatingAbort() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);

        assertTrue(session.tickSender(senderSettings(true), true, 9_000L));
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertEquals(330, port.lastHoppedWorld);
        assertEquals(List.of(false), port.releasedLeases);

        port.view = senderView(330, false);
        assertFalse(session.tickSender(senderSettings(true), true, 10_000L));
        assertEquals(MuleSession.Phase.IDLE, session.phase());
        assertEquals(1, port.releasedLeases.size(), "Returning must not repeatedly abort the lease");
    }

    @Test
    void disablingMulingStillCompletesSenderReturn() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);

        assertTrue(session.tickSender(senderSettings(false), false, 9_000L));
        assertEquals(330, port.lastHoppedWorld);
        port.view = senderView(330, false);
        assertFalse(session.tickSender(senderSettings(false), false, 10_000L));
        assertEquals(MuleSession.Phase.IDLE, session.phase());
    }

    @Test
    void returnClosesTradeBeforeHoppingAndDoesNotDiscardItsDestination() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        port.view = senderView(301, true);
        port.lastHoppedWorld = 0;

        assertTrue(session.tickSender(senderSettings(true), true, 9_000L));
        assertEquals(1, port.closedInterfaces);
        assertEquals(0, port.lastHoppedWorld, "A visible trade must close before a world hop");
        assertEquals(MuleSession.Phase.RETURNING, session.phase());

        port.view = senderView(301, false);
        assertTrue(session.tickSender(senderSettings(true), true, 10_000L));
        assertEquals(330, port.lastHoppedWorld);
    }

    @Test
    void cancellationClosesSameWorldTradeBeforeCompleting() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        port.view = senderView(330, true);

        assertTrue(session.tickSender(senderSettings(false), false, 9_000L));
        assertEquals(1, port.closedInterfaces);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        port.view = senderView(330, false);
        assertFalse(session.tickSender(senderSettings(false), false, 10_000L));
    }

    @Test
    void accountChangeDoesNotApplyPreviousAccountsReturnJourney() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        port.view = snapshot(999L, "NewAccount", 302, true, false, false, false,
            50_000_000, 0, false, false, "", 0, false, 0);
        port.lastHoppedWorld = 0;

        assertFalse(session.tickSender(senderSettings(true), true, 9_000L));
        assertEquals(MuleSession.Phase.IDLE, session.phase());
        assertEquals(0, port.lastHoppedWorld);
    }

    @Test
    void cancellationUsesOneVerifiedSnapshotAndKeepsReturnContextAcrossTheNextUnknownRead() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        int previousReads = port.readCalls;
        port.sequentialReads.add(senderView(301, false));
        port.sequentialReads.add(null);

        assertTrue(session.tickSender(senderSettings(true), true, 9_000L));
        assertEquals(previousReads + 1, port.readCalls, "Cancellation must reuse its verified snapshot");
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertEquals(330, port.lastHoppedWorld);

        port.lastHoppedWorld = 0;
        assertTrue(session.tickSender(senderSettings(true), true, 10_000L));
        assertEquals(MuleSession.Phase.RETURNING, session.phase(), "Unknown reads must not erase the return destination");
        assertEquals(0, port.lastHoppedWorld);

        assertTrue(session.tickSender(senderSettings(true), true, 22_000L));
        assertEquals(330, port.lastHoppedWorld);
        port.view = senderView(330, false);
        assertFalse(session.tickSender(senderSettings(true), true, 23_000L));
    }

    @Test
    void replacementAccountAfterAnUnknownReturnReadIsNeverMovedForTheOldAccount() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        port.sequentialReads.add(senderView(301, false));
        port.sequentialReads.add(null);
        assertTrue(session.tickSender(senderSettings(true), true, 9_000L));
        assertTrue(session.tickSender(senderSettings(true), true, 10_000L));

        port.view = snapshot(999L, "NewAccount", 302, true, false, false, false,
            50_000_000, 0, false, false, "", 0, false, 0);
        port.lastHoppedWorld = 0;
        assertFalse(session.tickSender(senderSettings(true), true, 22_000L));
        assertEquals(MuleSession.Phase.IDLE, session.phase());
        assertEquals(0, port.lastHoppedWorld);
    }

    @Test
    void timedOutTransferKeepsItsCooldownAndReturnContextAcrossAnUnknownRead() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        int previousReads = port.readCalls;
        int previousRequests = port.requestLeaseCalls;
        port.sequentialReads.add(senderView(301, false));
        port.sequentialReads.add(null);
        long timeout = 8_000L + MuleSession.PHASE_TIMEOUT_MS + 1;

        assertTrue(session.tickSender(senderSettings(true), false, timeout));
        assertEquals(previousReads + 1, port.readCalls);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertTrue(session.tickSender(senderSettings(true), false, timeout + 600L));
        assertEquals(MuleSession.Phase.RETURNING, session.phase());

        port.view = senderView(330, false);
        assertFalse(session.tickSender(senderSettings(true), false, timeout + 1_200L));
        assertFalse(session.tickSender(senderSettings(true), false, timeout + 6_000L));
        assertEquals(previousRequests, port.requestLeaseCalls, "Returning must retain the timeout cooldown");
    }

    @Test
    void receiverCountsCoinsWhenSenderCompletedTheLeaseBeforeItsNextTick() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.activeLease = null; // Sender's COMPLETE_TRADE packet won the scheduler race.
        port.view = receiverView(false, false, 25_000_000, "", 0);

        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        session.tickReceiver(receiverSettings(), 14_600L);
        assertEquals(20_000_000L, session.totalTransferredGp(), "Receipt must be counted only once");
    }

    @Test
    void previousReceiptDoesNotClearADifferentSendersNewLease() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        MuleNetwork.Lease nextLease = new MuleNetwork.Lease("NextFlipper", 10_000_000, 120_000L);
        port.activeLease = nextLease;
        port.view = receiverView(false, false, 25_000_000, "", 0);

        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertSame(nextLease, port.activeLease);
    }

    @Test
    void receiptClearCannotRemoveAReservationThatReplacedItsSnapshotDuringAccounting() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        MuleNetwork.Lease replacement = new MuleNetwork.Lease("NextFlipper", 10_000_000, 120_000L);
        port.beforeLeaseClear = () -> port.activeLease = replacement;
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        assertSame(replacement, port.activeLease);
    }

    @Test
    void receiverWaitsForDelayedInventoryCreditAfterTheConfirmedTradeCloses() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.activeLease = null;
        port.view = receiverView(false, false, 5_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(0, session.totalTransferredGp());

        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_600L);
        assertEquals(20_000_000L, session.totalTransferredGp());
    }

    @Test
    void receiverPreservesConfirmedReceiptAcrossAnUnknownReadBeforeLeaseRemoval() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.view = null;
        session.tickReceiver(receiverSettings(), 13_000L);
        assertFalse(port.loggedOut);
        assertEquals(0, port.lastLoginWorld);
        assertEquals(0, session.totalTransferredGp());

        port.activeLease = null;
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        session.tickReceiver(receiverSettings(), 14_600L);
        assertEquals(20_000_000L, session.totalTransferredGp(), "Unknown is not an observed logout");
    }

    @Test
    void receiverDoesNotAttributeAnExpiredCandidatesLaterCoinIncreaseToTheTrade() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.activeLease = null;
        port.view = receiverView(false, false, 5_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        session.tickReceiver(receiverSettings(), 14_000L + MuleSession.RECEIPT_SETTLE_MS);

        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 20_000L);
        assertEquals(0, session.totalTransferredGp());
    }

    @Test
    void receiverRequiresAFinalConfirmationAttemptBeforeCountingAReceipt() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.acceptConfirmResult = false;
        observeReceiverTrade(session, port);
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(0, session.totalTransferredGp());
    }

    @Test
    void receiverDoesNotCountACancelledAuthorizedFirstScreenAsAReceipt() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.view = receiverView(true, false, 5_000_000, "FlipperAcc", 20_000_000);
        session.tickReceiver(receiverSettings(), 10_000L);
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 11_000L);
        assertEquals(0, session.totalTransferredGp());
    }

    @Test
    void receiverDoesNotCarryAReceiptCandidateAcrossAccounts() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.view = snapshot(999L, "NewMule", 301, true, false, false, false,
            25_000_000, 0, false, false, "", 0, false, 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(0, session.totalTransferredGp());
    }

    @Test
    void receiverDiscardsReceiptCandidateAcrossAnObservedLogout() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        observeReceiverTrade(session, port);
        port.activeLease = null;
        port.view = MuleSession.Snapshot.loggedOut();
        session.tickReceiver(receiverSettings(), 14_000L);

        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 15_000L);
        assertEquals(0, session.totalTransferredGp(), "A later login must not reuse the previous login's baseline");
    }

    @Test
    void receiverDoesNotCountAStrangersCancelledTradeAsAReceipt() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.view = receiverView(true, false, 5_000_000, "Stranger", 20_000_000);
        session.tickReceiver(receiverSettings(), 10_000L);
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 11_000L);

        assertEquals(0, session.totalTransferredGp());
        assertEquals(1, port.declined);
    }

    @Test
    void receiverAcceptsOneFreshAuthorizedInvitationAndNeverRequestsWithoutOne() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        session.tickReceiver(receiverSettings(), 10_000L);
        assertEquals(0, port.tradePlayerCalls);

        session.recordTradeRequest("FlipperAcc", 202L, 10_500L);
        session.tickReceiver(receiverSettings(), 11_000L);
        assertEquals(1, port.tradePlayerCalls);
        assertTrue(MulePolicy.sameRsn("FlipperAcc", port.lastTradedPlayer));
        session.tickReceiver(receiverSettings(), 11_600L);
        assertEquals(1, port.tradePlayerCalls, "One invitation must not produce repeated reciprocal actions");
    }

    @Test
    void receiverRejectsStrangerWrongAccountStaleAndFutureInvitations() {
        String[] requesters = {"Stranger", "FlipperAcc", "FlipperAcc", "FlipperAcc", ""};
        long[] accounts = {202L, 303L, 202L, 202L, 202L};
        long[] times = {11_000L, 11_000L, 1_000L, 13_000L, 11_000L};
        for (int i = 0; i < requesters.length; i++) {
            FakePort port = receiverPort();
            MuleSession session = new MuleSession(port);
            session.recordTradeRequest(requesters[i], accounts[i], times[i]);
            session.tickReceiver(receiverSettings(), 12_001L);
            assertEquals(0, port.tradePlayerCalls, "Invalid invitation case " + i);
        }
    }

    @Test
    void receiverRechecksLeaseAndProximityBeforeReciprocatingInvitation() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        session.recordTradeRequest("FlipperAcc", 202L, 10_000L);
        port.activeLease = new MuleNetwork.Lease("OtherFlipper", 20_000_000, 100_000L);
        session.tickReceiver(receiverSettings(), 10_500L);
        assertEquals(0, port.tradePlayerCalls);

        port.activeLease = new MuleNetwork.Lease("FlipperAcc", 20_000_000, 100_000L);
        session.recordTradeRequest("FlipperAcc", 202L, 11_000L);
        port.playerNearby = false;
        session.tickReceiver(receiverSettings(), 11_500L);
        assertEquals(0, port.tradePlayerCalls);
    }

    @Test
    void stoppingReceiverClearsPendingInvitationAndRememberedIdentity() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.view = receiverView(false, false, 5_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 10_000L);
        session.recordTradeRequest("FlipperAcc", 202L, 10_500L);
        session.stopReceiver();

        assertFalse(port.serverRunning);
        assertNull(port.activeLease);
        port.view = MuleSession.Snapshot.loggedOut();
        session.tickReceiver(new MuleSession.ReceiverSettings(43599, 0, MulePolicy.TileSpec.auto(), "NewMule"), 11_000L);
        assertEquals("NewMule", port.lastServerRsn);
        assertEquals(0, port.tradePlayerCalls);
    }

    @Test
    void receiverFinishStopsServerAndWaitsUntilTheTradeIsActuallyClosed() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.view = receiverView(true, false, 5_000_000, "FlipperAcc", 20_000_000);

        assertFalse(session.finishReceiver(10_000L));
        assertFalse(port.serverRunning);
        assertEquals(1, port.declined);
        port.view = receiverView(false, false, 5_000_000, "", 0);
        assertTrue(session.finishReceiver(11_000L));
    }

    @Test
    void receiverFinishDoesNotDeclareUnknownOrOfferedTradeStateComplete() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.view = null;
        assertFalse(session.finishReceiver(10_000L));
        port.view = snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, false, false, "", 1, false, 0);
        assertFalse(session.finishReceiver(11_000L));
        port.view = MuleSession.Snapshot.loggedOut();
        assertTrue(session.finishReceiver(12_000L));
    }

    @Test
    void receiverFinishWaitsForAllowedClosedState() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        port.allowed = false;
        assertFalse(session.finishReceiver(10_000L));
        port.allowed = true;
        assertTrue(session.finishReceiver(10_600L));
    }

    // ---- Mule now and Keep during update ----

    private static final MuleNetwork.MuleReply READY_MULE =
        new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");

    private static MuleSession.SenderSettings keepSettings(int keep, int updateKeep) {
        return new MuleSession.SenderSettings(true, 0, keep, updateKeep, "BankMule", "auto",
            43599, 0, MulePolicy.TileSpec.auto());
    }

    @Test
    void muleNowSendsTheAmountAtOnceWhateverTheTriggerAndKeepAmount() {
        FakePort port = new FakePort();
        port.muleReply = READY_MULE;
        MuleSession session = new MuleSession(port);
        // Keep 150M with an 86M total: automatic muling would never send anything.
        MuleSession.SenderSettings settings = keepSettings(150_000_000, -1);
        port.view = capitalView(30_000_000, 86_000_000L, true, true);
        assertFalse(session.tickSender(settings, false, 0L));
        assertEquals(0, port.requestLeaseCalls);

        session.requestManualTransfer(20_000_000);
        assertTrue(session.manualTransferPending());
        assertTrue(session.tickSender(settings, false, 1_000L), "No debounce or trigger for Mule now");
        assertEquals(20_000_000, port.lastRequestedAmount);
        assertEquals(MuleSession.Phase.PREPARING_CASH, session.phase());
        assertFalse(session.manualTransferPending());
        assertTrue(session.status().contains("Mule now"), session.status());

        // The keep amount changing mid-transfer belongs to automatic muling; it doesn't cancel Mule now.
        session.tickSender(keepSettings(60_000_000, -1), false, 2_000L);
        assertNotEquals(MuleSession.Phase.IDLE, session.phase());
        assertNotEquals(MuleSession.Phase.RETURNING, session.phase());
        assertFalse(session.status().contains("limit changed"), session.status());
    }

    @Test
    void muleNowFreesUpGpFirstWhenNotEnoughIsLoose() {
        FakePort port = new FakePort();
        port.muleReply = READY_MULE;
        MuleSession session = new MuleSession(port);
        MuleSession.SenderSettings settings = keepSettings(150_000_000, -1);
        port.view = capitalView(5_000_000, 86_000_000L, true, true);
        session.requestManualTransfer(20_000_000);
        assertFalse(session.tickSender(settings, false, 1_000L));
        assertTrue(session.liquidationRequested(), "Cancel buys and sell down first");
        assertEquals(20_000_000, session.liquidationTargetGp());
        assertTrue(session.status().contains("freeing up"), session.status());
        assertEquals(0, port.requestLeaseCalls);

        port.view = capitalView(25_000_000, 86_000_000L, true, true);
        assertTrue(session.tickSender(settings, false, 2_000L));
        assertEquals(20_000_000, port.lastRequestedAmount);
        assertFalse(session.liquidationRequested());
    }

    @Test
    void muleNowThatCanNotRunEndsWithAReason() {
        FakePort port = new FakePort();
        port.muleReply = READY_MULE;
        MuleSession.SenderSettings settings = keepSettings(50_000_000, -1);

        MuleSession tooMuch = new MuleSession(port);
        port.view = capitalView(5_000_000, 10_000_000L, true, true);
        tooMuch.requestManualTransfer(20_000_000);
        assertFalse(tooMuch.tickSender(settings, false, 1_000L));
        assertFalse(tooMuch.manualTransferPending());
        assertFalse(tooMuch.liquidationRequested());
        assertTrue(tooMuch.status().contains("more than your total"), tooMuch.status());

        MuleSession stalled = new MuleSession(port);
        port.view = capitalView(5_000_000, 86_000_000L, true, true);
        stalled.requestManualTransfer(20_000_000);
        stalled.tickSender(settings, false, 1_000L);
        stalled.liquidationStalled(2_000L); // everything listed, sales not in yet
        assertFalse(stalled.manualTransferPending());
        assertFalse(stalled.liquidationRequested());
        assertTrue(stalled.status().contains("Press Mule now again"), stalled.status());

        FakePort offlinePort = new FakePort(); // its Mule doesn't answer
        MuleSession offline = new MuleSession(offlinePort);
        offlinePort.view = capitalView(30_000_000, 86_000_000L, true, true);
        offline.requestManualTransfer(20_000_000);
        assertFalse(offline.tickSender(settings, false, 1_000L));
        assertFalse(offline.manualTransferPending(), "No endless retries for a button press");
        assertTrue(offline.status().startsWith("Mule now: "), offline.status());
        assertTrue(offline.status().contains("try again"), offline.status());

        MuleSession mulingOff = new MuleSession(port);
        port.view = capitalView(30_000_000, 86_000_000L, true, true);
        mulingOff.requestManualTransfer(20_000_000);
        assertFalse(mulingOff.tickSender(senderSettings(false, 50_000_000), false, 1_000L));
        assertFalse(mulingOff.manualTransferPending());

        MuleSession tiny = new MuleSession(port);
        tiny.requestManualTransfer(500);
        assertFalse(tiny.manualTransferPending(), "Below the smallest transfer");
    }

    @Test
    void keepDuringUpdateSetsWhatTheUpdateTransferKeeps() {
        // 30M loose of a 60M total, keep 50M: the update transfer normally sends the 10M above it.
        int[][] cases = {{-1, 10_000_000}, {40_000_000, 20_000_000}, {0, 30_000_000}};
        for (int[] c : cases) {
            FakePort port = new FakePort();
            port.muleReply = READY_MULE;
            MuleSession session = new MuleSession(port);
            port.view = capitalView(30_000_000, 60_000_000L, true, true);
            session.requestTransferBeforeUpdate(300_000L);
            assertTrue(session.tickSender(keepSettings(50_000_000, c[0]), false, 1_000L), "keep " + c[0]);
            assertEquals(c[1], port.lastRequestedAmount, "keep " + c[0]);
        }

        FakePort port = new FakePort();
        port.muleReply = READY_MULE;
        MuleSession nothingLoose = new MuleSession(port);
        port.view = capitalView(0, 60_000_000L, true, true);
        nothingLoose.requestTransferBeforeUpdate(300_000L);
        assertFalse(nothingLoose.tickSender(keepSettings(50_000_000, 0), false, 1_000L));
        assertTrue(nothingLoose.status().contains("no loose GP"), nothingLoose.status());
    }

    @Test
    void keepDuringUpdateIsReadFromItsSetting() {
        assertEquals(-1, FlipperScript.updateKeepGp(updateKeep("")), "Blank uses Keep for flipping");
        assertEquals(-1, FlipperScript.updateKeepGp(updateKeep("  ")));
        assertEquals(0, FlipperScript.updateKeepGp(updateKeep("0")), "0 sends all free GP");
        assertEquals(20_000_000, FlipperScript.updateKeepGp(updateKeep("20")));
        assertEquals(-1, FlipperScript.updateKeepGp(updateKeep("2O")), "A typo falls back to Keep for flipping");
        assertEquals(-1, FlipperScript.updateKeepGp(null));
    }

    private static FlipperConfig updateKeep(String text) {
        return new FlipperConfig() {
            @Override public String muleUpdateKeepGp() { return text; }
        };
    }

    @Test
    void muleAmountSettingsReadPlainNumbersAsMillions() {
        assertEquals(150_000_000, MulePolicy.parseAmountSetting("150"));
        assertEquals(1_000_000_000, MulePolicy.parseAmountSetting("1000"));
        assertEquals(1_500_000, MulePolicy.parseAmountSetting("1.5"));
        assertEquals(150_000_000, MulePolicy.parseAmountSetting(" 150 "));
        assertEquals(0, MulePolicy.parseAmountSetting("0"));
        // A unit or full digits are read as written.
        assertEquals(150_000_000, MulePolicy.parseAmountSetting("150M"));
        assertEquals(500_000, MulePolicy.parseAmountSetting("500k"));
        assertEquals(1_200_000_000, MulePolicy.parseAmountSetting("1.2b"));
        assertEquals(50_000_000, MulePolicy.parseAmountSetting("50,000,000"));
        assertEquals(150, MulePolicy.parseAmountSetting("150 gp"));
        // Too big for the game, or not a number: invalid, which pauses automatic muling.
        assertEquals(-1, MulePolicy.parseAmountSetting("3000"));
        assertEquals(-1, MulePolicy.parseAmountSetting("50000000"), "Not read as 50,000,000M");
        assertEquals(-1, MulePolicy.parseAmountSetting(""));
        assertEquals(-1, MulePolicy.parseAmountSetting("abc"));
        assertEquals(-1, MulePolicy.parseAmountSetting(null));
    }

    @Test
    void policyParsesTheTriggerAndAllowsOnePercentOfDrift() {
        assertEquals(50_000_000, MulePolicy.parseTrigger("", 50_000_000), "Blank keeps the old behaviour");
        assertEquals(50_000_000, MulePolicy.parseTrigger("0", 50_000_000));
        assertEquals(75_000_000, MulePolicy.parseTrigger("75M", 50_000_000));
        assertEquals(200_000_000, MulePolicy.parseTrigger("200", 50_000_000), "A plain number is millions");
        assertEquals(-1, MulePolicy.parseTrigger("75q", 50_000_000), "A typo stops automatic muling");
        assertEquals(500_000L, MulePolicy.transferTolerance(50_000_000));
        assertEquals(MulePolicy.MIN_TRANSFER_GP, MulePolicy.transferTolerance(10_000));
    }

    @Test
    void transferWaitsUntilWorkingCapitalReachesTheMuleWhenOverAmount() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        MuleSession.SenderSettings settings = triggerSettings(75_000_000, 50_000_000);
        port.view = capitalView(70_000_000, 70_000_000L, true, true);
        assertFalse(session.tickSender(settings, false, 0L));
        assertFalse(session.tickSender(settings, false, 60_000L));
        assertFalse(session.liquidationRequested());
        assertEquals(0, port.requestLeaseCalls, "20M above the keep amount is not enough below the trigger");

        port.view = capitalView(75_000_000, 75_000_000L, true, true);
        assertFalse(session.tickSender(settings, false, 61_000L));
        assertTrue(session.tickSender(settings, false, 61_000L + MuleSession.ELIGIBLE_DEBOUNCE_MS));
        assertEquals(25_000_000, port.lastRequestedAmount, "Sends everything above the keep amount");
    }

    @Test
    void aStartedSellDownCarriesOnToTheKeepAmountWhenCapitalDipsBelowTheTrigger() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        MuleSession.SenderSettings settings = triggerSettings(75_000_000, 50_000_000);
        port.view = capitalView(10_000_000, 75_000_000L, true, true);
        session.tickSender(settings, false, 0L);
        assertTrue(session.liquidationRequested());

        port.view = capitalView(10_000_000, 74_000_000L, true, true);
        session.tickSender(settings, false, 1_000L);
        assertTrue(session.liquidationRequested(), "Buys are already cancelled; stopping now would waste that");
        assertEquals(24_000_000, session.liquidationTargetGp());

        port.view = capitalView(24_000_000, 74_000_000L, true, true);
        assertTrue(session.tickSender(settings, false, 2_000L));
        assertEquals(24_000_000, port.lastRequestedAmount);
    }

    @Test
    void aSmallPriceMoveDuringTheTransferDoesNotCancelIt() {
        FakePort port = new FakePort();
        MuleSession session = senderWithInvestedCapitalAtOpenTrade(port);
        // 300K below the 55M estimate the 5M transfer was locked with: within 1% of the 50M keep amount.
        port.view = investedTradeView(301, 5_000_000, 54_700_000L, true, false, 0);
        session.tickSender(senderSettings(true, 50_000_000), false, 11_000L);
        assertEquals(5_000_000, port.lastOfferedCoins);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());
        assertEquals(0, port.closedInterfaces);
    }

    @Test
    void updateTransferIgnoresTheTriggerAndSendsOnlyLiquidExcessAtOnce() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        MuleSession.SenderSettings settings = triggerSettings(100_000_000, 50_000_000);
        port.view = capitalView(8_000_000, 60_000_000L, true, true);
        assertFalse(session.tickSender(settings, false, 0L));
        assertEquals(0, port.requestLeaseCalls);

        session.requestTransferBeforeUpdate(300_000L);
        assertTrue(session.transferBeforeUpdateBusy());
        assertTrue(session.tickSender(settings, false, 1_000L), "No debounce or trigger before an update");
        assertEquals(8_000_000, port.lastRequestedAmount, "Only liquid GP; no sell-down with an update coming");
        assertFalse(session.liquidationRequested());
        assertEquals(MuleSession.Phase.PREPARING_CASH, session.phase());
        assertTrue(session.transferBeforeUpdateBusy());
        assertTrue(session.status().contains("before the OSRS update"), session.status());
    }

    @Test
    void updateTransferThatCannotRunIsMarkedTriedSoFinishCanStop() {
        FakePort port = new FakePort();
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        MuleSession.SenderSettings settings = triggerSettings(75_000_000, 50_000_000);

        MuleSession tooClose = new MuleSession(port);
        port.view = capitalView(20_000_000, 70_000_000L, true, true);
        tooClose.requestTransferBeforeUpdate(1_000L);
        assertFalse(tooClose.tickSender(settings, false, 2_000L));
        assertFalse(tooClose.transferBeforeUpdateBusy());
        assertTrue(tooClose.status().contains("too close"), tooClose.status());

        MuleSession nothingToSend = new MuleSession(port);
        port.view = capitalView(20_000_000, 50_000_000L, true, true);
        nothingToSend.requestTransferBeforeUpdate(300_000L);
        assertFalse(nothingToSend.tickSender(settings, false, 2_000L));
        assertFalse(nothingToSend.transferBeforeUpdateBusy());

        MuleSession mulingOff = new MuleSession(port);
        port.view = capitalView(20_000_000, 70_000_000L, true, true);
        mulingOff.requestTransferBeforeUpdate(300_000L);
        assertFalse(mulingOff.tickSender(senderSettings(false, 50_000_000), false, 2_000L));
        assertFalse(mulingOff.transferBeforeUpdateBusy());
        assertEquals(0, port.requestLeaseCalls);
    }

    @Test
    void updateTransferStillBeforeItsTradeStopsAtTheDeadlineAndReturnsHome() {
        FakePort port = new FakePort();
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        MuleSession.SenderSettings settings = senderSettings(true);
        port.view = senderView(330, false);
        session.requestTransferBeforeUpdate(20_000L);
        assertTrue(session.tickSender(settings, false, 1_000L));
        session.tickSender(settings, false, 2_000L);
        assertEquals(MuleSession.Phase.HOPPING_TO_MULE, session.phase());
        session.tickSender(settings, false, 3_000L);
        assertEquals(301, port.lastHoppedWorld);
        port.view = senderView(301, false);

        assertTrue(session.tickSender(settings, false, 20_000L));
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertTrue(session.status().contains("too close"), session.status());
        assertEquals(Arrays.asList(false), port.releasedLeases);
        assertTrue(session.transferBeforeUpdateBusy(), "Busy until the return journey ends");
        session.tickSender(settings, false, 21_000L);
        assertEquals(330, port.lastHoppedWorld);
        port.view = senderView(330, false);
        assertFalse(session.tickSender(settings, false, 22_000L));
        assertFalse(session.transferBeforeUpdateBusy());
    }

    @Test
    void aMuleThatNoLongerHoldsTheLeaseCancelsInsteadOfRunningIntoTimeOuts() {
        FakePort port = new FakePort();
        MuleSession session = senderAwayFromHome(port);
        port.muleReply = MuleNetwork.noResponse("127.0.0.1", "Connection failed");
        session.tickSender(senderSettings(true), false, 5_000L + MuleSession.HEARTBEAT_INTERVAL_MS);
        assertNotEquals(MuleSession.Phase.RETURNING, session.phase(), "A network blip is not proof the lease is gone");
        assertTrue(port.releasedLeases.isEmpty());

        port.muleReply = new MuleNetwork.MuleReply(false, false, "BankMule", 301, "", "127.0.0.1",
            "Lease expired or not owned");
        session.tickSender(senderSettings(true), false, 5_000L + 2 * MuleSession.HEARTBEAT_INTERVAL_MS);
        assertEquals(MuleSession.Phase.RETURNING, session.phase());
        assertTrue(session.status().contains("no longer holds"), session.status());
        assertEquals(Arrays.asList(false), port.releasedLeases);
    }

    @Test
    void receiverAnywhereAtTheGeIsReadyOnItsFirstLeasedLogin() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        // Logged in for the lease at a GE bank booth, 14 tiles from the GE centre, with no remembered tile yet.
        port.view = new MuleSession.Snapshot(true, 202L, "BankMule", 301, 3150, 3475, 0, true,
            false, false, false, 5_000_000, 0, 5_000_000, false, false, false, "", 0, false, 0);
        session.tickReceiver(receiverSettings(), 10_000L);
        assertTrue(port.lastServerReady);
        assertEquals(3150, port.lastServerX);
        assertEquals(3475, port.lastServerY);
    }

    private static MuleSession.SenderSettings triggerSettings(int trigger, int keep) {
        return new MuleSession.SenderSettings(true, trigger, keep, "BankMule", "auto",
            43599, 0, MulePolicy.TileSpec.auto());
    }

    private static MuleSession.SenderSettings senderSettings(boolean enabled) {
        return senderSettings(enabled, 30_000_000);
    }

    private static MuleSession.SenderSettings senderSettings(boolean enabled, int floor) {
        return new MuleSession.SenderSettings(enabled, floor, floor, "BankMule", "auto",
            43599, 0, MulePolicy.TileSpec.auto());
    }

    private static MuleSession.Snapshot cashFloorView(int coins) {
        return snapshot(101L, "FlipperAcc", 330, true, true, false, false,
            coins, 0, false, false, "", 0, false, 0);
    }

    private static MuleSession.Snapshot capitalView(int coins, long capital, boolean verified, boolean complete) {
        return new MuleSession.Snapshot(true, 101L, "FlipperAcc", 330,
            MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
            true, true, false, false, coins, 0, capital, false,
            false, false, "", 0, false, 0, verified, complete);
    }

    private static MuleSession.Snapshot investedTradeView(int world, int coins, long capital,
                                                         boolean main, boolean confirm, int offered) {
        return new MuleSession.Snapshot(true, 101L, "FlipperAcc", world,
            MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
            true, false, false, false, coins, 0, capital, false,
            main, confirm, main || confirm ? "BankMule" : "", offered, false, 0, true, true);
    }

    private static MuleSession.Snapshot senderView(int world, boolean tradeOpen) {
        return snapshot(101L, "FlipperAcc", world, true, false, false, false,
            50_000_000, 0, tradeOpen, false, tradeOpen ? "BankMule" : "", 0, false, 0);
    }

    private static MuleSession senderAwayFromHome(FakePort port) {
        MuleSession session = new MuleSession(port);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = senderView(330, false);
        session.tickSender(senderSettings(true), false, 0L);
        session.tickSender(senderSettings(true), false, 5_000L);
        session.tickSender(senderSettings(true), false, 6_000L);
        session.tickSender(senderSettings(true), false, 7_000L);
        port.view = senderView(301, false);
        session.tickSender(senderSettings(true), false, 8_000L);
        assertEquals(MuleSession.Phase.WALKING_TO_MULE, session.phase());
        return session;
    }

    private static MuleSession senderAtOpenTrade(FakePort port) {
        MuleSession session = senderAwayFromHome(port);
        session.tickSender(senderSettings(true), false, 9_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            50_000_000, 0, true, false, "BankMule", 0, false, 0);
        session.tickSender(senderSettings(true), false, 10_000L);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());
        return session;
    }

    private static MuleSession senderWithFiftyMillionFloorAtOpenTrade(FakePort port) {
        MuleSession session = new MuleSession(port);
        MuleSession.SenderSettings settings = senderSettings(true, 50_000_000);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = snapshot(101L, "FlipperAcc", 330, true, false, false, false,
            55_000_000, 0, false, false, "", 0, false, 0);
        session.tickSender(settings, false, 0L);
        session.tickSender(settings, false, 5_000L);
        session.tickSender(settings, false, 6_000L);
        session.tickSender(settings, false, 7_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            55_000_000, 0, false, false, "", 0, false, 0);
        session.tickSender(settings, false, 8_000L);
        session.tickSender(settings, false, 9_000L);
        port.view = snapshot(101L, "FlipperAcc", 301, true, false, false, false,
            55_000_000, 0, true, false, "BankMule", 0, false, 0);
        session.tickSender(settings, false, 10_000L);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());
        return session;
    }

    private static MuleSession senderWithInvestedCapitalAtOpenTrade(FakePort port) {
        MuleSession session = new MuleSession(port);
        MuleSession.SenderSettings settings = senderSettings(true, 50_000_000);
        port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
        port.view = investedTradeView(330, 5_000_000, 55_000_000L, false, false, 0);
        session.tickSender(settings, false, 0L);
        session.tickSender(settings, false, 5_000L);
        session.tickSender(settings, false, 6_000L);
        session.tickSender(settings, false, 7_000L);
        port.view = investedTradeView(301, 5_000_000, 55_000_000L, false, false, 0);
        session.tickSender(settings, false, 8_000L);
        session.tickSender(settings, false, 9_000L);
        port.view = investedTradeView(301, 5_000_000, 55_000_000L, true, false, 0);
        session.tickSender(settings, false, 10_000L);
        assertEquals(MuleSession.Phase.OFFERING_COINS, session.phase());
        return session;
    }

    @Test
    void receiverObserverHearsEachConfirmedReceiptOnceAndOnlyIdleHoldings() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        List<String> events = new ArrayList<>();
        session.observeReceiver(recordingObserver(events));
        observeReceiverTrade(session, port);
        assertEquals(Arrays.asList("holdings:202:5000000:0:false"), events,
            "Coin counts while a trade window is open are mid-offer and must not be reported");

        port.activeLease = null;
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        session.tickReceiver(receiverSettings(), 14_600L);
        assertEquals(Arrays.asList("holdings:202:5000000:0:false", "holdings:202:25000000:0:false",
            "received:202:20000000", "holdings:202:25000000:0:false"), events);
        assertEquals(20_000_000L, session.totalTransferredGp());
    }

    @Test
    void receiverObserverSeesBankCoinsOnlyWhenTheBankWasReadable() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        List<String> events = new ArrayList<>();
        session.observeReceiver(recordingObserver(events));

        port.view = snapshot(202L, "BankMule", 301, true, false, false, false, 5_000_000, 0,
            false, false, "", 0, false, 0);
        session.tickReceiver(receiverSettings(), 10_000L);
        port.view = snapshot(202L, "BankMule", 301, true, false, false, false, 5_000_000, 700_000_000,
            false, false, "", 0, false, 0);
        session.tickReceiver(receiverSettings(), 10_600L);
        port.view = snapshot(202L, "BankMule", 301, true, false, false, true, 5_000_000, 0,
            false, false, "", 0, false, 0);
        session.tickReceiver(receiverSettings(), 11_200L);
        assertEquals(Arrays.asList("holdings:202:5000000:0:false", "holdings:202:5000000:700000000:true",
            "holdings:202:5000000:0:true"), events,
            "An open bank with no coins is a real zero; an unloaded bank is not known");
    }

    @Test
    void aFailingReceiverObserverCannotChangeATradeOutcome() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        session.observeReceiver(new MuleSession.ReceiverObserver() {
            @Override public void received(long account, long gp) { throw new IllegalStateException("display"); }
            @Override public void holdings(long account, long inventoryCoins, long bankCoins, boolean bankKnown) {
                throw new IllegalStateException("display");
            }
        });
        observeReceiverTrade(session, port);
        port.activeLease = null;
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 14_000L);
        assertEquals(20_000_000L, session.totalTransferredGp());
        session.tickReceiver(receiverSettings(), 14_600L);
        assertEquals(20_000_000L, session.totalTransferredGp(), "Still counted exactly once");
    }

    @Test
    void receiverObserverIsNotToldAboutUnconfirmedOrCancelledTrades() {
        FakePort port = receiverPort();
        MuleSession session = new MuleSession(port);
        List<String> events = new ArrayList<>();
        session.observeReceiver(recordingObserver(events));
        port.view = receiverView(true, false, 5_000_000, "Stranger", 20_000_000);
        session.tickReceiver(receiverSettings(), 10_000L);
        port.view = receiverView(false, false, 25_000_000, "", 0);
        session.tickReceiver(receiverSettings(), 11_000L);
        assertTrue(events.stream().noneMatch(event -> event.startsWith("received")), events.toString());
        assertEquals(0, session.totalTransferredGp());
    }

    private static MuleSession.ReceiverObserver recordingObserver(List<String> events) {
        return new MuleSession.ReceiverObserver() {
            @Override public void received(long account, long gp) { events.add("received:" + account + ":" + gp); }
            @Override public void holdings(long account, long inventoryCoins, long bankCoins, boolean bankKnown) {
                events.add("holdings:" + account + ":" + inventoryCoins + ":" + bankCoins + ":" + bankKnown);
            }
        };
    }

    private static MuleSession.ReceiverSettings receiverSettings() {
        return new MuleSession.ReceiverSettings(43599, 0, MulePolicy.TileSpec.auto(), "BankMule");
    }

    private static MuleSession.Snapshot receiverView(boolean main, boolean confirm, int coins,
                                                    String partner, int offered) {
        return snapshot(202L, "BankMule", 301, true, false, false, false,
            coins, 0, main, confirm, partner, 0, false, offered);
    }

    private static FakePort receiverPort() {
        FakePort port = new FakePort();
        port.activeLease = new MuleNetwork.Lease("FlipperAcc", 20_000_000, 100_000L);
        port.view = receiverView(false, false, 5_000_000, "", 0);
        return port;
    }

    private static void observeReceiverTrade(MuleSession session, FakePort port) {
        session.tickReceiver(receiverSettings(), 10_000L);
        port.view = receiverView(true, false, 5_000_000, "FlipperAcc", 20_000_000);
        session.tickReceiver(receiverSettings(), 11_000L);
        port.view = receiverView(false, true, 5_000_000, "FlipperAcc", 20_000_000);
        session.tickReceiver(receiverSettings(), 12_000L);
    }

    static MuleSession.Snapshot snapshot(long account, String playerName, int world, boolean atGe,
                                                 boolean exchangeOpen, boolean offerOpen, boolean bankOpen,
                                                 int invCoins, int bankCoins,
                                                 boolean tradeMain, boolean tradeConfirm, String partner,
                                                 int ourCoins, boolean ourOther, int otherCoins) {
        return new MuleSession.Snapshot(true, account, playerName, world,
            MulePolicy.GE_CENTER_X, MulePolicy.GE_CENTER_Y, MulePolicy.GE_CENTER_PLANE,
            atGe, exchangeOpen, offerOpen, bankOpen,
            invCoins, bankCoins, invCoins + bankCoins, false,
            tradeMain, tradeConfirm, partner, ourCoins, ourOther, otherCoins);
    }

    static final class FakePort implements MuleSession.Port {
        MuleSession.Snapshot view;
        boolean allowed = true;
        MuleNetwork.MuleReply muleReply = new MuleNetwork.MuleReply(false, false, "", 0, "", "", "Offline");
        MuleNetwork.Lease activeLease;
        int closedInterfaces;
        int lastHoppedWorld;
        String lastTradedPlayer = "";
        int lastOfferedCoins;
        int acceptedMain;
        int acceptedConfirm;
        int declined;
        int lastWalkX, lastWalkY, lastWalkPlane;
        boolean loggedOut;
        int lastLoginWorld;
        int tradePlayerCalls;
        boolean playerNearby = true;
        boolean acceptConfirmResult = true;
        boolean serverRunning;
        Runnable beforeLeaseClear = () -> { };
        String lastServerRsn = "";
        boolean lastServerReady;
        int lastServerX, lastServerY;
        final List<Boolean> releasedLeases = new ArrayList<>();
        final List<String> releaseTargets = new ArrayList<>();
        final List<MuleSession.Snapshot> sequentialReads = new ArrayList<>();
        int sequentialReadIndex;
        int readCalls;
        int requestLeaseCalls;
        int lastRequestedAmount;

        @Override public MuleSession.Snapshot read() {
            readCalls++;
            return sequentialReadIndex < sequentialReads.size() ? sequentialReads.get(sequentialReadIndex++) : view;
        }
        @Override public boolean allowed() { return allowed; }
        @Override public void prepareBoundary() { }
        @Override public void closeInterfaces() { closedInterfaces++; }
        @Override public void withdrawBankCoins(int needed) { }
        @Override public void walkTo(int x, int y, int plane) { lastWalkX = x; lastWalkY = y; lastWalkPlane = plane; }
        @Override public boolean hopToWorld(int world) { lastHoppedWorld = world; return true; }
        @Override public boolean playerNear(String rsn, int tiles) { return playerNearby; }
        @Override public boolean tradePlayer(String rsn) { tradePlayerCalls++; lastTradedPlayer = rsn; return true; }
        @Override public boolean offerCoins(int amount, int inventoryCoins) { lastOfferedCoins = amount; return true; }
        @Override public boolean acceptTradeMain() { acceptedMain++; return true; }
        @Override public boolean acceptTradeConfirm() { acceptedConfirm++; return acceptConfirmResult; }
        @Override public boolean declineTrade() { declined++; return true; }
        @Override public void logout() { loggedOut = true; }
        @Override public void login(int world) { lastLoginWorld = world; }
        @Override public int profileWorld() { return 301; }
        @Override public MuleNetwork.MuleReply requestMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp) { requestLeaseCalls++; lastRequestedAmount = amountGp; return muleReply; }
        @Override public MuleNetwork.MuleReply heartbeatMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp) { return muleReply; }
        @Override public void releaseMuleLease(String host, int port, String muleRsn, String flipperRsn, boolean completed) {
            releasedLeases.add(completed);
            releaseTargets.add(host + ":" + port + ":" + muleRsn + ":" + completed);
        }
        @Override public boolean ensureServer(int port) { serverRunning = true; return true; }
        @Override public void updateServerState(boolean ready, String muleRsn, int world, int x, int y, int plane) {
            lastServerRsn = muleRsn;
            lastServerReady = ready;
            lastServerX = x;
            lastServerY = y;
        }
        @Override public MuleNetwork.Lease activeServerLease() { return activeLease; }
        @Override public void clearServerLease(MuleNetwork.Lease expected) {
            beforeLeaseClear.run();
            if (expected != null && expected.sameReservation(activeLease)) activeLease = null;
        }
        @Override public void stopServer() { serverRunning = false; activeLease = null; }
        @Override public void reset() { }
    }
}
