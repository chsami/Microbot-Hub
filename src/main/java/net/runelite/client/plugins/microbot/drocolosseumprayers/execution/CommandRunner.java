package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDiagnostics;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.inferno.InfernoCommands;

final class CommandRunner
{
	Commands commands;
	InfernoCommands infernoCommands;
    net.runelite.client.plugins.microbot.drocolosseumprayers.ColosseumUi colosseumUi;

	private final Consumer<Runnable> gameThread;
	private final Consumer<String> log;

	private final Consumer<JsonObject> send;

    private final java.util.function.BooleanSupplier allowed;
    private final Runnable violation;
    private final java.util.function.LongSupplier motionClock;
    private final PrayerDiagnostics prayers;
    void observePrayers(java.util.function.IntPredicate active){
        var values=new java.util.HashMap<Integer,Boolean>();
        java.util.function.IntPredicate once=id->values.computeIfAbsent(id,active::test);
        prayers.observe(once);if(commands!=null)commands.observePrayers(once);
        var guards=new java.util.EnumMap<net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum,Boolean>(
            net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.class);
        for(var guard:PROTECTIONS)guards.put(guard,once.test(guard.getVarbit()));
        protectionState=guards;protectionStateNanos=motionClock.getAsLong();
    }
    private static final net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum[] PROTECTIONS={
        net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_MAGIC,
        net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_RANGE,
        net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_MELEE};

    private volatile java.util.Map<net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum,Boolean> protectionState=java.util.Map.of();
    private volatile long protectionStateNanos;
    void observePrayerMenu(int widget,boolean consumed){prayers.menu(widget,consumed);}
    @FunctionalInterface interface Executor {
        void execute(String op,int priority,java.util.function.BooleanSupplier fresh,Runnable task);
        default boolean prepare(String op){return true;}
        default net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.ClickHandler mouse(){return null;}
        default void gesture(String op,int priority,java.util.function.BooleanSupplier fresh,Runnable task){execute(op,priority,fresh,task);}
    }
    private Executor executor;
    void executor(Executor value){executor=value;}

    private volatile java.util.function.Supplier<net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput> fightInput=
        ()->net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput.MICROBOT_MOUSE;
    private final net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInvoke invoke=new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInvoke();
    void fightInput(java.util.function.Supplier<net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput> value){
        fightInput=value==null?()->net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput.MICROBOT_MOUSE:value;}

    private final net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaSnapWalk snapWalk=new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaSnapWalk();

    private net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.ClickHandler handler(String op,JsonObject args){
        if("movement.tile".equals(op)&&flag(args,"snap"))return snapWalk;
        var mode=fightInput.get();
        if(mode==net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput.INVOKES&&!"movement.tile".equals(op))return invoke;
        return null;
    }
    private final java.util.concurrent.ConcurrentHashMap<String,Desired> desired=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Desired {final String signature;final long version;Desired(String signature,long version){this.signature=signature;this.version=version;}}
    private final java.util.concurrent.atomic.AtomicLong arrivals=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong prayerUrgency=new java.util.concurrent.atomic.AtomicLong();
    private net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct product=net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.INFERNO;
    void product(net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct product){this.product=product;}
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.LinkedHashSet<Long> recentIds = new java.util.LinkedHashSet<>();
	private volatile long tick;
	private volatile long tickStartMs;

	private final ConcurrentLinkedQueue<Command> scheduled = new ConcurrentLinkedQueue<>();

	private static final class Command
	{
        JsonObject raw;
        ArenaInputDiagnostics.Trace inputTrace;
        PrayerDiagnostics.Ticket prayerTrace;
        volatile boolean dispatchStarted;

        volatile boolean gestureStarted,pressing;
        volatile long dispatchNanos,motionUrgency;
        String desiredKey;long arrival,desiredVersion;int priority;
		long id;
        long generation;
		long tick;
		String op;
		JsonObject args;
		long deadlineMs;
		long arrivedAt;
	}

