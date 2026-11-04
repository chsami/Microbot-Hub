package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.Client;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class NmzScriptBankPinTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2Widget> widget;
    private NmzPlugin plugin;
    private NmzScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        bank = mockStatic(Rs2Bank.class);
        widget = mockStatic(Rs2Widget.class);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        widget.when(() -> Rs2Widget.isWidgetVisible(anyInt())).thenReturn(true);
        plugin = mock(NmzPlugin.class);
        script = new NmzScript(plugin, mock(NmzConfig.class));
    }

    @AfterEach
    void tearDown() {
        widget.close();
        bank.close();
        microbot.close();
    }

    @Test
    void noPinScreenContinuesWithoutEnteringPin() {
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(false);

        assertTrue(script.handleRewardChestBankPin());

        bank.verify(Rs2Bank::handleBankPin, never());
        microbot.verify(() -> Microbot.stopPlugin(plugin), never());
    }

    @Test
    void profileWithoutSavedPinStopsPluginInsteadOfThrowing() {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getBankPin()).thenReturn(null);
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(true);
        bank.when(Rs2Bank::handleBankPin).thenCallRealMethod();

        try (MockedStatic<LoginManager> login = mockStatic(LoginManager.class)) {
            login.when(LoginManager::getActiveProfile).thenReturn(profile);

            assertFalse(script.handleRewardChestBankPin());
        }

        microbot.verify(() -> Microbot.stopPlugin(plugin));
    }

    @Test
    void failedEntryRetriesOnceThenStopsWithMessage() {
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(true);
        bank.when(Rs2Bank::handleBankPin).thenReturn(false);

        assertFalse(script.handleRewardChestBankPin());

        bank.verify(Rs2Bank::handleBankPin, times(2));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        microbot.verify(() -> Microbot.showMessage(message.capture()));
        assertTrue(message.getValue().contains("Bank PIN field in your Microbot profile"));
        assertTrue(message.getValue().contains("enter the PIN yourself"));
        microbot.verify(() -> Microbot.stopPlugin(plugin));
    }

    @Test
    void secondAttemptSucceedingContinuesToRewardShop() {
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(true);
        bank.when(Rs2Bank::handleBankPin).thenReturn(false, true);

        assertTrue(script.handleRewardChestBankPin());

        bank.verify(Rs2Bank::handleBankPin, times(2));
        microbot.verify(() -> Microbot.stopPlugin(plugin), never());
    }

    @Test
    void enteredPinContinuesToRewardShop() {
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(true, true, false);
        bank.when(Rs2Bank::handleBankPin).thenReturn(true);

        assertTrue(script.handleRewardChestBankPin());

        bank.verify(Rs2Bank::handleBankPin, times(1));
        microbot.verify(() -> Microbot.stopPlugin(plugin), never());
    }

    @Test
    void pinScreenClosedAfterFailedEntryContinues() {
        bank.when(Rs2Bank::isBankPinWidgetVisible).thenReturn(true, false);
        bank.when(Rs2Bank::handleBankPin).thenReturn(false);

        assertTrue(script.handleRewardChestBankPin());

        microbot.verify(() -> Microbot.stopPlugin(plugin), never());
    }
}
