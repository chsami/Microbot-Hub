package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.Commands;

/** Pure bounded checks on the socket thread: no client reads, IO, waits or executable payloads. */
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
            case "prayer.set":case "prayer.flick":case "movement.tile":case "npc.attack":
            case "equipment.set":case "inventory.interact":case "camera.set":case "ground.take":
            case "npc.interact":case "widget.click":case "combat.spec":case "magic.armIce":case "player.logout":
            case "equipment.slot":case "equipment.equip":case "inventory.eat":case "inventory.guzzle":
            case "combat.style":case "combat.retaliate":case "object.interact":case "dialogue.next":case "dialogue.choose":return op;
            default:return "UNKNOWN";
        }
    }
    static void validate(String op, JsonObject a, ArenaProduct product) {
        if (op==null || a==null) reject();
        switch(op) {
            case "prayer.set": keys(a,"prayer","on"); text(a,"prayer",80); bool(a,"on",true); break;
            case "prayer.flick": keys(a,"prayer"); text(a,"prayer",80); break;
            case "movement.tile":
                keys(a,"x","y","plane","screenOnly");
                integer(a,"x",0,16383);integer(a,"y",0,16383);integer(a,"plane",0,3);bool(a,"screenOnly",false);break;
            case "npc.attack": keys(a,"index");integer(a,"index",0,65535);break;
            case "equipment.set":
                keys(a,"name","items");if(a.has("name"))text(a,"name",80);
                var items=a.get("items");if(items==null||!items.isJsonArray()||items.getAsJsonArray().size()>28)reject("INVALID_EQUIPMENT_ITEMS");
                for(var item:items.getAsJsonArray())number(item,1,Integer.MAX_VALUE);break;
            case "inventory.interact":
                keys(a,"slot","itemId","option");integer(a,"slot",0,27);integer(a,"itemId",1,Integer.MAX_VALUE);
                String option=text(a,"option",80).toLowerCase(Locale.ROOT);
                if(!Commands.safeInventoryOption(option)&&!option.equals("drop"))reject("INVENTORY_OPTION_NOT_ALLOWED");break;
            case "camera.set":
                keys(a,"yaw","pitch","zoom");optionalInteger(a,"yaw",0,2047);optionalInteger(a,"pitch",128,383);optionalInteger(a,"zoom",0,1004);break;
            case "ground.take":keys(a,"name","radius");text(a,"name",80);integer(a,"radius",0,15);break;
            case "npc.interact":integer(a,"index",0,65535);text(a,"option",80);break;
            case "object.interact":
                if(a.has("ids")) {
                    var ids=a.get("ids");if(!ids.isJsonArray()||ids.getAsJsonArray().size()>128)reject("INVALID_OBJECT_IDS");
                    for(var item:ids.getAsJsonArray())number(item,1,Integer.MAX_VALUE);
                } else text(a,"name",80);
                text(a,"option",80);optionalInteger(a,"radius",0,64);bool(a,"walk",false);break;
            case "widget.click":integer(a,"id",0,Integer.MAX_VALUE);optionalInteger(a,"child",0,65535);
                if(a.has("option")&&!a.get("option").isJsonNull())text(a,"option",80);break;
            case "equipment.slot":integer(a,"slot",0,13);text(a,"option",80);break;
            case "equipment.equip":case "inventory.eat":case "inventory.guzzle":integer(a,"itemId",1,Integer.MAX_VALUE);break;
            case "combat.style":integer(a,"style",0,3);break;
            case "combat.retaliate":bool(a,"on",true);break;
            case "dialogue.choose":if(a.has("text"))text(a,"text",512);else integer(a,"n",1,10);break;
            case "dialogue.next":break;
            case "magic.armIce":keys(a,"index");optionalInteger(a,"index",0,65535);break;
            case "combat.spec":case "player.logout":keys(a);break;
            default:reject("UNKNOWN_OPERATION");
        }
    }
    static void keys(JsonObject a,String... names){
        // Protocol extensions are ignored; only arguments consumed by the dispatcher are validated.
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
    private static void optionalInteger(JsonObject a,String key,int min,int max){if(a.has(key)&&!a.get(key).isJsonNull())integer(a,key,min,max);}
    static void reject(String reason){throw new Rejection(reason);}
    private static void reject(){reject("INVALID_ARGUMENTS");}
}
