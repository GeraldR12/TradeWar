package com.nextinngames.tradeWar.bluemap;

import java.util.Locale;

/**
 * Pure text/HTML formatting for BlueMap markers. Kept free of BlueMap types so the
 * output can be asserted in unit tests without a running server.
 */
public final class TradeMarkerFormatter {

    private TradeMarkerFormatter() {
    }

    public static String markerId(String townName) {
        return "town-" + townName.toLowerCase(Locale.ROOT);
    }

    public static String label(TradeStatus status) {
        String icon = switch (status.severity()) {
            case EMBARGO -> "⛔"; // no-entry
            case SANCTION -> "🔴"; // red circle
            case TARIFF -> "🟠"; // orange circle
            case NONE -> "";
        };
        return (icon.isEmpty() ? "" : icon + " ") + status.townName();
    }

    public static String styleClass(TradeStatus.Severity severity) {
        return switch (severity) {
            case EMBARGO -> "tradewar-embargo";
            case SANCTION -> "tradewar-sanction";
            case TARIFF -> "tradewar-tariff";
            case NONE -> "tradewar-none";
        };
    }

    public static String detailHtml(TradeStatus status, long now) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"tradewar-popup\"><b>").append(escape(status.townName())).append("</b><br/>");
        sb.append("<i>Restrictions: ").append(status.restrictions().size()).append("</i><br/><br/>");
        for (TradeRestriction r : status.restrictions()) {
            sb.append(section(r, now));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String section(TradeRestriction r, long now) {
        return switch (r.type()) {
            case EMBARGO -> "⛔ <b>NATION EMBARGO</b><br/>Applied by: " + escape(r.appliedBy()) + "<br/><br/>";
            case SANCTION -> "🔴 <b>SANCTION</b><br/>Applied by: " + escape(r.appliedBy()) + "<br/><br/>";
            case TARIFF -> "🟠 <b>" + r.direction().toUpperCase(Locale.ROOT) + " TARIFF</b><br/>"
                    + "Applied by: " + escape(r.appliedBy()) + "<br/>"
                    + formatPercentage(r.percentage()) + "%<br/>"
                    + escape(r.item()) + "<br/>"
                    + "Expires: " + expiryLabel(r.expiresAt(), now) + "<br/><br/>";
        };
    }

    private static String expiryLabel(long expiresAt, long now) {
        if (expiresAt <= 0) {
            return "Permanent";
        }
        long remaining = expiresAt - now;
        if (remaining <= 0) {
            return "Expired";
        }
        long totalMinutes = (remaining + 59_999L) / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private static String formatPercentage(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
