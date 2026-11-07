package net.runelite.client.plugins.microbot.geflipper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure policy and parsing helpers for GE Flipper LAN muling. */
final class MulePolicy {
    static final int MIN_TRANSFER_GP = 1_000;
    static final int DEFAULT_PORT = 43599;
    static final int GE_CENTER_X = 3164;
    static final int GE_CENTER_Y = 3487;
    static final int GE_CENTER_PLANE = 0;

    private static final Pattern GP_PATTERN = Pattern.compile(
        "^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([kmb]?)\\s*(?:gp)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern THOUSANDS_PATTERN = Pattern.compile(
        "^[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?[kmb]?(?:gp)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TILE_PATTERN = Pattern.compile(
        "^\\s*([0-9]{4,5})\\s*[,\\s]+\\s*([0-9]{4,5})(?:\\s*[,\\s]+\\s*([0-3]))?\\s*$");

    private MulePolicy() { }

    static final class TileSpec {
        final boolean auto;
        final boolean grandExchange;
        final int x;
        final int y;
        final int plane;

        TileSpec(boolean auto, boolean grandExchange, int x, int y, int plane) {
            this.auto = auto;
            this.grandExchange = grandExchange;
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        static TileSpec auto() {
            return new TileSpec(true, false, 0, 0, 0);
        }

        static TileSpec grandExchange() {
            return new TileSpec(false, true, GE_CENTER_X, GE_CENTER_Y, GE_CENTER_PLANE);
        }

        static TileSpec fixed(int x, int y, int plane) {
            return new TileSpec(false, false, x, y, Math.max(0, Math.min(3, plane)));
        }
    }

