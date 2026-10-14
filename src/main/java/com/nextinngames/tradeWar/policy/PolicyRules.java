package com.nextinngames.tradeWar.policy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure (Bukkit-free) trade policy rules, so they can be unit tested.
 */
public final class PolicyRules {

    public static final String TOWN_PREFIX = "town:";
    public static final String PLAYER_PREFIX = "player:";
    public static final String NATION_PREFIX = "nation:";

    private static final long MILLIS_PER_SECOND = 1000L;

    private PolicyRules() {
    }

    /**
     * Sum of every active tariff in {@code tariffs} that targets the buyer's town or nation.
     */
    public static double applicablePercentage(List<Tariff> tariffs, String buyerTown, String buyerNation, long now) {
        if (tariffs == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Tariff tariff : tariffs) {
            if (tariff.isExpired(now)) {
                continue;
            }
            boolean applies = switch (tariff.targetType()) {
                case Tariff.NATION -> buyerNation != null && buyerNation.equalsIgnoreCase(tariff.targetName());
                case Tariff.TOWN -> buyerTown != null && buyerTown.equalsIgnoreCase(tariff.targetName());
                default -> false;
            };
            if (applies) {
                total += tariff.percentage();
            }
        }
        return total;
    }

    /**
     * Whether a sanction list covers the given player or their town.
     */
    public static boolean isSanctioned(Set<String> sanctions, String townName, UUID playerId) {
        if (sanctions == null || sanctions.isEmpty()) {
            return false;
        }
        if (playerId != null && containsIgnoreCase(sanctions, playerKey(playerId))) {
            return true;
        }
        return townName != null && containsIgnoreCase(sanctions, townKey(townName));
    }

    /**
     * Whether an embargo list covers the given nation.
     */
    public static boolean isEmbargoed(Set<String> embargoes, String nationName) {
        return embargoes != null && nationName != null && containsIgnoreCase(embargoes, nationName);
    }

    private static boolean containsIgnoreCase(Set<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    public static String townKey(String townName) {
        return TOWN_PREFIX + townName;
    }

    public static String playerKey(UUID playerId) {
        return PLAYER_PREFIX + playerId;
    }

    /**
     * Parses a duration like {@code 90}, {@code 45s}, {@code 30m}, {@code 2h} or {@code 1d} into milliseconds.
     * A bare number is minutes.
     *
     * @throws IllegalArgumentException if the input is not a positive duration
     */
    public static long parseDurationMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Empty duration");
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        char unit = value.charAt(value.length() - 1);
        long secondsPerUnit;
        String number = value.substring(0, value.length() - 1);
        switch (unit) {
            case 's' -> secondsPerUnit = 1L;
            case 'm' -> secondsPerUnit = 60L;
            case 'h' -> secondsPerUnit = 3_600L;
            case 'd' -> secondsPerUnit = 86_400L;
            default -> {
                secondsPerUnit = 60L;
                number = value;
            }
        }
        long amount = Long.parseLong(number);
        if (amount <= 0L) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        return Math.multiplyExact(Math.multiplyExact(amount, secondsPerUnit), MILLIS_PER_SECOND);
    }

    /**
     * Formats a remaining duration as e.g. {@code 1d 2h 5m}; anything under a minute shows as {@code <1m}.
     */
    public static String formatRemaining(long expiryTime, long now) {
        if (expiryTime <= 0) {
            return "Permanent";
        }
        long remaining = expiryTime - now;
        if (remaining <= 0) {
            return "Expired";
        }
        long totalMinutes = remaining / 60_000L;
        if (totalMinutes == 0) {
            return "<1m";
        }
        long days = totalMinutes / 1_440L;
        long hours = (totalMinutes % 1_440L) / 60L;
        long minutes = totalMinutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
        }
        return sb.toString().trim();
    }

    public static String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
