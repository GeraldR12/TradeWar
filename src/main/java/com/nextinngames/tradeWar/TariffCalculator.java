package com.nextinngames.tradeWar;

import org.bukkit.Material;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public final class TariffCalculator {
    private TariffCalculator() {}

    private static final MathContext CALC_CONTEXT = MathContext.DECIMAL64;
    private static final int CURRENCY_SCALE = 2;

    public static double calculateTariff(List<TradeDataManager.TariffRule> rules, String residentTownName,
                                          Material shopItem, boolean isImport, double baseTotal, long now) {
        if (rules == null || rules.isEmpty() || !Double.isFinite(baseTotal) || baseTotal <= 0.0) {
            return 0.0;
        }

        BigDecimal combinedRate = BigDecimal.ZERO;
        for (TradeDataManager.TariffRule rule : rules) {
            if (rule.expiryTime() > 0 && now > rule.expiryTime()) continue;
            if (rule.item() != null && rule.item() != shopItem) continue;
            if (rule.type().equalsIgnoreCase("import") && !isImport) continue;
            if (rule.type().equalsIgnoreCase("export") && isImport) continue;
            if (!residentTownName.equalsIgnoreCase(rule.targetName())) continue;

            combinedRate = combinedRate.add(BigDecimal.valueOf(rule.percentage()).divide(BigDecimal.valueOf(100), CALC_CONTEXT));
        }

        if (combinedRate.signum() <= 0) return 0.0;

        BigDecimal total = BigDecimal.valueOf(baseTotal);
        BigDecimal tariff = isImport
                ? total.multiply(combinedRate, CALC_CONTEXT)
                : total.multiply(combinedRate, CALC_CONTEXT).divide(BigDecimal.ONE.add(combinedRate), CALC_CONTEXT);

        return roundToCurrency(tariff);
    }

    public static boolean canAffordImport(double holdingBalance, double baseTotal, double quickShopInteractorRate, double tariffAmount) {
        if (!Double.isFinite(holdingBalance) || !Double.isFinite(baseTotal) || !Double.isFinite(quickShopInteractorRate) || !Double.isFinite(tariffAmount)) {
            return false;
        }
        return holdingBalance >= baseTotal + quickShopSurcharge(baseTotal, quickShopInteractorRate) + tariffAmount;
    }

    public static double quickShopSurcharge(double baseTotal, double quickShopInteractorRate) {
        return baseTotal * quickShopInteractorRate;
    }

    public static double applyQuickShopSellerDeduction(double baseTotal, double quickShopInteractorRate) {
        if (!Double.isFinite(baseTotal) || baseTotal <= 0.0 || !Double.isFinite(quickShopInteractorRate)) {
            return 0.0;
        }
        return baseTotal * (1.0 - quickShopInteractorRate);
    }

    private static double roundToCurrency(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return 0.0;
        return amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
