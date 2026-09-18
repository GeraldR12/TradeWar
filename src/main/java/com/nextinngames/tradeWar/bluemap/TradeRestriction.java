package com.nextinngames.tradeWar.bluemap;

public record TradeRestriction(
        Type type,
        String appliedBy,
        String direction,
        String item,
        double percentage,
        long expiresAt
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
