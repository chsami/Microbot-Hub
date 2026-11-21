package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CharterQuestBorrowedImprovementsTest {
    private Widget destination(String name) {
        Widget child = mock(Widget.class);
        when(child.getActions()).thenReturn(new String[]{"Examine", name});
        return child;
    }
    private Widget menu(Widget... children) {
        Widget parent = mock(Widget.class);
        when(parent.getChildren()).thenReturn(children);
        return parent;
    }
    @Test public void mapMenuUsesTheExactDestinationAndItsActionIndex() {
        Client client = mock(Client.class);
        Widget shore = destination("The Summer Shore");
        when(client.getWidget(72, 0)).thenReturn(mock(Widget.class));
        doReturn(menu(destination("The Pandemonium"), shore)).when(client).getWidget(72, 3);
        assertTrue(CharterMenu.open(client));
        assertSame(shore, CharterMenu.destination(client, "The Summer Shore"));
        assertEquals(2, CharterMenu.actionIndex(shore, "The Summer Shore"));
    }
    @Test public void listMenuRemainsSupported() {
        Client client = mock(Client.class);
        Widget port = destination("Catherby");
        when(client.getWidget(885, 0)).thenReturn(mock(Widget.class));
        doReturn(menu(port)).when(client).getWidget(885, 4);
        assertTrue(CharterMenu.open(client));
        assertSame(port, CharterMenu.destination(client, "Catherby"));
    }
    @Test public void mapHiddenAndSubstringDestinationsCannotBeSelected() {
        Client client = mock(Client.class);
        Widget hidden = destination("Catherby");
        when(hidden.isHidden()).thenReturn(true);
        doReturn(menu(hidden, destination("Catherby extra"))).when(client).getWidget(72, 3);
        assertNull(CharterMenu.destination(client, "Catherby"));
    }
    @Test public void ambiguousMapDestinationsAreRejected() {
        Client client = mock(Client.class);
        doReturn(menu(destination("Catherby"), destination("Catherby"))).when(client).getWidget(72, 3);
        assertNull(CharterMenu.destination(client, "Catherby"));
    }
    @Test public void emptyQuestHelperHighlightListFindsTheLiveTransformedNpc() throws Exception {
        Client client = mock(Client.class);
        WorldView world = mock(WorldView.class);
        Player player = mock(Player.class);
        when(client.getTopLevelWorldView()).thenReturn(world);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldView()).thenReturn(world);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3000, 3000, 0));
        NPC npc = mock(NPC.class);
        NPCComposition composition = mock(NPCComposition.class), expected = mock(NPCComposition.class);
        when(expected.getId()).thenReturn(100);
        when(expected.getName()).thenReturn("Quest guide");
        when(client.getNpcDefinition(100)).thenReturn(expected);
        when(npc.getId()).thenReturn(200);
        when(npc.getName()).thenReturn("Quest guide");
        when(npc.getComposition()).thenReturn(composition);
        when(composition.getConfigs()).thenReturn(new int[]{100, 200});
        when(npc.getWorldView()).thenReturn(world);
        when(npc.getWorldLocation()).thenReturn(new WorldPoint(3002, 3000, 0));
        @SuppressWarnings("unchecked") IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
        when(npcs.iterator()).thenAnswer(ignored -> List.of(npc).iterator());
        doReturn(npcs).when(world).npcs();
        NpcStep step = mock(NpcStep.class);
        Field id = WalkerTestReflection.declaredField(NpcStep.class, "npcID"); id.setAccessible(true); id.setInt(step, 100);
        Field highlighted = WalkerTestReflection.declaredField(NpcStep.class, "npcs"); highlighted.setAccessible(true);
        ArrayList<NPC> original = new ArrayList<>(); highlighted.set(step, original);
        when(step.getActiveStep()).thenReturn(step);
        QuestHelper quest = mock(QuestHelper.class);
        when(quest.getCurrentStep()).thenReturn(step);
        QuestHelperPlugin helper = mock(QuestHelperPlugin.class);
        when(helper.getSelectedQuest()).thenReturn(quest);
        PluginManager manager = mock(PluginManager.class);
        when(manager.getPlugins()).thenReturn(List.of(helper));
        when(manager.isActive(helper)).thenReturn(true);
        QuestHelperBridge.QuestTarget target = QuestHelperBridge.resolveQuestTarget(manager, client);
        assertSame(npc, target.npc);
        assertEquals(npc.getWorldLocation(), target.destination);
        assertTrue(original.isEmpty(), "Do not mutate Quest Helper's own highlight list");
        when(npc.isDead()).thenReturn(true);
        assertFalse(QuestHelperBridge.matchesNpcFamily(npc, expected));
    }
}
