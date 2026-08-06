package net.runelite.client.plugins.microbot.microbotdashboardplus.notify;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses + tracks per-skill level alert thresholds and detects crossings.
 *
 * <p>Threshold input format: {@code "MINING:60, WOODCUTTING:80, FISHING:70"}.
 * Skill names follow the OSRS API {@link Skill} enum (case-insensitive on
 * parse). Levels are integers in [1, 99]. Invalid entries are skipped with a
 * debug log.
 *
 * <p>State: a set of {@code (Skill, level)} pairs already fired. Crossings
 * fire exactly once per (skill, level) pair across the session.
 */
@Slf4j
public class AlertManager {

    private Map<Skill, Integer> thresholds = new EnumMap<>(Skill.class);
    private final Set<String> fired = new HashSet<>();

    public synchronized void setThresholdsFromConfig(String csv) {
        // Re-arm the once-per-session dedupe so adjusted or re-added thresholds
        // can fire again after the user edits config.
        resetFired();
        Map<Skill, Integer> next = new EnumMap<>(Skill.class);
        if (csv == null || csv.trim().isEmpty()) {
            this.thresholds = next;
            return;
        }
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            int colon = t.indexOf(':');
            if (colon <= 0 || colon >= t.length() - 1) {
                log.debug("AlertManager: skipping malformed token '{}'", t);
                continue;
            }
            String skillStr = t.substring(0, colon).trim().toUpperCase();
            String levelStr = t.substring(colon + 1).trim();
            try {
                Skill s = Skill.valueOf(skillStr);
                int level = Integer.parseInt(levelStr);
                if (level >= 1 && level <= 99) next.put(s, level);
                else log.debug("AlertManager: level out of range '{}'", level);
            } catch (Throwable ex) {
                log.debug("AlertManager: invalid token '{}' ({})", t, ex.getClass().getSimpleName());
            }
        }
        this.thresholds = next;
    }

    /**
     * Returns true if {@code level} just crossed (i.e. {@code >=}) the
     * configured threshold for {@code skill} and we haven't fired the alert
     * for that pair yet. Marks the pair as fired.
     */
    public synchronized boolean checkCrossing(Skill skill, int level) {
        Integer threshold = thresholds.get(skill);
        if (threshold == null) return false;
        if (level < threshold) return false;
        String key = skill.name() + ":" + threshold;
        if (fired.contains(key)) return false;
        fired.add(key);
        return true;
    }

    /** Threshold for a given skill, or null if not configured. */
    public synchronized Integer thresholdFor(Skill skill) {
        return thresholds.get(skill);
    }

    /** Read-only snapshot of configured thresholds. */
    public synchronized Map<Skill, Integer> getThresholds() {
        return Collections.unmodifiableMap(new EnumMap<>(thresholds));
    }

    /** Reset fire-state. Used when user changes config or resets the session. */
    public synchronized void resetFired() {
        fired.clear();
    }
}
