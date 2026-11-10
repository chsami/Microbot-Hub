package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.*;
import java.util.Set;
import java.util.function.Consumer;

/** Sanitized diagnostics for rejected local actions. No transport or authentication owner. */
final class RejectedJson {
    private static final Set<String> SECRETS=Set.of("code","key","license","licence","licensekey","licencekey","apikey","pass","token","resume","resumetoken","authorization","hwid","username","ip");
    static void log(Consumer<String> log,String reason,JsonElement payload) {
        log.accept("[inferno-local] stage=REJECTED_JSON reason="+reason+" json="+clean(payload));
    }
    private static JsonElement clean(JsonElement source) {
        if(source==null)return JsonNull.INSTANCE;
        if(source.isJsonObject()) {
            JsonObject out=new JsonObject();
            for(var e:source.getAsJsonObject().entrySet()) {
                String key=e.getKey().toLowerCase(java.util.Locale.ROOT).replace("_","").replace("-","");
                out.add(e.getKey(),SECRETS.contains(key)?new JsonPrimitive("[redacted]"):clean(e.getValue()));
            }return out;
        }
        if(source.isJsonArray()){JsonArray out=new JsonArray();for(var e:source.getAsJsonArray())out.add(clean(e));return out;}
        return source;
    }
}
