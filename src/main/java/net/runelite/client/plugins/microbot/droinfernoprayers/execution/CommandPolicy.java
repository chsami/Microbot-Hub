package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.Commands;

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
            case "prayer.set":case "prayer.flick":return op;
            default:return "UNKNOWN";
        }
    }
    static void validate(String op, JsonObject a, ArenaProduct product) {
        if (op==null || a==null) reject();
        switch(op) {
            case "prayer.set": keys(a,"prayer","on"); text(a,"prayer",80); bool(a,"on",true); break;
            case "prayer.flick": keys(a,"prayer"); text(a,"prayer",80); break;
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
    private static int integer(JsonObject a,String key,int min,int max){
        try{return number(a.get(key),min,max);}
        catch(Rejection invalid){throw new Rejection(invalid.getMessage()+"_"+key);}
    }
    private static int number(JsonElement e,int min,int max){
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())reject("MISSING_OR_INVALID_NUMBER");
        try{int n=e.getAsBigDecimal().intValueExact();if(n<min||n>max)reject("NUMBER_OUT_OF_RANGE");return n;}
        catch(ArithmeticException|NumberFormatException invalid){throw new Rejection("NUMBER_NOT_EXACT_INTEGER");}
    }
    static void reject(String reason){throw new Rejection(reason);}
    private static void reject(){reject("INVALID_ARGUMENTS");}
}
