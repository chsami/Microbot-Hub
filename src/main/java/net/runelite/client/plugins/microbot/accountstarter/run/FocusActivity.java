package net.runelite.client.plugins.microbot.accountstarter.run;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.FocusPlan;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

@Slf4j
public final class FocusActivity implements Activity {
    private final double glanceChance;
    private boolean cleared;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BUDGET_MS = 20_000;
    private static final int MAX_SCROLLS = 6;
    private static final String SHOW_TASK = "Show task: ";

    private final ApTask task;
    private final ApPace pace;
    private final Random random;

    public FocusActivity(ApTask task, double glanceChance, ApPace pace, Random random) {
        this.glanceChance = glanceChance;
        this.task = task;
        this.pace = pace;
        this.random = random;
    }

    @Override
    public String describe() {
        return "Setting " + task + " as the Adventure Paths focus";
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        if (!cleared) {
            Dialogs.close();
            cleared = true;
        }
        if (!needed()) {
            if (random.nextDouble() >= glanceChance) return Outcome.done("focus already on " + task);
            BreakHandlerScript.setLockState(true);
            try {
                String why = attempt();
                if (why == null) Global.sleep((int) pace.readMs(taskName(), random));
            } finally {
                close();
                BreakHandlerScript.setLockState(false);
            }
            return Outcome.done("looked at " + task + " in Adventure Paths");
        }
        long deadline = System.currentTimeMillis() + BUDGET_MS;
        String why = "out of time";
        BreakHandlerScript.setLockState(true);
        try {
            for (int i = 0; i < MAX_ATTEMPTS && System.currentTimeMillis() < deadline; i++) {
                why = attempt();
                if (why == null) {
                    if (Global.sleepUntil(() -> !needed(), 3000)) return Outcome.done("focus set on " + task);
                    why = "the focus varbits did not change";
                }
            }
        } finally {
            close();
            BreakHandlerScript.setLockState(false);
        }
        log.info("Account Starter: focus not set: {}", why);
        return Outcome.done("focus not set: " + why);
    }

    @Override
    public void abort() {
        BreakHandlerScript.setLockState(false);
    }

    private String attempt() {
        if (!open()) return "could not open the Adventure Paths window";
        int rows = ApTask.of(task.path()).size();
        if (rowCount() != rows) {
            if (rowCount() > 0 && click(InterfaceID.Adventurepath.BACK, "Back")) {
                Global.sleepUntil(() -> rowCount() == 0, 2000);
            }
            if (!click(InterfaceID.Adventurepath.PATHS, FocusPlan.selectAction(task.path()))
                    || !Global.sleepUntil(() -> rowCount() == rows, 3000)) {
                return "could not show the " + task.path() + " path";
            }
        }
        int row = FocusPlan.rowIndex(task);
        String name = Text.removeTags(rowAction(row)).replace(SHOW_TASK, "");
        if (name.isEmpty()) return "no task row " + row;
        if (!scrollIntoView(row)) return "task row " + row + " out of view";
        Global.sleep((int) pace.reactionMs(random));
        if (!clickRect(rowBounds(row))) return "task row " + row + " has no bounds";
        if (!Global.sleepUntil(() -> name.equals(taskName()), 2000)) {
            return "task " + name + " did not show";
        }
        if (find(InterfaceID.Adventurepath.TASK_FOCUS, "Remove Focus"::equals) != null) return null;
        return click(InterfaceID.Adventurepath.TASK_FOCUS, "Set Focus") ? null : "no Set Focus button";
    }

