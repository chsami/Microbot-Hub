/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise actual startup scheduling, frame capture and teleport dispatch, including empty loadouts. */
public class StartupLifecycleTest {
    private static void set(Object object,String field,Object value)throws Exception {
        Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);
    }
    private static Object get(Object object,String field)throws Exception {
        Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);
    }
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);
        final WorldView world=mock(WorldView.class);
        final Player player=mock(Player.class);
        final FcActions actions=mock(FcActions.class);
        final FcKeybindings keys=mock(FcKeybindings.class);
        final DroFirecapeScript script=new DroFirecapeScript();
        final DroFirecapeConfig config=mock(DroFirecapeConfig.class);
        final net.runelite.client.config.ConfigManager saved=mock(net.runelite.client.config.ConfigManager.class);
        final List<Runnable> tasks=new ArrayList<>();
        final List<Long> delays=new ArrayList<>();
        final List<ScheduledFuture<?>> futures=new ArrayList<>();
        final List<String> logs=new ArrayList<>();
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> players=mockStatic(Rs2Player.class);
        final MockedStatic<SessionFatigue> fatigue=mockStatic(SessionFatigue.class);
        final boolean paused=Microbot.pauseAllScripts.get();
        Fixture()throws Exception {
            Microbot.pauseAllScripts.set(false);
            ClientThread clientThread=mock(ClientThread.class);
            when(clientThread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            Rs2NpcCache cache=mock(Rs2NpcCache.class,RETURNS_DEEP_STUBS);
            when(cache.query().toList()).thenReturn(List.of());
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            microbot.when(Microbot::isLoggedIn).thenAnswer(i->client.getGameState()==GameState.LOGGED_IN);
            microbot.when(()->Microbot.log(anyString())).thenAnswer(i->{logs.add(i.getArgument(0));return null;});
            fatigue.when(SessionFatigue::isActive).thenReturn(true);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(world);
            when(client.getWorldView(anyInt())).thenReturn(world);
            when(client.getTickCount()).thenReturn(100);when(client.getWorld()).thenReturn(307);
            when(world.getBaseX()).thenReturn(3200);when(world.getBaseY()).thenReturn(3200);
            CollisionData collision=mock(CollisionData.class);when(collision.getFlags()).thenReturn(new int[104][104]);
            when(world.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
            when(player.getLocalLocation()).thenReturn(new LocalPoint(50*128+64,50*128+64,WorldView.TOPLEVEL));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3250,3250,0));
            when(client.getRealSkillLevel(any())).thenReturn(99);when(client.getBoostedSkillLevel(any())).thenReturn(99);
            when(config.inventorySetup()).thenReturn(mock(InventorySetup.class));
            when(keys.step(false)).thenReturn(true);when(keys.result()).thenReturn("Default F keys confirmed");
            when(actions.minigameStep()).thenReturn("TELEPORT_SENT");
            set(script,"client",client);set(script,"actions",actions);set(script,"keybindings",keys);
            when(saved.getConfiguration(DroFirecapeConfig.GROUP,"oneTickPrayersV033")).thenReturn("false");
            set(script,"configManager",saved);
            set(script,"baseProfile",mock(BaseProfileDro.class));
            ScheduledExecutorService executor=mock(ScheduledExecutorService.class);
            when(executor.scheduleWithFixedDelay(any(Runnable.class),anyLong(),anyLong(),any())).thenAnswer(i->{
                tasks.add(i.getArgument(0));delays.add(i.getArgument(2));
                ScheduledFuture<?> future=mock(ScheduledFuture.class);when(future.isDone()).thenReturn(true);
                futures.add(future);return future;
            });
            Field field=Script.class.getDeclaredField("scheduledExecutorService");field.setAccessible(true);field.set(script,executor);
            assertTrue(script.run(config));assertEquals(2,tasks.size());
        }
        void tick(){tasks.get(0).run();}
        public void close(){fatigue.close();players.close();microbot.close();Microbot.pauseAllScripts.set(paused);}
    }
    @Test public void emptyInventoryAndEquipmentStillCaptureAndStartTeleport()throws Exception {
        try(Fixture f=new Fixture()) {
            f.tick();assertNotNull(f.script.frame());assertTrue(f.script.frame().inventory.isEmpty());
            assertEquals(DroFirecapeScript.State.TELEPORT,f.script.state());
            verify(f.actions).minigameStep();
            assertTrue(f.logs.stream().anyMatch(s->s.contains("state=TELEPORT")&&s.contains("waiting for landing")));
            f.tick();verify(f.actions,times(1)).minigameStep();
        }
    }
    @Test public void missingCollisionWaitsWithoutInputAndRecoversWhenSceneLoads()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.world.getCollisionMaps()).thenReturn(null);f.tick();
            assertNull(f.script.frame());assertEquals("Waiting for collision map",f.script.status());
            verify(f.actions,never()).minigameStep();
            assertTrue(f.logs.stream().anyMatch(s->s.contains("Waiting for collision map")));
            CollisionData map=mock(CollisionData.class);when(map.getFlags()).thenReturn(new int[104][104]);
            when(f.world.getCollisionMaps()).thenReturn(new CollisionData[]{map});f.tick();
            verify(f.actions).minigameStep();
        }
    }
    @Test public void pausePreventsStartupInputsAndReleaseContinues()throws Exception {
        try(Fixture f=new Fixture()) {
            Microbot.pauseAllScripts.set(true);f.tick();verify(f.keys,never()).step(anyBoolean());
            verify(f.actions,never()).minigameStep();
            Microbot.pauseAllScripts.set(false);f.tick();verify(f.actions).minigameStep();
        }
    }
    @Test public void restartClearsPreviousTeleportAndFailure()throws Exception {
        try(Fixture f=new Fixture()) {
            f.tick();set(f.script,"teleportAt",System.currentTimeMillis()-60_000);
            set(f.script,"warning","Previous teleport failed");
            assertTrue(f.script.run(f.config));f.tasks.get(2).run();
            assertEquals(DroFirecapeScript.State.TELEPORT,f.script.state());
            verify(f.actions,times(2)).minigameStep();
            assertFalse(f.script.warning().contains("Previous teleport failed"));
        }
    }
    @Test public void teleportLandingAdvancesToBankWithoutSendingAnotherTeleport()throws Exception {
        try(Fixture f=new Fixture()) {
            f.tick();
            when(f.world.getBaseX()).thenReturn(2368);when(f.world.getBaseY()).thenReturn(5120);
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(2418,5170,0));
            f.script.onGameTick();f.tick();
            assertEquals(DroFirecapeScript.State.WALK_BANK,f.script.state());
            verify(f.actions,times(1)).minigameStep();
        }
    }
    @Test public void lowRunEnergyDoesNotHoldCompletedCameraPreparation()throws Exception {
        int previousThreshold=Microbot.runEnergyThreshold;
        Microbot.runEnergyThreshold=1000;
        try {
            for(int energy:new int[]{0,1,500,1000,1001}) {
                try(Fixture f=new Fixture(); MockedStatic<net.runelite.client.plugins.microbot.util.bank.Rs2Bank> bank=mockStatic(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.class)) {
                    ItemContainer inventory=mock(ItemContainer.class),equipment=mock(ItemContainer.class);
                    when(inventory.getItems()).thenReturn(new Item[]{new Item(3024,1)});
                    Item[] gear=new Item[14];gear[3]=new Item(9185,1);
                    when(equipment.getItems()).thenReturn(gear);
                    when(equipment.getItem(3)).thenReturn(gear[3]);
                    when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
                    when(f.client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
                    when(f.client.getItemDefinition(anyInt())).thenAnswer(i->{
                        ItemComposition item=mock(ItemComposition.class);
                        when(item.getName()).thenReturn((int)i.getArgument(0)==3024?"Super restore(4)":"Rune crossbow");
                        return item;
                    });
                    when(f.client.getEnergy()).thenReturn(energy);
                    when(f.client.getVarpValue(172)).thenReturn(1);
                    when(f.actions.zoomConfirmed()).thenReturn(true);
                    set(f.script,"state",DroFirecapeScript.State.CAMERA);set(f.script,"cameraSet",true);
                    f.script.onGameTick();
                    java.lang.reflect.Method outside=DroFirecapeScript.class.getDeclaredMethod("outside",FcFrame.class);
                    outside.setAccessible(true);outside.invoke(f.script,get(f.script,"frame"));
                    if(energy<=1000) {
                        assertEquals("Low energy must allow walking: "+f.script.status(),DroFirecapeScript.State.WALK_ENTRANCE,f.script.state());
                        verify(f.actions,never()).enableRun();
                    } else {
                        assertEquals(DroFirecapeScript.State.CAMERA,f.script.state());verify(f.actions).enableRun();
                    }
                }
            }
        } finally {Microbot.runEnergyThreshold=previousThreshold;}
    }
    @Test public void oldDisabledCheckboxCannotRemoveTheMandatoryPrayerOwner()throws Exception {
        try(Fixture f=new Fixture()) {
            FcTickPrayers driver=(FcTickPrayers)get(f.script,"tickPrayers");assertNotNull(driver);
            verify(f.actions).tickPrayerDriver(driver);assertEquals(List.of(80L,10L),f.delays);
            verify(f.saved,never()).getConfiguration(DroFirecapeConfig.GROUP,"oneTickPrayersV033");
            for(java.lang.reflect.Method method:DroFirecapeConfig.class.getDeclaredMethods()) {
                net.runelite.client.config.ConfigItem item=method.getAnnotation(net.runelite.client.config.ConfigItem.class);
                if(item!=null)assertNotEquals("oneTickPrayersV033",item.keyName());
            }
        }
    }
    @Test public void restartCancelsBothWorkersAndPermanentlyDetachesThePreviousOwner()throws Exception {
        try(Fixture f=new Fixture()) {
            FcTickPrayers previous=(FcTickPrayers)get(f.script,"tickPrayers");
            f.script.run(f.config);FcTickPrayers next=(FcTickPrayers)get(f.script,"tickPrayers");
            assertNotSame(previous,next);verify(f.futures.get(0)).cancel(true);verify(f.futures.get(1)).cancel(true);
            previous.gameTick(RecoveryPolicyTest.frame(101,99,100),net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection.MAGIC);
            assertFalse(previous.ownsInput());assertFalse(previous.protectionReady());
            assertEquals(List.of(80L,10L,80L,10L),f.delays);
        }
    }
    @Test public void shutdownClearsTheActionOwnerAndCannotReacquireOnALateTick()throws Exception {
        try(Fixture f=new Fixture()) {
            FcTickPrayers previous=(FcTickPrayers)get(f.script,"tickPrayers");
            f.script.shutdown();assertNull(get(f.script,"tickPrayers"));verify(f.actions).tickPrayerDriver(null);
            previous.gameTick(RecoveryPolicyTest.frame(101,99,100),net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection.MAGIC);
            previous.clientTick(true,true,false);previous.pulse(f.actions);
            assertFalse(previous.ownsInput());verify(f.actions,never()).tickPrayer(any(),any(),any(),any());
        }
    }
}