    static final class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host == null ? "auto" : host;
            this.port = clampPort(port);
        }
    }

    /**
     * Parses human-friendly GP strings such as "50M", "2.5m", "500k", "50,000,000", or "50000000".
     * Returns -1 when invalid or outside [0, Integer.MAX_VALUE].
     */
    static int parseGp(String raw) {
        if (raw == null) return -1;
        String cleaned = raw.replace("_", "")
            .replace("\u00a0", "").replace(" ", "").trim();
        // Commas count only as thousands separators: "1,500,000" is fine, but "1,5M" is a typo, not 15M.
        if (cleaned.indexOf(',') >= 0) {
            if (!THOUSANDS_PATTERN.matcher(cleaned).matches()) return -1;
            cleaned = cleaned.replace(",", "");
        }
        if (cleaned.isEmpty()) return -1;
        Matcher matcher = GP_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return -1;
        try {
            BigDecimal number = new BigDecimal(matcher.group(1));
            if (number.signum() < 0) return -1;
            String suffix = matcher.group(2).toLowerCase(Locale.ROOT);
            BigDecimal multiplier = BigDecimal.ONE;
            if ("k".equals(suffix)) multiplier = BigDecimal.valueOf(1_000L);
            else if ("m".equals(suffix)) multiplier = BigDecimal.valueOf(1_000_000L);
            else if ("b".equals(suffix)) multiplier = BigDecimal.valueOf(1_000_000_000L);
            BigDecimal total = number.multiply(multiplier);
            if (total.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return -1;
            }
            return total.setScale(0, RoundingMode.DOWN).intValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            return -1;
        }
    }

    /**
     * The "Mule when over" amount. Blank or 0 means "as soon as there is excess above the keep amount", which is how
     * muling always worked. A typo returns -1, so it stops automatic muling instead of muling more often.
     */
    static int parseTrigger(String raw, int keepGp) {
        if (raw == null || raw.trim().isEmpty()) return keepGp;
        int trigger = parseGp(raw);
        if (trigger < 0) return -1;
        return trigger == 0 ? keepGp : trigger;
    }

    /**
     * Calculates how much loose GP should be transferred to the mule.
     * Only triggers when looseCoins >= thresholdGp and (looseCoins - reserveGp) >= MIN_TRANSFER_GP.
     * Equal threshold and reserve implement a cash floor: keep that amount for flipping and send only its excess.
     */
    static int calculateTransferAmount(long looseCoins, int thresholdGp, int reserveGp) {
        if (thresholdGp <= 0 || reserveGp < 0 || thresholdGp < reserveGp) return 0;
        if (looseCoins < thresholdGp) return 0;
        long excess = looseCoins - reserveGp;
        if (excess < MIN_TRANSFER_GP) return 0;
        return (int) Math.min(Integer.MAX_VALUE, excess);
    }

    /** Keeps the selected total working capital and transfers only its liquid, verified excess. */
    static int calculatePortfolioTransferAmount(long totalCapitalGp, long looseCoins, int capitalFloorGp) {
        if (looseCoins < MIN_TRANSFER_GP) return 0;
        long amount = Math.min(requiredPortfolioTransferAmount(totalCapitalGp, capitalFloorGp), looseCoins);
        return amount < MIN_TRANSFER_GP ? 0 : (int) Math.min(Integer.MAX_VALUE, amount);
    }

    static int requiredPortfolioTransferAmount(long totalCapitalGp, int capitalFloorGp) {
        if (capitalFloorGp <= 0 || totalCapitalGp < capitalFloorGp) return 0;
        long excess = totalCapitalGp - capitalFloorGp;
        return excess < MIN_TRANSFER_GP ? 0 : (int) Math.min(Integer.MAX_VALUE, excess);
    }

    /**
     * How far the estimate may dip under the keep amount while a locked transfer runs. Guide prices refresh and GE
     * fills pay tax mid-transfer; without this room, a few hundred GP of drift cancelled the trade and hopped home.
     */
    static long transferTolerance(int keepGp) {
        return Math.max(MIN_TRANSFER_GP, Math.max(0, keepGp) / 100L);
    }

    /** Whether above-limit capital is still invested instead of available to transfer as GP. */
    static boolean needsPortfolioLiquidity(long totalCapitalGp, long looseCoins, int capitalFloorGp) {
        if (looseCoins < 0) return false;
        int required = requiredPortfolioTransferAmount(totalCapitalGp, capitalFloorGp);
        return required > 0 && looseCoins < required;
    }

    /**
     * Returns true when total portfolio wealth (loose cash + GE offers + tradeable inventory items)
     * has reached the mule threshold, and we still need to cancel buy offers or sell items into cash.
     */
    static boolean shouldLiquidateForMule(long totalWealthGp, long looseCoins, boolean hasActiveBuys,
                                          int thresholdGp, int reserveGp) {
        if (thresholdGp == reserveGp) return needsPortfolioLiquidity(totalWealthGp, looseCoins, reserveGp);
        if (thresholdGp <= 0 || reserveGp < 0 || thresholdGp <= reserveGp) return false;
        if ((thresholdGp - reserveGp) < MIN_TRANSFER_GP) return false;
        if (totalWealthGp < thresholdGp) return false;
        return looseCoins < thresholdGp || hasActiveBuys;
    }

    /**
     * Parses a meeting location setting:
     * - "" or "Auto" -> TileSpec.auto() (remember player's tile)
     * - "GE" or "Grand Exchange" -> TileSpec.grandExchange() (3164, 3487, 0)
     * - "3164,3487" or "3164,3487,0" -> TileSpec.fixed(x, y, plane)
     */
    static TileSpec parseLocation(String raw) {
        if (raw == null) return TileSpec.auto();
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "auto".equalsIgnoreCase(trimmed)) {
            return TileSpec.auto();
        }
        if ("ge".equalsIgnoreCase(trimmed) || "grand exchange".equalsIgnoreCase(trimmed)
            || "grandexchange".equalsIgnoreCase(trimmed)) {
            return TileSpec.grandExchange();
        }
        Matcher matcher = TILE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            try {
                int x = Integer.parseInt(matcher.group(1));
                int y = Integer.parseInt(matcher.group(2));
                int plane = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                return TileSpec.fixed(x, y, plane);
            } catch (NumberFormatException ignored) {
                return TileSpec.auto();
            }
        }
        return TileSpec.auto();
    }

    /** Parses optional host:port notation from configuredHost, falling back to configuredPort. */
    static HostPort parseHostAndPort(String configuredHost, int configuredPort) {
        int fallbackPort = clampPort(configuredPort);
        if (configuredHost == null) return new HostPort("auto", fallbackPort);
        String trimmed = configuredHost.trim();
        if (trimmed.isEmpty() || "auto".equalsIgnoreCase(trimmed)) {
            return new HostPort("auto", fallbackPort);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String hostPart = trimmed.substring(0, colon).trim();
            String portPart = trimmed.substring(colon + 1).trim();
            try {
                int parsedPort = Integer.parseInt(portPart);
                if (parsedPort >= 1024 && parsedPort <= 65535) {
                    return new HostPort(hostPart.isEmpty() ? "auto" : hostPart, parsedPort);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new HostPort(trimmed, fallbackPort);
    }

    /** Accepts the server-provided request name, or its standard trade-request message when the name is empty. */
    static String tradeRequestName(String sender, String message) {
        String name = normalizeRsn(sender);
        if (name.isEmpty()) {
            String text = normalizeRsn(message);
            String suffix = " wishes to trade with you.";
            if (!text.endsWith(suffix)) return "";
            name = text.substring(0, text.length() - suffix.length()).trim();
        }
        // OSRS display names may contain hyphens (e.g. "Bank-Mule").
        return name.length() <= 12 && name.matches("[a-z0-9 -]+") ? name : "";
    }

    /** Normalizes OSRS display names (tags, non-breaking spaces, casing). */
    static String normalizeRsn(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("<[^>]*>", "")
            .replaceAll("(?i)&nbsp;|&#160;|&#x0*a0;", " ")
            .replace('\u00a0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202f', ' ')
            .replace('_', ' ')
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    static boolean sameRsn(String first, String second) {
        String a = normalizeRsn(first);
        String b = normalizeRsn(second);
        return !a.isEmpty() && a.equals(b);
    }

    /** Extracts the partner RSN from Trademain.TITLE ("Trading With: Name") or Tradeconfirm.TRADEOPPONENT. */
    static String extractTradePartner(String widgetText) {
        if (widgetText == null) return "";
        String plain = widgetText.replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("<[^>]*>", "")
            .replaceAll("(?i)&nbsp;|&#160;|&#x0*a0;", " ")
            .replace('\u00a0', ' ')
            .trim()
            .replaceAll("\\s+", " ");
        String lower = plain.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("trading with:");
        if (idx >= 0) {
            plain = plain.substring(idx + "trading with:".length()).trim();
        } else if (lower.startsWith("trading with ")) {
            plain = plain.substring("trading with ".length()).trim();
        }
        return normalizeRsn(plain);
    }

    static int clampPort(int port) {
        return port >= 1024 && port <= 65535 ? port : DEFAULT_PORT;
    }
}
