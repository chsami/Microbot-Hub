package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PiratesHideoutDoorActionTest {
    private static final WorldPoint DOOR = new WorldPoint(3038,3956,0);

    @Test public void actualDispatchUsesCarriedLockpickWithoutAnyBankAction() throws Exception {
        checkDispatch(11727,true,70,"Pick-lock",null);
    }

    @Test public void missingLockpickStopsInsteadOfOpeningOrWithdrawingAnother() throws Exception {
        checkDispatch(11727,false,70,null,"lockpick");
    }

    @Test public void insufficientThievingStopsInsteadOfRetryingDoor() throws Exception {
        checkDispatch(11727,true,38,null,"39 Thieving");
    }

    @Test public void ordinaryDoorsKeepTheirOpenAction() throws Exception {
        checkDispatch(12345,false,1,"Open",null);
    }

    @Test public void pickLockPreservesTheExactDoorAndWorldViewWithoutChangingOriginalMenu() {
        NewMenuEntry original = entry(11727);
        original.setWorldViewId(7);
        NewMenuEntry selected = PiratesHideoutDoorAction.pickLock(original);
        assertEquals("Open",original.getOption());
        assertEquals("Pick-lock",selected.getOption());
        assertEquals(MenuAction.GAME_OBJECT_SECOND_OPTION,selected.getType());
        assertEquals(original.getParam0(),selected.getParam0());
        assertEquals(original.getParam1(),selected.getParam1());
        assertEquals(original.getIdentifier(),selected.getIdentifier());
        assertEquals(original.getTarget(),selected.getTarget());
        assertSame(original.getGameObject(),selected.getGameObject());
        assertEquals(7,selected.getWorldViewId());
    }

    @Test public void lockPickingRemainsSpacedAndBoundedAcrossRepeatedRequests() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint outside = new WorldPoint(3037,3956,0);
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,attempts.request(11727,DOOR,outside,337,false,0));
        assertEquals(WalkerDoorAttempts.Decision.WAIT,attempts.request(11727,DOOR,outside,337,false,600));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,attempts.request(11727,DOOR,outside,337,false,2500));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,attempts.request(11727,DOOR,outside,337,false,5000));
        assertEquals(WalkerDoorAttempts.Decision.BLOCK,attempts.request(11727,DOOR,outside,337,false,7500));
    }

    @Test public void alKharidTollGateBlocksWithoutPrinceAliRescue() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint gate = new WorldPoint(3268, 3228, 0);
        WorldPoint outside = new WorldPoint(3267, 3228, 0);
        try (var player = mockStatic(Rs2Player.class)) {
            player.when(() -> Rs2Player.getQuestState(Quest.PRINCE_ALI_RESCUE)).thenReturn(QuestState.NOT_STARTED);
            // 44052/44053 are the live pre-quest gate objects; the tiles decide, not the id.
            for (int id : new int[]{44052, 44053, 44050, 2786}) {
                assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(id, gate, outside, 337, false, 0));
                assertTrue(attempts.takeFailure().contains("Prince Ali Rescue"));
            }

            player.when(() -> Rs2Player.getQuestState(Quest.PRINCE_ALI_RESCUE)).thenReturn(QuestState.FINISHED);
            assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(44050, gate, outside, 337, false, 0));
        }
    }

    private static NewMenuEntry entry(int id) {
        TileObject door = mock(TileObject.class);
        when(door.getWorldLocation()).thenReturn(DOOR);
        return new NewMenuEntry(30,40,MenuAction.GAME_OBJECT_FIRST_OPTION.getId(),id,-1,
                "Open","<col=ffff>Door",door,WorldView.TOPLEVEL);
    }

    private static void checkDispatch(int id,boolean lockpick,int thieving,String action,String failure) throws Exception {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        ClientThread thread = mock(ClientThread.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3037,3956,0));
        when(client.getTopLevelWorldView()).thenReturn(mock(WorldView.class));
        when(client.getWorld()).thenReturn(337);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        Rectangle bounds = new Rectangle(10,20,30,40);
        try (var dro = mockStatic(Microbot.class);
             var inventory = mockStatic(Rs2Inventory.class);
             var bank = mockStatic(Rs2Bank.class);
             var skills = mockStatic(Rs2Player.class)) {
            dro.when(Microbot::getClientThread).thenReturn(thread);
            inventory.when(() -> Rs2Inventory.hasItem(1523)).thenReturn(lockpick);
            skills.when(() -> Rs2Player.getBoostedSkillLevel(Skill.THIEVING)).thenReturn(thieving);
            WalkerMouseControl.resetSession();
            WalkerMouseControl.interact(client,entry(id),bounds,true);
            if (action == null) {
                dro.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),never());
                assertTrue(WalkerMouseControl.takeDoorFailure().contains(failure));
            } else {
                dro.verify(() -> Microbot.doInvoke(argThat(menu -> action.equals(menu.getOption())
                        && menu.getIdentifier() == id
                        && menu.getType() == (id == 11727 ? MenuAction.GAME_OBJECT_SECOND_OPTION
                                : MenuAction.GAME_OBJECT_FIRST_OPTION)),eq(bounds)));
                assertNull(WalkerMouseControl.takeDoorFailure());
            }
            bank.verifyNoInteractions();
            inventory.verify(() -> Rs2Inventory.interact(anyInt(),anyString()),never());
        } finally {
            WalkerMouseControl.resetSession();
        }
    }
}
