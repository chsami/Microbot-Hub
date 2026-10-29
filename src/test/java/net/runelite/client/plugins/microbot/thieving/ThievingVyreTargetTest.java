package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThievingVyreTargetTest {

    private final List<NPC> highlightedNpcs = new ArrayList<>();

    @AfterEach
    void clearHighlights() {
        highlightedNpcs.forEach(NpcIndicatorsPlugin.getHighlightedNpcs()::remove);
    }

    @Test
    void unknownVyreNameHasNoHouse() {
        assertNull(assertDoesNotThrow(() -> ThievingData.getVyreHouse("Knight of Ardougne")));
        assertNull(ThievingData.getVyreHouse(null));
        for (String vyre : ThievingData.VYRES) {
            assertNotNull(ThievingData.getVyreHouse(vyre), vyre);
        }
    }

    @Test
    void vyresModeDoesNotUseHighlightOnlyFilter() {
        assertFalse(ThievingScript.usesHighlightedNpcs(ThievingNpc.VYRES, true));
        assertFalse(ThievingScript.usesHighlightedNpcs(ThievingNpc.ARDOUGNE_KNIGHT, false));
        assertTrue(ThievingScript.usesHighlightedNpcs(ThievingNpc.ARDOUGNE_KNIGHT, true));
    }

    @Test
    void vyresFilterRejectsHighlightedKnight() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        Rs2NpcModel knight = npc("Knight of Ardougne");
        highlight(knight);
        Rs2NpcModel vyre = npc("Vallessia von Pitt");

        try (MockedStatic<Microbot> microbot = mockClientThread()) {
            Predicate<Rs2NpcModel> filter = filter(script);
            assertFalse(filter.test(knight));
            assertTrue(filter.test(vyre));
        }
    }

    @Test
    void vyresFilterPrefersHighlightedVyre() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        Rs2NpcModel knight = npc("Knight of Ardougne");
        Rs2NpcModel tagged = npc("Vonnetta Varnis");
        highlight(knight);
        highlight(tagged);
        Rs2NpcModel other = npc("Vallessia von Pitt");

        try (MockedStatic<Microbot> microbot = mockClientThread()) {
            Predicate<Rs2NpcModel> filter = filter(script);
            assertTrue(filter.test(tagged));
            assertFalse(filter.test(other));
            assertFalse(filter.test(knight));
        }
    }

    @Test
    void highlightFilterMatchesOnlyHighlightedNpcs() throws Exception {
        ThievingScript script = script(ThievingNpc.ARDOUGNE_KNIGHT);
        Rs2NpcModel highlighted = npc("Knight of Ardougne");
        highlight(highlighted);
        Rs2NpcModel other = npc("Guard");

        try (MockedStatic<Microbot> microbot = mockClientThread()) {
            Predicate<Rs2NpcModel> filter = filter(script);
            assertTrue(filter.test(highlighted));
            assertFalse(filter.test(other));
        }
    }

    @Test
    void unknownHouseClearsTheTarget() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        setField(script, "thievingNpc", npc("Knight of Ardougne"));
        setField(script, "startingNpc", "Knight of Ardougne");

        Method method = ThievingScript.class.getDeclaredMethod("getVyreHouseOrReset", String.class);
        method.setAccessible(true);
        assertNull(method.invoke(script, "Knight of Ardougne"));
        assertNull(script.getThievingNpc());
        assertNull(getField(script, "startingNpc"));
    }

    @Test
    void darkmeyerBankWithoutVyreSetStopsTheScript() throws Exception {
        ThievingScript script = spy(script(ThievingNpc.VYRES));
        doNothing().when(script).shutdown();
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            bank.when(Rs2Bank::isOpen).thenReturn(false);
            bank.when(Rs2Bank::getNearestBank).thenReturn(BankLocation.DARKMEYER);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3222, 3218, 0));

            Method method = ThievingScript.class.getDeclaredMethod("bankAndEquip");
            method.setAccessible(true);
            method.invoke(script);

            verify(script).shutdown();
            microbot.verify(() -> Microbot.showMessage(contains("Vyre noble set")));
            bank.verify(Rs2Bank::openBank, never());
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any()), never());
        }
    }

    private void highlight(Rs2NpcModel model) {
        highlightedNpcs.add(model.getNpc());
        NpcIndicatorsPlugin.getHighlightedNpcs().put(model.getNpc(), null);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Rs2NpcModel> filter(ThievingScript script) throws Exception {
        Method method = ThievingScript.class.getDeclaredMethod("getThievingNpcFilter");
        method.setAccessible(true);
        return (Predicate<Rs2NpcModel>) method.invoke(script);
    }

    @SuppressWarnings("unchecked")
    private static MockedStatic<Microbot> mockClientThread() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<Object>) inv.getArgument(0)).call()));
        MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        return microbot;
    }

    private static Rs2NpcModel npc(String name) {
        Rs2NpcModel model = mock(Rs2NpcModel.class);
        when(model.getName()).thenReturn(name);
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn(name);
        when(model.getNpc()).thenReturn(npc);
        return model;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ThievingScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = ThievingScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static ThievingScript script(ThievingNpc npc) {
        ThievingConfig config = mock(ThievingConfig.class);
        when(config.THIEVING_NPC()).thenReturn(npc);
        return new ThievingScript(config, mock(ThievingPlugin.class));
    }
}
