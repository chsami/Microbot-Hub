package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookSnapshot;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookcaseSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArceuusLibraryEmptyBookcaseTest
{
    private static final BookSnapshot KING = new BookSnapshot("KILLING_OF_A_KING", 1, "Killing of a King", "Killing of a King");
    private static final BookSnapshot ENVOY = new BookSnapshot("VARLAMORE_ENVOY", 2, "Varlamore Envoy", "The Envoy to Varlamore");
    private static final WorldPoint DOUBLE_CASE = new WorldPoint(1618, 3794, 2);
    private static final WorldPoint OTHER_CASE = new WorldPoint(1625, 3793, 2);

    private static BookcaseSnapshot bookcase(WorldPoint loc, BookSnapshot... possible)
    {
        return new BookcaseSnapshot(loc, null, new HashSet<>(Arrays.asList(possible)), false);
    }

    @Test
    void emptyBookcaseIsDroppedFromFetchCandidates()
    {
        List<BookcaseSnapshot> raw = Arrays.asList(bookcase(DOUBLE_CASE, KING, ENVOY), bookcase(OTHER_CASE, KING));

        List<BookcaseSnapshot> before = ArceuusLibraryScript.filterCandidates(raw, KING, false, Collections.emptySet());
        assertEquals(2, before.size());

        Set<WorldPoint> empty = Collections.singleton(DOUBLE_CASE);
        List<BookcaseSnapshot> after = ArceuusLibraryScript.filterCandidates(raw, KING, false, empty);
        assertEquals(1, after.size());
        assertEquals(OTHER_CASE, after.get(0).getLocation());
    }

    @Test
    void everyCandidateEmptyLeavesNoCandidates()
    {
        List<BookcaseSnapshot> raw = Collections.singletonList(bookcase(DOUBLE_CASE, KING, ENVOY));
        Set<WorldPoint> empty = Collections.singleton(DOUBLE_CASE);
        assertTrue(ArceuusLibraryScript.filterCandidates(raw, KING, false, empty).isEmpty());
    }

    @Test
    void sweepSkipsBookcaseThatCameBackEmptyUntilBooksShift()
    {
        ArceuusLibraryScript script = new ArceuusLibraryScript(null);
        BookcaseSnapshot bc = bookcase(DOUBLE_CASE, KING, ENVOY);
        assertTrue(script.isUsefulSweepCandidate(bc));

        assertFalse(script.recordIfEmpty(DOUBLE_CASE));
        assertTrue(script.isUsefulSweepCandidate(bc));

        script.onNothingFound();
        assertTrue(script.recordIfEmpty(DOUBLE_CASE));
        assertFalse(script.isUsefulSweepCandidate(bc));

        script.onBooksShifted();
        assertTrue(script.isUsefulSweepCandidate(bc));
    }

    private static ArceuusLibraryScript receive(ChatMessageType type, String message)
    {
        ArceuusLibraryPlugin plugin = new ArceuusLibraryPlugin();
        ArceuusLibraryScript script = mock(ArceuusLibraryScript.class);
        plugin.script = script;
        ChatMessage event = new ChatMessage();
        event.setType(type);
        event.setMessage(message);
        plugin.onChatMessage(event);
        return script;
    }

    @Test
    void nothingFoundGameMessageReachesScript()
    {
        verify(receive(ChatMessageType.GAMEMESSAGE, "You don't find anything useful here.")).onNothingFound();
    }

    @Test
    void colouredShiftMessageClearsEmptyBookcases()
    {
        verify(receive(ChatMessageType.GAMEMESSAGE,
                "<col=7f007f>You hear the shifting of books due to a mysterious force...or are you just hearing things?"))
                .onBooksShifted();
    }

    @Test
    void publicChatIsIgnored()
    {
        verify(receive(ChatMessageType.PUBLICCHAT, "You don't find anything useful here."), never()).onNothingFound();
    }
}
