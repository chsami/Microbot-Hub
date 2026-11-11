package net.runelite.client.plugins.microbot.droefficientwalker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransportMenuAttemptsTest {
    @Test void unchangedOptionsNeverProduceSpamAndAllowOnlyOnePacedRetry(){
        var gate=new TransportMenuAttempts();assertTrue(gate.allow("destination",0));
        for(int i=1;i<2500;i++)assertFalse(gate.allow("destination",i));
        assertTrue(gate.allow("destination",2500));assertFalse(gate.allow("destination",10000));
    }
    @Test void changedDialogueCanContinueButRevisitingAnExhaustedStateCannotLoop(){
        var gate=new TransportMenuAttempts();assertTrue(gate.allow("travel",0));
        assertTrue(gate.allow("confirm",1));assertTrue(gate.allow("travel",2500));
        assertFalse(gate.allow("travel",10000));assertTrue(gate.allow("arrival",10001));
    }
}