	CommandRunner(Consumer<Runnable> gameThread, Consumer<String> log, Consumer<JsonObject> send, java.util.function.BooleanSupplier allowed)
	{
        this(gameThread,log,send,allowed,() -> {});
    }
    CommandRunner(Consumer<Runnable> gameThread, Consumer<String> log, Consumer<JsonObject> send,
            java.util.function.BooleanSupplier allowed, Runnable violation) {
        this(gameThread,log,send,allowed,violation,System::nanoTime);
    }
    CommandRunner(Consumer<Runnable> gameThread,Consumer<String> log,Consumer<JsonObject> send,
            java.util.function.BooleanSupplier allowed,Runnable violation,java.util.function.LongSupplier motionClock){
        this.motionClock=motionClock;
        this.violation=violation;
        this.prayers=new PrayerDiagnostics(values->{
            log.accept("[prayer] "+values);
            var bus=net.runelite.client.plugins.microbot.Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic(values));
        });
		this.gameThread = gameThread;
        executor=(op,priority,fresh,task)->gameThread.accept(task);
		this.log = log;
		this.send = send;
        this.allowed = allowed;
	}

	long tick()
	{
		return tick;
	}

	void nextTick()
	{
		tick++;
		tickStartMs = System.currentTimeMillis();
	}

	void runDue()
	{
		List<Command> due = new ArrayList<>();
		scheduled.removeIf(c -> c.tick <= tick && due.add(c));
		due.forEach(this::submit);
	}

	void clear()
	{
        generation.incrementAndGet();
        prayers.clear();protectionState=java.util.Map.of();
        if(commands!=null)commands.clearPrayerSnapshot();
		for(Command c:scheduled)if(c.inputTrace!=null)c.inputTrace.event("COMMAND_DROPPED","SESSION_CLEARED_BEFORE_DUE",0);
		scheduled.clear();desired.clear();
	}

	synchronized void onCommand(JsonObject m)
	{
        try { acceptCommand(m); }
        catch(RuntimeException invalid) {
            String reason=invalid instanceof CommandPolicy.Rejection?invalid.getMessage():"MALFORMED_COMMAND";
            log.accept("[session] stage=COMMAND_REJECTED op="+CommandPolicy.operation(m)+" reason="+reason);
            RejectedJson.log(log,reason,m);

            try {
                long id=m.get("id").getAsBigDecimal().longValueExact();
                if(id>=0) {
                    var rejected=new Command();rejected.id=id;rejected.op=CommandPolicy.operation(m);
                    rejected.arrivedAt=System.currentTimeMillis();
                    rejected.inputTrace=new ArenaInputDiagnostics.Trace(id,rejected.op,v->{});
                    result(rejected,"BAD_COMMAND",tick,rejected.arrivedAt,false);
                }
            } catch(RuntimeException unidentifiable) {  }
        }
    }
    private void acceptCommand(JsonObject m) {
        CommandPolicy.keys(m,"type","id","tick","op","args","deadlineMs");
		Command c = new Command();
		c.raw = m;
		c.arrivedAt = System.currentTimeMillis();
        c.generation = generation.get();
		c.id = m.get("id").getAsLong();
		c.tick = m.has("tick") ? m.get("tick").getAsLong() : 0;
		c.op = m.get("op").getAsString();
		c.args = m.has("args") ? m.getAsJsonObject("args") : new JsonObject();
		c.deadlineMs = m.has("deadlineMs") ? m.get("deadlineMs").getAsLong() : 0;
        if(c.id<0)CommandPolicy.reject("NEGATIVE_ID");
        if(recentIds.contains(c.id))CommandPolicy.reject("DUPLICATE_ID");
        if(c.tick<0||c.tick>tick+10)CommandPolicy.reject("TICK_OUT_OF_RANGE");
        if(c.deadlineMs<0||c.deadlineMs>600)CommandPolicy.reject("DEADLINE_OUT_OF_RANGE");
        if(scheduled.size()>=128)CommandPolicy.reject("QUEUE_LIMIT");
        CommandPolicy.validate(c.op,c.args,product);
        if(c.op.equals("prayer.set")||c.op.equals("prayer.flick"))
            c.prayerTrace=prayers.received(c.id,c.op,str(c.args,"prayer"),c.op.equals("prayer.flick")||c.args.get("on").getAsBoolean(),c.tick,tick,c.deadlineMs);
        c.arrival=arrivals.incrementAndGet();c.priority=priority(c.op,m);
        c.desiredKey=c.op.equals("movement.tile")||c.op.equals("camera.set")||c.op.equals("equipment.set")?c.op:
            c.prayerTrace!=null?"prayer:"+Commands.prayerEnum(str(c.args,"prayer")):null;
        if(c.desiredKey!=null){
            String signature=c.prayerTrace!=null?Boolean.toString(c.op.equals("prayer.flick")||c.args.get("on").getAsBoolean()):
                c.op.equals("movement.tile")?num(c.args,"x")+":"+num(c.args,"y")+":"+num(c.args,"plane")+":"+flag(c.args,"screenOnly"):
                c.op.equals("equipment.set")?String.valueOf(c.args.get("name")):
                maybe(c.args,"yaw")+":"+maybe(c.args,"pitch")+":"+maybe(c.args,"zoom");
            c.desiredVersion=desired.compute(c.desiredKey,(key,old)->old!=null&&old.signature.equals(signature)?old:new Desired(signature,c.arrival)).version;
        }
        c.inputTrace=new ArenaInputDiagnostics.Trace(c.id,c.op,values->{
            if ("DELIVERY_RECEIPT".equals(values.get("stage"))) log.accept("[input] " + values);
            var bus=net.runelite.client.plugins.microbot.Microbot.getEventBus();
            if(bus!=null)bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic(values));
        });
        c.inputTrace.event("COMMAND_RECEIVED","VALIDATED",0);
        publishCommand(c);
        recentIds.add(c.id);
        if(recentIds.size()>256) recentIds.remove(recentIds.iterator().next());
		long now = tick;
		if (c.tick != 0 && c.tick < now)
		{
			RejectedJson.log(log,"STALE",c.raw);
			result(c, "STALE", now, c.arrivedAt, false);
		}
		else if (c.tick == 0 || c.tick == now)
		{
			submit(c);
		}
		else
		{
			scheduled.add(c);
		}
	}

    private void publishCommand(Command c){
        var bus=net.runelite.client.plugins.microbot.Microbot.getEventBus();
        if(bus==null)return;
        bus.post(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic(java.util.Map.of(
            "event","arena_command","id",c.id,"op",c.op,"args",c.args.toString(),"engineTick",c.tick,
            "deadlineMs",c.deadlineMs,"receivedTick",tick,"sinceTickMs",Math.max(0,c.arrivedAt-tickStartMs))));
    }

    static boolean protectionPrayer(String name){
        if(name==null)return false;
        try{
            var p=Commands.prayerEnum(name);
            return p==net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_MAGIC
                ||p==net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_RANGE
                ||p==net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_MELEE;
        }catch(RuntimeException unknown){return false;}
    }

    private boolean urgentPrayer(Command c){
        if(c.prayerTrace==null||!fresh(c)||!protectionPrayer(str(c.args,"prayer")))return false;
        Boolean active=motionClock.getAsLong()-protectionStateNanos>1_200_000_000L?null
            :protectionState.get(Commands.prayerEnum(str(c.args,"prayer")));
        return active==null||active!=(c.op.equals("prayer.flick")||c.args.get("on").getAsBoolean());
    }
    private void submit(Command c){
        if(urgentPrayer(c))prayerUrgency.incrementAndGet();
        try(var diagnostic=ArenaInputDiagnostics.open(c.inputTrace)){
            executor.execute(c.op,c.priority,()->fresh(c),ArenaInputDiagnostics.capture(()->run(c)));
        }
        catch(java.util.concurrent.RejectedExecutionException full){c.inputTrace.event("COMMAND_DROPPED","INPUT_BACKLOG",0);prayers.result(c.prayerTrace,"INPUT_BACKLOG",false);RejectedJson.log(log,"INPUT_BACKLOG",c.raw);result(c,"FAILED",tick,System.currentTimeMillis(),false);}
    }
    private boolean current(Command c){return c.generation==generation.get()&&(c.desiredKey==null||desired.get(c.desiredKey)!=null&&desired.get(c.desiredKey).version==c.desiredVersion);}
    private boolean fresh(Command c){long now=System.currentTimeMillis();return current(c)&&(c.tick==0||c.tick==tick)
        &&now-c.arrivedAt<=600L&&(c.deadlineMs==0||now<=(c.tick==0?c.arrivedAt:tickStartMs)+c.deadlineMs);}
    private boolean motionFresh(Command c){
        if(colosseumWalk(c))return colosseumWalkFresh(c);
        if(!current(c))return false;

        if(c.prayerTrace!=null&&c.dispatchStarted)
            return motionClock.getAsLong()-c.dispatchNanos<=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(600);

        if("equipment.set".equals(c.op)&&c.dispatchStarted)
            return motionClock.getAsLong()-c.dispatchNanos<=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1200)
                &&c.motionUrgency==prayerUrgency.get();
        return fresh(c)&&(!c.dispatchStarted||c.motionUrgency==prayerUrgency.get());
    }

    private boolean colosseumWalk(Command c){
        return c.dispatchStarted&&product==net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.COLOSSEUM
            &&"movement.tile".equals(c.op);
    }

    private boolean colosseumWalkFresh(Command c){
        if(c.generation!=generation.get())return false;
        if(c.pressing)return true;
        if(c.gestureStarted)return current(c)&&c.motionUrgency==prayerUrgency.get();
        return fresh(c);
    }
    private boolean queueGesture(Command c,Runnable gesture){
        var trace=c.inputTrace.gesture();
        trace.event("GESTURE_QUEUED","SUBMITTED",0);
        try(var diagnostic=ArenaInputDiagnostics.open(trace)){
        executor.gesture(c.op,c.priority,()->motionFresh(c),net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.capture(()->{
            try {
            if(!motionFresh(c)||!allowed.getAsBoolean()){
                ArenaInputDiagnostics.event("GESTURE_CANCELLED","GESTURE_CANCELLED");prayers.gesture(c.prayerTrace,"GESTURE_CANCELLED");return;
            }

            if(c.prayerTrace!=null){
                Boolean active=commands.prayerActive(str(c.args,"prayer"));
                if(active==null){ArenaInputDiagnostics.event("PRAYER_STATE_UNAVAILABLE","PRAYER_STATE_UNAVAILABLE");prayers.gesture(c.prayerTrace,"PRAYER_STATE_UNAVAILABLE");return;}
                if(active==(c.op.equals("prayer.flick")||c.args.get("on").getAsBoolean())){
                    ArenaInputDiagnostics.event("PRAYER_ALREADY_APPLIED","PRAYER_ALREADY_APPLIED");prayers.gesture(c.prayerTrace,"PRAYER_ALREADY_APPLIED");return;
                }
            }
            if(c.prayerTrace==null&&!executor.prepare(c.op)){ArenaInputDiagnostics.event("TAB_NOT_READY","TAB_NOT_READY");prayers.gesture(c.prayerTrace,"TAB_NOT_READY");return;}
            prayers.gesture(c.prayerTrace,"GESTURE_STARTED");

            if(colosseumWalk(c)){c.motionUrgency=prayerUrgency.get();c.gestureStarted=true;}
            gesture.run();
            } catch(RuntimeException failure){
                ArenaInputDiagnostics.event("GESTURE_EXCEPTION",failure.getClass().getSimpleName());throw failure;
            } finally { ArenaInputDiagnostics.event("GESTURE_END","CALLBACK_FINISHED"); trace.finishGesture(); }
        }));
        } catch(java.util.concurrent.RejectedExecutionException rejected){
            trace.event("GESTURE_DROPPED","INPUT_BACKLOG_OR_CLOSED",0);throw rejected;
        }
        return true;
    }
    private static String prayerArg(JsonObject message){
        try{var a=message.getAsJsonObject("args");return a!=null&&a.has("prayer")?a.get("prayer").getAsString():null;}
        catch(RuntimeException malformed){return null;}
    }
    static int priority(String op,JsonObject message){
        int metadata=0;
        try{if(message.has("priority")&&message.get("priority").isJsonPrimitive()&&message.get("priority").getAsJsonPrimitive().isNumber())metadata=Math.max(0,Math.min(100,message.get("priority").getAsBigDecimal().intValueExact()));}
        catch(ArithmeticException|NumberFormatException ignored){ }

        int tier=op.startsWith("prayer.")?(protectionPrayer(prayerArg(message))?4:3)
            :op.startsWith("inventory.")||op.equals("equipment.set")?2:op.equals("movement.tile")?1:0;
        return tier*101+metadata;
    }

	private void run(Command c)
	{
		long startTick = tick;
		long startMs = System.currentTimeMillis();
		if (c.deadlineMs > 0 && startMs > (c.tick==0?c.arrivedAt:tickStartMs) + c.deadlineMs)
		{
			RejectedJson.log(log,"EXPIRED",c.raw);
			result(c, "EXPIRED", startTick, startMs, false);
			return;
		}
        if (c.generation != generation.get() || !allowed.getAsBoolean()) {
            RejectedJson.log(log,"ARENA_OR_SESSION_HELD",c.raw);
            result(c, "BLOCKED", startTick, startMs, false); return;
        }
        if (!current(c)||(c.tick != 0 && c.tick != startTick) || startMs - c.arrivedAt > 600L) {
            RejectedJson.log(log,"STALE",c.raw);
            result(c, "STALE", startTick, startMs, false); return;
        }
        c.dispatchNanos=motionClock.getAsLong();c.motionUrgency=prayerUrgency.get();c.dispatchStarted=true;
		String outcome;
		boolean done = false;
        final net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.ClickHandler handler=handler(c.op,c.args);
        try(PrayerDiagnostics.Scope prayerScope=prayers.dispatch(c.prayerTrace);
            net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope scope=
            net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.open(new net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.Profile(){
                public net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory motion(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory source){return net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMouseMotion.withoutMistakes(source);}
                public long clickPause(){return 0;}
                public boolean immediateTabs(){return true;}
                public net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.ClickHandler mouse(){return handler;}
                public boolean scheduleGesture(Runnable gesture){return queueGesture(c,gesture);}
                public boolean shouldClick(){
                    if(c.prayerTrace==null){if(colosseumWalk(c))c.pressing=true;return true;}
                    Boolean active=commands.prayerActive(str(c.args,"prayer"));
                    if(active==null){ArenaInputDiagnostics.event("PRAYER_STATE_UNAVAILABLE","PRAYER_STATE_UNAVAILABLE");prayers.gesture(c.prayerTrace,"PRAYER_STATE_UNAVAILABLE");return false;}
                    c.prayerTrace.before(active);
                    boolean desired=c.op.equals("prayer.flick")||c.args.get("on").getAsBoolean();
                    if (active==desired) ArenaInputDiagnostics.event("PRAYER_ALREADY_APPLIED","PREPRESS_ALREADY_SATISFIED");
                    return active!=desired;
                }
                public boolean isValid(){return motionFresh(c)&&allowed.getAsBoolean()&&!net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
                    &&!net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();}
            }))
        {
            if(c.prayerTrace!=null||executor.prepare(c.op))done = dispatch(c.op, c.args);
            else { ArenaInputDiagnostics.event("TAB_NOT_READY","COMMAND_PREPARATION");log.accept("[input] stage=TAB_NOT_READY op="+c.op); }
			outcome = done ? "DONE" : "FAILED";
            if(!done)RejectedJson.log(log,"ACTION_NOT_DELIVERED",c.raw);
		}
		catch (IllegalArgumentException e)
		{
            RejectedJson.log(log,"DISPATCH_REJECTED",c.raw);
			log.accept("Rejected local command");
			outcome = "BAD_COMMAND";
		}
		catch (RuntimeException e)
		{
			RejectedJson.log(log,"EXECUTION_ERROR",c.raw);
			log.accept("Local command failed");
			outcome = "ERROR";
		}
		result(c, outcome, startTick, startMs, done);
	}

	boolean dispatch(String op, JsonObject a)
	{
		switch (op)
		{
			case "prayer.set":
				return commands.prayerSet(str(a, "prayer"), a.get("on").getAsBoolean());
			case "prayer.flick":
				return commands.prayerFlick(str(a, "prayer"));
			default:
				throw new IllegalArgumentException("unknown op " + op);
        }
	}


	private void result(Command c, String outcome, long startTick, long startMs, boolean delivered)
	{
		long now = System.currentTimeMillis();
        c.inputTrace.event("SUBMISSION_RESULT",outcome,0);
        if (c.inputTrace.gestureCount()==0) c.inputTrace.event("COMMAND_NO_GESTURE",doneReason(outcome),0);
        prayers.result(c.prayerTrace,outcome,delivered);
		JsonObject r = new JsonObject();
		r.addProperty("type", "result");
		r.addProperty("id", c.id);
		r.addProperty("op", c.op);
		r.addProperty("outcome", outcome);
		r.addProperty("delivered", delivered);
		r.addProperty("tick", startTick);
		r.addProperty("waitMs", Math.max(0, startMs - c.arrivedAt));
		r.addProperty("runMs", Math.max(0, now - startMs));
		r.addProperty("doneTick", tick);
		r.addProperty("atMs", Math.max(0, now - tickStartMs));
		send.accept(r);
	}

    private static String doneReason(String outcome) {
        return "DONE".equals(outcome) ? "STATE_OR_NON_MOUSE_ACTION_REPORTED_DONE" : outcome;
    }

	private static String str(JsonObject a, String key)
	{
		if (!a.has(key))
		{
			throw new IllegalArgumentException("missing " + key);
		}
		return a.get(key).getAsString();
	}

	private static int num(JsonObject a, String key)
	{
		if (!a.has(key))
		{
			throw new IllegalArgumentException("missing " + key);
		}
		return a.get(key).getAsInt();
	}

	private static Integer maybe(JsonObject a, String key)
	{
		return a.has(key) && !a.get(key).isJsonNull() ? a.get(key).getAsInt() : null;
	}

	private static boolean flag(JsonObject a, String key)
	{
		return a.has(key) && a.get(key).getAsBoolean();
	}

}
