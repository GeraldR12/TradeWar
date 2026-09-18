package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.TradeDataManager;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStatusAggregatorTest {

    private static final long NOW = 1_000_000_000L;

    private final TradeStatusAggregator aggregator = new TradeStatusAggregator();

    private Map<String, TradeStatus> aggregate(
            Set<String> towns,
            Set<String> nations,
            Map<String, List<String>> townsByNation,
            Map<String, List<TradeDataManager.TariffRule>> tariffs,
            Map<String, Set<String>> sanctions,
            Map<String, Set<String>> embargoes,
            List<String> warnings
    ) {
        return aggregator.aggregate(towns, nations, townsByNation, tariffs, sanctions, embargoes, NOW, warnings::add);
    }

    private static TradeDataManager.TariffRule tariff(String type, String target, Material item, double percentage, long expiry) {
        return new TradeDataManager.TariffRule(type, "town", target, item, percentage, expiry);
    }

    @Test
    void townWithNoRestrictionsHasNoStatus() {
        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana"), Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void singleActiveTariffIsReported() {
        Map<String, List<TradeDataManager.TariffRule>> tariffs = Map.of(
                "NewYork", List.of(tariff("import", "Havana", Material.IRON_INGOT, 15.0, NOW + 60_000)));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "NewYork"), Set.of(), Map.of(), tariffs, Map.of(), Map.of(), new ArrayList<>());

        TradeStatus status = result.get("Havana");
        assertEquals(1, status.restrictions().size());
        TradeRestriction r = status.restrictions().get(0);
        assertEquals(TradeRestriction.Type.TARIFF, r.type());
        assertEquals("NewYork", r.appliedBy());
        assertEquals("import", r.direction());
        assertEquals("IRON_INGOT", r.item());
        assertEquals(15.0, r.percentage());
        assertEquals(TradeStatus.Severity.TARIFF, status.severity());
    }

    @Test
    void multipleTariffsFromDifferentTownsAreAllReported() {
        Map<String, List<TradeDataManager.TariffRule>> tariffs = new HashMap<>();
        tariffs.put("NewYork", List.of(tariff("import", "Havana", Material.IRON_INGOT, 15.0, 0L)));
        tariffs.put("Brasilia", List.of(tariff("export", "Havana", null, 5.0, 0L)));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "NewYork", "Brasilia"), Set.of(), Map.of(), tariffs, Map.of(), Map.of(), new ArrayList<>());

        assertEquals(2, result.get("Havana").restrictions().size());
    }

    @Test
    void expiredTariffIsIgnored() {
        Map<String, List<TradeDataManager.TariffRule>> tariffs = Map.of(
                "NewYork", List.of(tariff("import", "Havana", Material.IRON_INGOT, 15.0, NOW - 1)));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "NewYork"), Set.of(), Map.of(), tariffs, Map.of(), Map.of(), new ArrayList<>());

        assertTrue(result.isEmpty());
    }

    @Test
    void sanctionIsReported() {
        Map<String, Set<String>> sanctions = Map.of("Brasilia", Set.of("Havana"));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "Brasilia"), Set.of(), Map.of(), Map.of(), sanctions, Map.of(), new ArrayList<>());

        TradeStatus status = result.get("Havana");
        assertEquals(1, status.restrictions().size());
        assertEquals(TradeRestriction.Type.SANCTION, status.restrictions().get(0).type());
        assertEquals("Brasilia", status.restrictions().get(0).appliedBy());
        assertEquals(TradeStatus.Severity.SANCTION, status.severity());
    }

    @Test
    void nationEmbargoExpandsToMemberTowns() {
        Map<String, Set<String>> embargoes = Map.of("UnitedKingdom", Set.of("Cuba"));
        Map<String, List<String>> townsByNation = Map.of("Cuba", List.of("Havana", "Santiago"));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "Santiago"), Set.of("Cuba", "UnitedKingdom"), townsByNation,
                Map.of(), Map.of(), embargoes, new ArrayList<>());

        assertEquals(2, result.size());
        assertEquals(TradeRestriction.Type.EMBARGO, result.get("Havana").restrictions().get(0).type());
        assertEquals("UnitedKingdom", result.get("Havana").restrictions().get(0).appliedBy());
        assertEquals(TradeStatus.Severity.EMBARGO, result.get("Santiago").severity());
    }

    @Test
    void tariffPlusSanctionMergeOnSameTown() {
        Map<String, List<TradeDataManager.TariffRule>> tariffs = Map.of(
                "NewYork", List.of(tariff("import", "Havana", Material.IRON_INGOT, 15.0, 0L)));
        Map<String, Set<String>> sanctions = Map.of("Brasilia", Set.of("Havana"));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "NewYork", "Brasilia"), Set.of(), Map.of(), tariffs, sanctions, Map.of(), new ArrayList<>());

        TradeStatus status = result.get("Havana");
        assertEquals(2, status.restrictions().size());
        assertEquals(TradeStatus.Severity.SANCTION, status.severity());
    }

    @Test
    void tariffPlusSanctionPlusEmbargoMergeAndSeverityIsEmbargo() {
        Map<String, List<TradeDataManager.TariffRule>> tariffs = Map.of(
                "NewYork", List.of(tariff("import", "Havana", Material.IRON_INGOT, 15.0, 0L)));
        Map<String, Set<String>> sanctions = Map.of("Brasilia", Set.of("Havana"));
        Map<String, Set<String>> embargoes = Map.of("UnitedKingdom", Set.of("Cuba"));
        Map<String, List<String>> townsByNation = Map.of("Cuba", List.of("Havana"));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "NewYork", "Brasilia"), Set.of("Cuba", "UnitedKingdom"), townsByNation,
                tariffs, sanctions, embargoes, new ArrayList<>());

        TradeStatus status = result.get("Havana");
        assertEquals(3, status.restrictions().size());
        assertEquals(TradeStatus.Severity.EMBARGO, status.severity());
    }

    @Test
    void sanctionTargetingNonexistentTownIsSkippedAndLogged() {
        Map<String, Set<String>> sanctions = Map.of("Brasilia", Set.of("Atlantis"));
        List<String> warnings = new ArrayList<>();

        Map<String, TradeStatus> result = aggregate(
                Set.of("Brasilia"), Set.of(), Map.of(), Map.of(), sanctions, Map.of(), warnings);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void embargoTargetingNonexistentNationIsSkippedAndLogged() {
        Map<String, Set<String>> embargoes = Map.of("UnitedKingdom", Set.of("Atlantis"));
        List<String> warnings = new ArrayList<>();

        Map<String, TradeStatus> result = aggregate(
                Set.of(), Set.of("UnitedKingdom"), Map.of(), Map.of(), Map.of(), embargoes, warnings);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void resolutionIsCaseInsensitive() {
        Map<String, Set<String>> sanctions = Map.of("brasilia", Set.of("havana"));

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana", "Brasilia"), Set.of(), Map.of(), Map.of(), sanctions, Map.of(), new ArrayList<>());

        assertEquals(1, result.size());
        TradeStatus status = result.get("Havana");
        assertEquals("Brasilia", status.restrictions().get(0).appliedBy());
    }

    @Test
    void unresolvableIssuerIsSkippedAndLogged() {
        Map<String, Set<String>> sanctions = Map.of("Atlantis", Set.of("Havana"));
        List<String> warnings = new ArrayList<>();

        Map<String, TradeStatus> result = aggregate(
                Set.of("Havana"), Set.of(), Map.of(), Map.of(), sanctions, Map.of(), warnings);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }
}
