package com.nextinngames.tradeWar;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TariffCalculatorTest {

    private static TradeDataManager.TariffRule rule(String type, String target, Material item, double percentage, long expiry) {
        return new TradeDataManager.TariffRule(type, "town", target, item, percentage, expiry);
    }

    @Test
    void noTariffRulesReturnsZero() {
        assertEquals(0.0, TariffCalculator.calculateTariff(List.of(), "Riverside", Material.DIAMOND, false, 2.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(null, "Riverside", Material.DIAMOND, false, 2.0, 0L));
    }

    @Test
    void exportOwnerReportedExample() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 100.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 2.0, 0L);
        assertEquals(1.0, tariff, "100% export tariff on a 2g trade must be exactly 1g, seller keeps the other 1g");
    }

    @Test
    void exportNormalTariffDeductsRatioShare() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 25.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 10.0, 0L);
        assertEquals(2.0, tariff, "town's share = 10 * 0.25 / 1.25 = 2.0, resident nets 8.0");
    }

    @Test
    void importTariffIsAdditionalCharge() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("import", "Riverside", null, 50.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, true, 4.0, 0L);
        assertEquals(2.0, tariff);
    }

    @Test
    void wrongDirectionRuleDoesNotApply() {
        List<TradeDataManager.TariffRule> importOnly = List.of(rule("import", "Riverside", null, 50.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(importOnly, "Riverside", Material.DIAMOND, false, 4.0, 0L));

        List<TradeDataManager.TariffRule> exportOnly = List.of(rule("export", "Riverside", null, 50.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(exportOnly, "Riverside", Material.DIAMOND, true, 4.0, 0L));
    }

    @Test
    void differentTargetTownDoesNotApply() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "OtherTown", null, 50.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 4.0, 0L));
    }

    @Test
    void specificItemRuleOnlyMatchesThatItem() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", Material.GOLD_INGOT, 50.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 4.0, 0L));
        assertEquals(1.33, TariffCalculator.calculateTariff(rules, "Riverside", Material.GOLD_INGOT, false, 4.0, 0L));
    }

    @Test
    void expiredRuleDoesNotApply() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 50.0, 1000L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 4.0, 5000L));
        assertEquals(1.33, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 4.0, 500L));
    }

    @Test
    void permanentRuleHasZeroExpiry() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 50.0, 0L));
        assertEquals(1.33, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 4.0, Long.MAX_VALUE));
    }

    @Test
    void multipleMatchingRulesStackTheirRatesBeforeSplitting() {
        List<TradeDataManager.TariffRule> rules = List.of(
                rule("export", "Riverside", null, 10.0, 0L),
                rule("export", "Riverside", null, 20.0, 0L)
        );
        assertEquals(2.31, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 10.0, 0L));
    }

    @Test
    void roundingKeepsTwoDecimalCurrencyPrecision() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 33.333, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 1.0, 0L);
        assertEquals(0.25, tariff);
    }

    @Test
    void tinyTariffRoundsToZeroAndIsTreatedAsNoTariff() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 0.001, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 1.0, 0L);
        assertEquals(0.0, tariff);
    }

    @Test
    void nonFiniteOrNonPositiveBaseTotalReturnsZero() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 50.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 0.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, -5.0, 0L));
        assertEquals(0.0, TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, Double.NaN, 0L));
    }

    @Test
    void importAboveHundredPercentTariffExceedsBaseTotal() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("import", "Riverside", null, 150.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, true, 2.0, 0L);
        assertEquals(3.0, tariff, "import tariff is a plain surcharge, so it can exceed the base price");
    }

    @Test
    void exportTariffNeverExceedsBaseTotalEvenAtExtremePercentage() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 100_000.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, 2.0, 0L);
        assertTrue(tariff <= 2.0, "export tariff can round up to the full total but must never exceed it (no money creation, seller net never negative)");
    }

    @Test
    void targetTownMatchIsCaseInsensitive() {
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "riverside", null, 50.0, 0L));
        assertEquals(1.33, TariffCalculator.calculateTariff(rules, "RIVERSIDE", Material.DIAMOND, false, 4.0, 0L));
    }

    @Test
    void exportExamplesOnCanonical2GTransaction() {
        assertEquals(0.18, exportTariffOn2G(10.0), "10% -> resident nets 1.82");
        assertEquals(0.40, exportTariffOn2G(25.0), "25% -> resident nets 1.60");
        assertEquals(0.67, exportTariffOn2G(50.0), "50% -> resident nets 1.33");
        assertEquals(1.00, exportTariffOn2G(100.0), "100% -> resident nets 1.00 (owner's canonical example)");
    }

    private static double exportTariffOn2G(double percentage) {
        return TariffCalculator.calculateTariff(List.of(rule("export", "Riverside", null, percentage, 0L)), "Riverside", Material.DIAMOND, false, 2.0, 0L);
    }

    @Test
    void exportAccountingConservesTotalAtEveryPercentage() {
        for (double pct : new double[]{10.0, 25.0, 50.0, 100.0}) {
            double tariff = exportTariffOn2G(pct);
            double residentNet = 2.0 - tariff;
            assertEquals(2.0, round2(residentNet + tariff), "gross must equal residentNet + tariff at " + pct + "%");
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Test
    void canAffordImportRequiresBasePlusTariffTogetherWithNoQuickShopTax() {
        assertTrue(TariffCalculator.canAffordImport(150.0, 100.0, 0.0, 50.0), "exactly base + tariff must be affordable");
        assertTrue(TariffCalculator.canAffordImport(200.0, 100.0, 0.0, 50.0), "more than enough must be affordable");
        assertFalse(TariffCalculator.canAffordImport(100.0, 100.0, 0.0, 50.0), "exactly base only must NOT be affordable - this is the reported bypass");
        assertFalse(TariffCalculator.canAffordImport(50.0, 100.0, 0.0, 50.0), "less than base must not be affordable");
        assertFalse(TariffCalculator.canAffordImport(149.99, 100.0, 0.0, 50.0), "one cent short must not be affordable");
    }

    @Test
    void canAffordImportAccountsForQuickShopsOwnActiveTax() {
        assertFalse(TariffCalculator.canAffordImport(160.0, 100.0, 0.20, 50.0), "100 + 20 (quickshop) + 50 (tariff) = 170 > 160");
        assertTrue(TariffCalculator.canAffordImport(170.0, 100.0, 0.20, 50.0), "exactly enough for all three debits");
        assertTrue(TariffCalculator.canAffordImport(171.0, 100.0, 0.20, 50.0));
    }

    @Test
    void canAffordImportRejectsNonFiniteBalance() {
        assertFalse(TariffCalculator.canAffordImport(Double.NaN, 100.0, 0.0, 50.0));
        assertFalse(TariffCalculator.canAffordImport(Double.POSITIVE_INFINITY, 100.0, 0.0, 50.0));
    }

    @Test
    void canAffordImportFailsClosedWhenQuickShopRateIsUndetermined() {
        assertFalse(TariffCalculator.canAffordImport(1_000_000.0, 100.0, Double.NaN, 50.0));
    }

    @Test
    void canAffordImportWithZeroTariffOnlyNeedsBase() {
        assertTrue(TariffCalculator.canAffordImport(100.0, 100.0, 0.0, 0.0));
        assertFalse(TariffCalculator.canAffordImport(99.99, 100.0, 0.0, 0.0));
    }

    @Test
    void applyQuickShopSellerDeductionReducesTheExportPot() {
        assertEquals(2.0, TariffCalculator.applyQuickShopSellerDeduction(2.0, 0.0), "no quickshop tax -> full total is the pot");
        assertEquals(1.6, TariffCalculator.applyQuickShopSellerDeduction(2.0, 0.20), "20% quickshop tax -> resident only nets 1.6");
        assertEquals(0.0, TariffCalculator.applyQuickShopSellerDeduction(2.0, 1.0), "100% quickshop tax -> resident nets nothing, no pot to tax");
    }

    @Test
    void applyQuickShopSellerDeductionSkipsCollectionWhenRateIsUndetermined() {
        assertEquals(0.0, TariffCalculator.applyQuickShopSellerDeduction(2.0, Double.NaN));
    }

    @Test
    void exportTariffOnReducedPotNeverExceedsWhatResidentActuallyReceived() {
        double actualCredit = TariffCalculator.applyQuickShopSellerDeduction(2.0, 0.20);
        List<TradeDataManager.TariffRule> rules = List.of(rule("export", "Riverside", null, 100.0, 0L));
        double tariff = TariffCalculator.calculateTariff(rules, "Riverside", Material.DIAMOND, false, actualCredit, 0L);
        assertTrue(tariff <= actualCredit, "tariff must never exceed what the resident actually received this trade");
        assertEquals(0.8, tariff, "100% tariff on the reduced 1.6 pot -> 0.8/0.8 split");
    }
}
