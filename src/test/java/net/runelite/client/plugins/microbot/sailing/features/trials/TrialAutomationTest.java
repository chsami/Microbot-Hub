package net.runelite.client.plugins.microbot.sailing.features.trials;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialInfo;

/** Standalone checks runnable against the installed client without launching the game. */
public final class TrialAutomationTest {
    public static void main(String[] args) {
        check(TrialAutomation.rumAction(new String[]{"Talk-to", "Collect-rum", "Deliver-rum"}, false)
                .equals("Collect-rum"), "pickup");
        check(TrialAutomation.rumAction(new String[]{"Talk-to", "Collect-rum", "Deliver-rum"}, true)
                .equals("Deliver-rum"), "delivery");
        check(TrialAutomation.rumAction(new String[]{"Talk-to", "Examine"}, true) == null, "unknown action");
        check(TrialAutomation.rumAction(null, false) == null, "missing composition actions");
        TrialInfo info = new TrialInfo();
        info.HasRum = true;
        check(!TrialAutomation.rumAcknowledged(true, 0, info), "must wait for delivery");
        check(TrialAutomation.rumAcknowledged(false, 0, info), "pickup confirmed");
        info.HasRum = false;
        check(TrialAutomation.rumAcknowledged(true, 0, info), "delivery confirmed");
        info.HasRum = true;
        info.CollectedPrimaryObjectives = 1;
        check(TrialAutomation.rumAcknowledged(true, 0, info), "counter confirmation");
        List<WorldPoint> points = List.of(new WorldPoint(100, 100, 0), new WorldPoint(110, 100, 0),
                new WorldPoint(120, 100, 0));
        check(TrialAutomation.nearestIndex(points, new WorldPoint(119, 100, 0)) == 2, "resume nearby");
        check(TrialAutomation.nearestIndex(points, null) == 0, "missing position");
        var swordfish = widget("<col=ffffff>Swordfish</col>", false);
        check(TrialAutomation.findRank(swordfish, "Swordfish") == swordfish, "configured rank button");
        check(TrialAutomation.findRank(swordfish, "Shark") == null, "never select another rank");
        check(TrialAutomation.findRank(widget("Swordfish", true), "Swordfish") == null, "hidden button");
        System.out.println("13 trial automation checks passed");
    }

    private static net.runelite.api.widgets.Widget widget(String text, boolean hidden) {
        return (net.runelite.api.widgets.Widget) java.lang.reflect.Proxy.newProxyInstance(
                TrialAutomationTest.class.getClassLoader(), new Class<?>[]{net.runelite.api.widgets.Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getText": return text;
                        case "isHidden": return hidden;
                        case "getWidth": return 60;
                        case "getHeight": return 20;
                        default: return null;
                    }
                });
    }

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
    }
}
