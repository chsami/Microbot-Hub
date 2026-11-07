package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopilotFundsTest {
    private static final String MINIMUM = "<html><center>Add at least <FONT COLOR=yellow>1,000</FONT>"
        + " gp<br>to your inventory<br>to get a flip suggestion<br></center></html>";

    @Test
    void actualFundsDisplayMatchesWithoutRequiringAnOpenSidebar() throws Exception {
        Fixture fixture = new Fixture();
        assertFalse(fixture.owner.mainPanel.isShowing());
        assertTrue(fixture.current());
        edt(() -> fixture.panel.suggestionText.setText("<html>Not enough funds</html>"));
        assertTrue(fixture.current());
        edt(() -> fixture.panel.suggestionText.setText("Insufficient coins"));
        assertTrue(fixture.current());
    }

    @Test
    void plainWaitAndUnrelatedLabelsNeverQualify() throws Exception {
        Fixture fixture = new Fixture();
        edt(() -> {
            fixture.panel.suggestionText.setText("<html><center>Wait<br></center></html>");
            fixture.owner.mainPanel.add(new JLabel("Not enough funds"));
        });
        assertFalse(fixture.current());
        edt(() -> fixture.panel.suggestionText.setText("Waiting for dumps..."));
        assertFalse(fixture.current());
        assertFalse(new CopilotFunds().current(new Plugin() {}, fixture.manager, fixture.suggestion));
    }

    @Test
    void localeSeparatorsAndOnlyTheKnownMinimumAreAccepted() {
        assertTrue(CopilotFunds.fundsText(MINIMUM));
        assertTrue(CopilotFunds.fundsText(MINIMUM.replace("1,000", "1.000")));
        assertTrue(CopilotFunds.fundsText(MINIMUM.replace("1,000", "1&#160;000")));
        assertTrue(CopilotFunds.fundsText(MINIMUM.replace("1,000", "1\u202f000")));
        assertTrue(CopilotFunds.fundsText(MINIMUM.replace("1,000", "١٬٠٠٠").replace('٬', ',')));
        assertFalse(CopilotFunds.fundsText(MINIMUM.replace("1,000", "5,000")));
        assertFalse(CopilotFunds.fundsText("Add at least 1,000 gp to buy this item"));
        assertFalse(CopilotFunds.fundsText(null));
        assertFalse(CopilotFunds.fundsText("Not enough funds" + " ".repeat(2048)));
        assertFalse(CopilotFunds.fundsText("The funds are sufficient"));
    }

    @Test
    void differentDisplayedCurrencyShortageAndFundingRequestsAreRecognized() {
        String[] warnings = {
            "Not enough funds to get another suggestion",
            "You do not have enough GP to trade",
            "You don't have enough money",
            "You don’t have enough coins",
            "<HTML>You DON&apos;T have enough <b>CASH</b></HTML>",
            "You don&#39;t have enough gp",
            "You don&rsquo;t have enough funds",
            "Need more capital before placing a buy offer",
            "This offer requires additional funds",
            "Insufficient available balance",
            "Not enough available gold pieces",
            "Running low on cash",
            "Short of money",
            "No available funds",
            "Please add more gp to your inventory",
            "Deposit additional coins",
            "Withdraw more money before continuing",
            "More gold needed",
            "Additional funds are required",
            "Your balance is too low",
            "Available cash is insufficient",
            "Low funds",
            "Low balance",
            "You have no money left",
            "You do not have sufficient cash",
            "You've run out of GP",
            "Too little capital",
            "Your funds are depleted"
        };
        for (String warning : warnings) assertTrue(CopilotFunds.fundsText(warning), warning);
    }

    @Test
    void ordinaryWaitingResourcesAndUnrelatedCurrencyMentionsAreNotShortages() {
        String[] ordinary = {
            "Wait for a new flip suggestion",
            "Waiting for dumps...",
            "Need more time to find an item",
            "Not enough items to sell",
            "Insufficient available slots",
            "Running low on inventory space",
            "Not enough profit; your money is available",
            "No available offers; gp balance is 20M",
            "Add more items or withdraw resources",
            "You have enough funds",
            "Cash balance: 1000000 gp",
            "Coin prices are changing",
            "Additional profit is required",
            "You do not need more gp",
            "You don't require additional money",
            "No additional funds are required",
            "Not insufficient capital"
        };
        for (String message : ordinary) assertFalse(CopilotFunds.fundsText(message), message);
    }

    @Test
    void loadingPreferencesAndHiddenAncestorCardsFailClosed() throws Exception {
        Fixture fixture = new Fixture();
        edt(() -> fixture.panel.suggestionTextContainer.setVisible(false));
        assertFalse(fixture.current());
        edt(() -> {
            fixture.panel.suggestionTextContainer.setVisible(true);
            fixture.panel.isPreferencesPanelVisible = true;
        });
        assertFalse(fixture.current());
        edt(() -> {
            fixture.panel.isPreferencesPanelVisible = false;
            fixture.owner.mainPanel.copilotPanel.setVisible(false);
        });
        assertFalse(fixture.current());
        edt(() -> {
            fixture.owner.mainPanel.copilotPanel.setVisible(true);
            fixture.panel.suggestionText.setText("");
        });
        assertFalse(fixture.current());
    }

    @Test
    void managerOwnershipAndTheActualComponentChainAreRequired() throws Exception {
        Fixture fixture = new Fixture();
        assertFalse(fixture.funds.current(fixture.owner, new Object(), fixture.suggestion));
        assertFalse(fixture.funds.current(fixture.owner, fixture.manager, null));
        edt(() -> fixture.panel.suggestionTextContainer.remove(fixture.panel.suggestionText));
        assertFalse(fixture.current());
        edt(() -> fixture.panel.suggestionTextContainer.add(fixture.panel.suggestionText));
        assertTrue(fixture.current());
        edt(() -> fixture.owner.mainPanel.remove(fixture.owner.mainPanel.copilotPanel));
        assertFalse(fixture.current());
    }

    @Test
    void everyReadIsFreshAndOwnerReplacementDoesNotReuseAnOldResult() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        edt(() -> second.panel.suggestionText.setText("Wait"));
        assertTrue(first.current());
        assertFalse(first.funds.current(second.owner, second.manager, second.suggestion));
        edt(() -> first.panel.suggestionText.setText("Wait"));
        assertFalse(first.current());
        edt(() -> first.panel.suggestionText.setText(MINIMUM));
        assertTrue(first.current());
    }

    @Test
    void resetInvalidatesAQueuedReadAndTimeoutDoesNotLeaveACachedResult() throws Exception {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        CountDownLatch submitted = new CountDownLatch(1);
        CopilotFunds funds = new CopilotFunds(task -> {
            queued.set(task);
            submitted.countDown();
        });
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(
            () -> funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        assertTrue(submitted.await(2, TimeUnit.SECONDS));
        funds.reset();
        edt(queued.get());
        assertFalse(result.get(2, TimeUnit.SECONDS));
        assertFalse(funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        // A timed-out FutureTask is cancelled; a late EDT drain cannot authorize anything.
        edt(queued.get());
        assertTrue(fixture.current());
    }

    @Test
    void readsRunOnEdtAndBrokenQueueOrWrongThreadFailClosed() throws Exception {
        Fixture fixture = new Fixture();
        AtomicReference<Boolean> direct = new AtomicReference<>();
        edt(() -> direct.set(fixture.current()));
        assertEquals(Boolean.TRUE, direct.get());
        assertFalse(new CopilotFunds(Runnable::run).current(fixture.owner, fixture.manager, fixture.suggestion));
        assertFalse(new CopilotFunds(task -> { throw new IllegalStateException("unavailable"); })
            .current(fixture.owner, fixture.manager, fixture.suggestion));
    }

    @Test
    void blockedEdtKeepsOnlyOneQueuedReadEvenAcrossReset() throws Exception {
        Fixture fixture = new Fixture();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        CopilotFunds funds = new CopilotFunds(task -> {
            queued.set(task);
            count.incrementAndGet();
        });
        assertFalse(funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        assertEquals(1, count.get());
        assertFalse(funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        funds.reset();
        assertFalse(funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        assertEquals(1, count.get());
        edt(queued.get());
        assertFalse(funds.current(fixture.owner, fixture.manager, fixture.suggestion));
        assertEquals(2, count.get());
        edt(queued.get());
    }

    @Test
    void aReadFollowingAnotherAtOnceIsNeverRefusedAsBusy() throws Exception {
        Fixture fixture = new Fixture();
        // The Swing thread clears its slot just after it hands back the result. The next read must not be
        // turned away because that last step has not happened yet (a click validates right after a read).
        for (int read = 0; read < 500; read++) {
            assertTrue(fixture.current(), "back-to-back read " + read);
        }
    }

    private static void edt(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }

    private static final class Fixture {
        final Object manager = new Object();
        final Object suggestion = new Object();
        final CopilotFunds funds = new CopilotFunds();
        Owner owner;
        SuggestionPanel panel;

        Fixture() throws Exception {
            edt(() -> {
                panel = new SuggestionPanel(manager);
                owner = new Owner(panel);
            });
        }

        boolean current() {
            return funds.current(owner, manager, suggestion);
        }
    }

    private static final class Owner extends Plugin {
        final MainPanel mainPanel;
        Owner(SuggestionPanel panel) { mainPanel = new MainPanel(panel); }
    }

    private static final class MainPanel extends JPanel {
        final CopilotPanel copilotPanel;
        MainPanel(SuggestionPanel panel) {
            copilotPanel = new CopilotPanel(panel);
            add(copilotPanel);
        }
    }

    private static final class CopilotPanel extends JPanel {
        final SuggestionPanel suggestionPanel;
        CopilotPanel(SuggestionPanel panel) {
            suggestionPanel = panel;
            add(panel);
        }
    }

    private static final class SuggestionPanel extends JPanel {
        final Object suggestionManager;
        final JLabel suggestionText = new JLabel(MINIMUM) {
            @Override public String getText() {
                assertTrue(SwingUtilities.isEventDispatchThread());
                return super.getText();
            }
        };
        final JPanel suggestionTextContainer = new JPanel();
        boolean isPreferencesPanelVisible;

        SuggestionPanel(Object manager) {
            suggestionManager = manager;
            suggestionTextContainer.add(suggestionText);
            add(suggestionTextContainer);
        }
    }
}