    private boolean open() {
        if (Rs2Widget.isWidgetVisible(InterfaceID.Adventurepath.UNIVERSE)) return true;
        if (!Rs2Widget.isWidgetVisible(InterfaceID.AdventurepathSide.OPEN_INTERFACE)) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.QUESTS) {
                Global.sleep((int) pace.reactionMs(random));
                Rs2Tab.switchTo(InterfaceTab.QUESTS);
                Global.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.QUESTS, 2000);
            }
            if (!Rs2Widget.isWidgetVisible(InterfaceID.AdventurepathSide.OPEN_INTERFACE)
                    && (!click(InterfaceID.SideJournal.ADVENTUREPATH_LIST, "Adventure Paths")
                    || !Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.AdventurepathSide.OPEN_INTERFACE), 2000))) {
                return false;
            }
        }
        return click(InterfaceID.AdventurepathSide.OPEN_INTERFACE, "Show Paths")
                && Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Adventurepath.UNIVERSE), 3000);
    }

    /** Escape only works with "Esc closes the current interface" on. */
    private void close() {
        if (Rs2Widget.isWidgetVisible(InterfaceID.Adventurepath.UNIVERSE)) {
            boolean closed = click(InterfaceID.Adventurepath.FRAME, "Close")
                    && Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Adventurepath.UNIVERSE), 2000);
            if (!closed) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.Adventurepath.UNIVERSE), 1200);
            }
        }
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        }
    }

    private boolean needed() {
        return FocusPlan.needed(task, Microbot.getVarbitValue(VarbitID.ADVENTUREPATH_PATH_IN_FOCUS),
                Microbot.getVarbitValue(VarbitID.ADVENTUREPATH_COMBAT_TASK_FOCUS),
                Microbot.getVarbitValue(VarbitID.ADVENTUREPATH_GATHERER_TASK_FOCUS));
    }

    /** The 15 combat rows do not all fit in the task list. */
    private boolean scrollIntoView(int row) {
        for (int i = 0; i <= MAX_SCROLLS; i++) {
            Rectangle list = bounds(InterfaceID.Adventurepath.TASKS);
            Rectangle r = rowBounds(row);
            if (list == null || r == null || list.contains(r)) return true;
            if (i == MAX_SCROLLS) return false;
            Point at = new Point((int) list.getCenterX(), (int) list.getCenterY());
            if (r.y < list.y) {
                Microbot.getMouse().scrollUp(at);
            } else {
                Microbot.getMouse().scrollDown(at);
            }
            Global.sleep(150 + random.nextInt(150));
        }
        return false;
    }

    private boolean click(int component, String action) {
        Rectangle r = find(component, action::equals);
        if (r == null) return false;
        Global.sleep((int) pace.reactionMs(random));
        return clickRect(r);
    }

    private static boolean clickRect(Rectangle r) {
        if (r == null || r.width <= 0 || r.height <= 0) return false;
        Microbot.getMouse().click(r);
        return true;
    }

    private static Rectangle find(int component, Predicate<String> action) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(component);
            if (w == null || w.isHidden()) return null;
            if (hasAction(w, action)) return w.getBounds();
            Widget[] kids = w.getDynamicChildren();
            if (kids == null) return null;
            for (Widget k : kids) {
                if (k != null && !k.isHidden() && hasAction(k, action)) return k.getBounds();
            }
            return null;
        }).orElse(null);
    }

    private static boolean hasAction(Widget w, Predicate<String> action) {
        String[] ops = w.getActions();
        if (ops == null) return false;
        for (String op : ops) {
            if (op != null && action.test(Text.removeTags(op))) return true;
        }
        return false;
    }

    private static int rowCount() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(InterfaceID.Adventurepath.TASKS);
            Widget[] kids = w == null || w.isHidden() ? null : w.getDynamicChildren();
            if (kids == null) return 0;
            int n = 0;
            for (Widget k : kids) {
                if (k != null && !k.isHidden() && hasAction(k, op -> op.startsWith(SHOW_TASK))) n++;
            }
            return n;
        }).orElse(0);
    }

    private static Widget row(int index) {
        Widget w = Microbot.getClient().getWidget(InterfaceID.Adventurepath.TASKS);
        Widget k = w == null || w.isHidden() ? null : w.getChild(index);
        return k == null || k.isHidden() ? null : k;
    }

    private static String rowAction(int index) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget k = row(index);
            String[] ops = k == null ? null : k.getActions();
            if (ops == null) return "";
            for (String op : ops) {
                if (op != null && Text.removeTags(op).startsWith(SHOW_TASK)) return op;
            }
            return "";
        }).orElse("");
    }

    private static Rectangle rowBounds(int index) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget k = row(index);
            return k == null ? null : k.getBounds();
        }).orElse(null);
    }

    private static Rectangle bounds(int component) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(component);
            return w == null || w.isHidden() ? null : w.getBounds();
        }).orElse(null);
    }

    private static String taskName() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(InterfaceID.Adventurepath.TASK_NAME);
            return w == null ? null : Text.removeTags(w.getText());
        }).orElse("");
    }
}
