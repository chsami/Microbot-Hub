package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldMembershipTravelTest {
    @Test void membersToFreeAndBackRechecksItemsAndSpellsOnTheSameClient() {
        Client client=mock(Client.class);
        ItemComposition membersItem=mock(ItemComposition.class),freeItem=mock(ItemComposition.class);
        when(membersItem.isMembers()).thenReturn(true);
        when(client.getItemDefinition(8007)).thenReturn(membersItem);
        when(client.getItemDefinition(2552)).thenReturn(membersItem);
        when(client.getItemDefinition(9013)).thenReturn(freeItem);
        for(boolean members:new boolean[]{true,false,true}) {
            when(client.getWorldType()).thenReturn(members?EnumSet.of(WorldType.MEMBERS):EnumSet.noneOf(WorldType.class));
            assertEquals(members,TeleportProviders.isItemAllowedOnWorld(client,8007),"Members tablet eligibility must follow current world");
            assertEquals(members,TeleportProviders.isItemAllowedOnWorld(client,2552),"Castle Wars ring transport must follow current world");
            assertTrue(TeleportProviders.isItemAllowedOnWorld(client,9013),"Free-world item remains eligible");
            assertEquals(members,TeleportProviders.isSpellAllowedOnWorld(client,Rs2Spells.CAMELOT_TELEPORT));
            assertTrue(TeleportProviders.isSpellAllowedOnWorld(client,Rs2Spells.VARROCK_TELEPORT));
        }
    }
}
