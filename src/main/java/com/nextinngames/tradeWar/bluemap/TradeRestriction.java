package com.nextinngames.tradeWar.bluemap;

public record TradeRestriction(
        Type type,
        String appliedBy,
        double percentage,
        long expiresAt
) {
    public enum Type { TARIFF, SANCTION, EMBARGO }

    public static TradeRestriction tariff(String appliedBy, double percentage, long expiresAt) {
        return new TradeRestriction(Type.TARIFF, appliedBy, percentage, expiresAt);
    }

    public static TradeRestriction sanction(String appliedBy) {
        return new TradeRestriction(Type.SANCTION, appliedBy, 0, 0);
    }

    public static TradeRestriction embargo(String appliedBy) {
        return new TradeRestriction(Type.EMBARGO, appliedBy, 0, 0);
    }
}
