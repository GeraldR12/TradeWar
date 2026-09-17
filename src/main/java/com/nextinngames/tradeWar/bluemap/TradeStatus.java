package com.nextinngames.tradeWar.bluemap;

import java.util.List;

/**
 * Aggregate of every restriction currently affecting a single town.
 * Only towns with at least one restriction should ever have a TradeStatus built.
 */
public record TradeStatus(String townName, List<TradeRestriction> restrictions) {

    public enum Severity { NONE, TARIFF, SANCTION, EMBARGO }

    public Severity severity() {
        Severity max = Severity.NONE;
        for (TradeRestriction r : restrictions) {
            Severity s = switch (r.type()) {
                case EMBARGO -> Severity.EMBARGO;
                case SANCTION -> Severity.SANCTION;
                case TARIFF -> Severity.TARIFF;
            };
            if (s.ordinal() > max.ordinal()) {
                max = s;
            }
        }
        return max;
    }
}
