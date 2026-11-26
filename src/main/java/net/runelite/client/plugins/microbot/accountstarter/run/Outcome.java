package net.runelite.client.plugins.microbot.accountstarter.run;

import lombok.Value;

@Value
public class Outcome {
    public enum Kind { CONTINUE, NATURAL_POINT, DONE, PARKED, FAILED }

    Kind kind;
    String reason;
    boolean replan;

    public static Outcome cont() { return new Outcome(Kind.CONTINUE, "", false); }
    public static Outcome natural(String reason) { return new Outcome(Kind.NATURAL_POINT, reason, false); }
    public static Outcome replan(String reason) { return new Outcome(Kind.NATURAL_POINT, reason, true); }
    public static Outcome done(String reason) { return new Outcome(Kind.DONE, reason, false); }
    public static Outcome parked(String reason) { return new Outcome(Kind.PARKED, reason, false); }
    public static Outcome failed(String reason) { return new Outcome(Kind.FAILED, reason, false); }
}
