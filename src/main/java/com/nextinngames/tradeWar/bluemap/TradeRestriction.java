package com.nextinngames.tradeWar.bluemap;

/**
 * A single trade restriction affecting a town, already resolved to a display-ready form.
 * Pure data, no Bukkit/BlueMap/Towny types, so it stays unit-testable.
 */
public record TradeRestriction(
        Type type,
        String appliedBy,
        String direction,   // "import"/"export", tariff only, else null
        String item,        // material name or "ALL", tariff only, else null
        double percentage,  // tariff only, else 0
        long expiresAt      // epoch millis, 0 = permanent/not applicable
) {
    public enum Type { TARIFF, SANCTION, EMBARGO }

    public static TradeRestriction tariff(String appliedBy, String direction, String item, double percentage, long expiresAt) {
        return new TradeRestriction(Type.TARIFF, appliedBy, direction, item, percentage, expiresAt);
    }

    public static TradeRestriction sanction(String appliedBy) {
        return new TradeRestriction(Type.SANCTION, appliedBy, null, null, 0, 0);
    }

    public static TradeRestriction embargo(String appliedBy) {
        return new TradeRestriction(Type.EMBARGO, appliedBy, null, null, 0, 0);
    }
}
