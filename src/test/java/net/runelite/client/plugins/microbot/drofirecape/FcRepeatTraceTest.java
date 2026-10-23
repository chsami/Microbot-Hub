/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FcRepeatTraceTest {
    @TempDir Path directory;

    @Test void repeatFromControllerKeepsTheNewSessionTraceWritable()throws Exception{
        try(var micro=mockStatic(Microbot.class)){
            var fixture=new Fixture();fixture.clientThread(micro);
            assertTrue(fixture.script.run(fixture.config));
            // A running Future.cancel(true) interrupts its own controller thread here.
            when(fixture.first.cancel(anyBoolean())).thenAnswer(i->{if(i.getArgument(0))Thread.currentThread().interrupt();return true;});
            fixture.script.hadCave=true;fixture.script.state=DroFirecapeScript.State.COMPLETE;
            try{
                fixture.controller.get().run();
                assertFalse(Thread.currentThread().isInterrupted());
                verify(fixture.first).cancel(false);
                assertEquals(2,fixture.trace.sessions);
                fixture.trace.event("after-repeat","still writable");
                assertNotNull(fixture.trace.writer);
                String events=Files.readString(fixture.trace.directory.resolve("events.jsonl"));
                assertTrue(events.contains("\"type\":\"session\""));
                assertTrue(events.contains("\"type\":\"after-repeat\""));
            }finally{Thread.interrupted();fixture.trace.close();}
        }
    }

    @Test void externalRestartStillInterruptsThePreviousController()throws Exception{
        try(var micro=mockStatic(Microbot.class)){
            var fixture=new Fixture();fixture.clientThread(micro);
            try{
                assertTrue(fixture.script.run(fixture.config));assertTrue(fixture.script.run(fixture.config));
                verify(fixture.first).cancel(true);assertEquals(2,fixture.trace.sessions);
            }finally{fixture.trace.close();}
        }
    }

    private class Fixture {
        final ScheduledExecutorService executor=mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> first=mock(ScheduledFuture.class),second=mock(ScheduledFuture.class);
        final AtomicReference<Runnable> controller=new AtomicReference<>();
        final TempTrace trace=new TempTrace();
        final DroFirecapeConfig config=new DroFirecapeConfig(){
            @Override public AfterExitBehavior afterExitBehavior(){return AfterExitBehavior.GET_ANOTHER_CAPE;}
        };
        final DroFirecapeScript script=new DroFirecapeScript(){
            @Override protected FcFrame captureFrame(){
                FcFrame outside=mock(FcFrame.class);
                try{Field hp=FcFrame.class.getDeclaredField("hp");hp.setAccessible(true);hp.setInt(outside,99);}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
                return outside;
            }
        };
        Fixture()throws Exception{
            script.client=mock(Client.class);
            field(script,DroFirecapeScript.class,"trace",trace);
            field(script,net.runelite.client.plugins.microbot.Script.class,"scheduledExecutorService",executor);
            doAnswer(i->{controller.compareAndSet(null,i.getArgument(0));return controller.get()==i.getArgument(0)?first:second;})
                .when(executor).scheduleWithFixedDelay(any(Runnable.class),eq(0L),eq(80L),eq(TimeUnit.MILLISECONDS));
        }
        void clientThread(org.mockito.MockedStatic<Microbot> micro){
            ClientThread thread=mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            micro.when(Microbot::getClientThread).thenReturn(thread);
        }
    }
    private class TempTrace extends FcTelemetry {
        int sessions;
        @Override void open(boolean enabled){
            close();directory=FcRepeatTraceTest.this.directory.resolve("session-"+(++sessions));
            try{Files.createDirectories(directory);writer=Files.newBufferedWriter(directory.resolve("events.jsonl"));event("session","repeat test");}
            catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    private static void field(Object owner,Class<?> type,String name,Object value)throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
}
