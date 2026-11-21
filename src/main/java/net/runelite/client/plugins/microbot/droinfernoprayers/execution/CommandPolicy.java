package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;

final class CommandPolicy {
    private CommandPolicy() { }
    static final class Rejection extends IllegalArgumentException {
        Rejection(String reason) { super(reason); }
    }
    static String operation(JsonObject message) {
        if(message==null)return "INVALID";
        var e=message.get("op");
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())return "INVALID";
        String op=e.getAsString();
        switch(op) {
            case "prayer.set":return op;
            default:return "UNKNOWN";
        }
    }
    static void validate(String op, JsonObject a, ArenaProduct product) {
        if (op==null || a==null) reject();
        switch(op) {
            case "prayer.set": keys(a,"prayer","on"); text(a,"prayer",80); bool(a,"on",true); break;
            default:reject("UNKNOWN_OPERATION");
        }
    }
    static void keys(JsonObject a,String... names){
    }
    private static String text(JsonObject a,String key,int max){
        var e=a.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())reject("MISSING_OR_INVALID_TEXT_"+key);
        String s=e.getAsString();if(s.isEmpty()||s.length()>max)reject("TEXT_LENGTH_"+key);return s;
    }
    private static void bool(JsonObject a,String key,boolean required){
        var e=a.get(key);if(e==null&&!required)return;
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())reject("MISSING_OR_INVALID_BOOLEAN_"+key);
    }
    static void reject(String reason){throw new Rejection(reason);}
    private static void reject(){reject("INVALID_ARGUMENTS");}
}
