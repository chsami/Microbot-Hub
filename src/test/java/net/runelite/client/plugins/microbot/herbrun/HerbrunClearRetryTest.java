package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HerbrunClearRetryTest {

    private MockedStatic<Microbot> microbot;
    private HerbrunScript script;

    @BeforeEach
    void setUp() throws Exception {
        microbot = mockStatic(Microbot.class);
        script = new HerbrunScript(mock(HerbrunPlugin.class), mock(HerbrunConfig.class));
        HerbPatch patch = mock(HerbPatch.class);
        when(patch.getDisplayName()).thenReturn("Ardougne");
        Field field = HerbrunScript.class.getDeclaredField("currentPatch");
        field.setAccessible(true);
        field.set(script, patch);
    }

    @AfterEach
    void tearDown() {
        microbot.close();
    }

    @Test
    void patchStillWeedyAfterRakeKeepsTheHerbPhase() {
        assertFalse(script.giveUpClearingHerbPatch());
    }

    @Test
    void patchThatNeverClearsIsSkippedAfterFiveTries() {
        for (int i = 0; i < 4; i++) {
            assertFalse(script.giveUpClearingHerbPatch());
        }

        assertTrue(script.giveUpClearingHerbPatch());
        microbot.verify(() -> Microbot.log(argThat((String message) -> message.contains("Ardougne herb patch after 5 tries")
                && message.contains("continuing the run"))));
    }

    @Test
    void retryBudgetStartsOverAfterGivingUp() {
        for (int i = 0; i < 5; i++) {
            script.giveUpClearingHerbPatch();
        }

        assertFalse(script.giveUpClearingHerbPatch());
    }
}
