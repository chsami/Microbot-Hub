package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IcePlateauWarningTest {
    @Test void acceptsWarningOnceAndStillWaitsForArrival() { exercise("Ice Plateau Teleport",true,false); }
    @Test void otherSpellCannotAcceptThisWarning() { exercise("Moonclan Teleport",true,false); }
    @Test void missingWarningDoesNotClickYes() { exercise("Ice Plateau Teleport",false,false); }
    @Test void alreadyArrivedDoesNotClickYes() { exercise("Ice Plateau Teleport",true,true); }
    private void exercise(String spell, boolean warning, boolean alreadyArrived) {
        Transport transport=mock(Transport.class);
        when(transport.getType()).thenReturn(TransportType.TELEPORTATION_SPELL);
        when(transport.getDisplayInfo()).thenReturn(spell);
        ClientThread thread=mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(i -> Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        AtomicBoolean clicked=new AtomicBoolean();
        try(var micro=mockStatic(Microbot.class);var widgets=mockStatic(Rs2Widget.class);var dialogue=mockStatic(Rs2Dialogue.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            widgets.when(()->Rs2Widget.hasWidget("Go DEEP into the wilderness?")).thenReturn(warning);
            dialogue.when(()->Rs2Dialogue.clickOption("Yes",true)).thenAnswer(i->{clicked.set(true);return true;});
            boolean shouldClick=spell.equals("Ice Plateau Teleport") && warning && !alreadyArrived;
            int[] polls={0};
            boolean arrived=NetworkTransportSupport.awaitSharedLanding(transport,()-> alreadyArrived || (shouldClick && clicked.get() && ++polls[0]>=3),shouldClick?1000:0);
            assertEquals(shouldClick || alreadyArrived,arrived);
            dialogue.verify(()->Rs2Dialogue.clickOption("Yes",true),times(shouldClick?1:0));
        }
    }
}
