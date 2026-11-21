package net.runelite.client.plugins.microbot.autofarmer.randomevents;

import java.util.*;
import java.util.regex.Pattern;

/** No action in this policy ever selects a skill or uses an inventory reward. */
final class RandomEventPolicy {
    enum Kind { GIFT, SANDWICH, BEEKEEPER, MAZE, MIME, DRILL, GRAVE, MANUAL }
    static final Set<Integer> EVENTS=Set.of(307,314,322,326,327,375,376,380,390,5429,
        5436,5437,5438,5439,5440,5441,5510,6738,6743,6744,6746,6747,6748,6749,
        6750,6751,6752,6753,6755,12551,12552);
    static final Set<Integer> XP_EVENTS=Set.of(326,327,12551,12552);
    static boolean acceptXp(int id){return XP_EVENTS.contains(id);}
    static boolean dismissInvitation(int id){return EVENTS.contains(id) && !acceptXp(id);}
    static final Set<Integer> GIFTS=Set.of(322,326,327,375,376,6750,6751,12551,12552);
    static Kind kind(int id) {
        if(id==6752 || id==325)return Kind.MAZE;
        if(id==6753 || id==321)return Kind.MIME;
        if(id==6743 || id==337)return Kind.DRILL;
        if(id==6746 || id==6745)return Kind.GRAVE;
        if(id==6747 || id==3235) return Kind.BEEKEEPER; return GIFTS.contains(id)?Kind.GIFT:id==5510?Kind.SANDWICH:Kind.MANUAL; }
    static boolean puzzle(Kind kind){return kind==Kind.MAZE || kind==Kind.MIME || kind==Kind.DRILL || kind==Kind.GRAVE;}
    static boolean ownedEvent(int id,boolean targetingLocal,int distance,int plane,int localPlane) {
        return EVENTS.contains(id) && targetingLocal && distance<=8 && distance>=0 && plane==localPlane;
    }
    static int rewardSlots(int id) { return id==322?2:1; }
    static final Map<String,Integer> FOODS;
    static {
        Map<String,Integer> foods=new LinkedHashMap<>();
        foods.put("baguette",6961); foods.put("triangle sandwich",6962); foods.put("square sandwich",6965);
        foods.put("pie",2327); foods.put("roll",6963); foods.put("meat pie",2327); foods.put("kebab",1971); foods.put("chocolate bar",1973);
        FOODS=Collections.unmodifiableMap(foods);
    }
    static String plain(String text) { return text==null?"":text.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT).trim(); }
    static int requestedFood(String speech) {
        String text=plain(speech); Set<Integer> matches=new HashSet<>();
        FOODS.forEach((name,id)-> { if(Pattern.compile("\\b"+Pattern.quote(name)+"\\b").matcher(text).find()) matches.add(id); });
        return matches.size()==1?matches.iterator().next():-1;
    }
    static int trayModel(int food) {
        switch(food){case 6961:return 10726;case 6963:return 10727;case 1973:return 10728;
            case 1971:return 10729;case 2327:return 10730;case 6965:return 10731;case 6962:return 10732;default:return -1;}
    }
    static boolean matchesFood(int requested,int itemId,int modelType,int modelId,int inventoryModel) {
        return requested>0 && (itemId==requested || modelType==1 && inventoryModel>0 && modelId==inventoryModel);
    }
    static boolean sameSpeaker(String expected,String actual) {
        return !plain(expected).isEmpty() && plain(expected).equals(plain(actual));
    }
}
