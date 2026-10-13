package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.policy.PolicySet;
import com.nextinngames.tradeWar.policy.Tariff;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStatusAggregatorTest {

    private static final long NOW = 1_000_000_000L;

    // Cuba = {Havana (capital), Santiago}; UnitedKingdom = {London}; Freeport and Tortuga have no nation.
    private static final Set<String> TOWNS = Set.of("Havana", "Santiago", "London", "Freeport", "Tortuga");
    private static final Set<String> NATIONS = Set.of("Cuba", "UnitedKingdom");
    private static final Map<String, List<String>> TOWNS_BY_NATION = Map.of(
            "Cuba", List.of("Havana", "Santiago"),
            "UnitedKingdom", List.of("London"));

    private final TradeStatusAggregator aggregator = new TradeStatusAggregator();

    private Map<String, TradeStatus> aggregate(Map<String, PolicySet> nations, Map<String, PolicySet> towns,
                                               TradeStatusAggregator.Filter filter, List<String> warnings) {
        return aggregator.aggregate(TOWNS, NATIONS, TOWNS_BY_NATION, nations, towns, filter, NOW, warnings::add);
    }

    private Map<String, TradeStatus> aggregate(Map<String, PolicySet> nations, Map<String, PolicySet> towns) {
        return aggregate(nations, towns, TradeStatusAggregator.Filter.ALL, new ArrayList<>());
    }

    private static PolicySet tariffs(Tariff... tariffs) {
        PolicySet set = new PolicySet();
        for (Tariff tariff : tariffs) {
            set.putTariff(tariff);
        }
        return set;
    }

    private static PolicySet sanctions(String... keys) {
        PolicySet set = new PolicySet();
        set.sanctions.addAll(List.of(keys));
        return set;
    }

    private static PolicySet embargoes(String... nations) {
        PolicySet set = new PolicySet();
        set.embargoes.addAll(List.of(nations));
        return set;
    }

    @Test
    void noPoliciesMeansNoStatus() {
        assertTrue(aggregate(Map.of(), Map.of()).isEmpty());
    }

    @Test
    void nationTariffOnNationMarksEveryMemberTown() {
        Map<String, TradeStatus> result = aggregate(
                Map.of("UnitedKingdom", tariffs(new Tariff(Tariff.NATION, "cuba", 12.5, NOW + 60_000))), Map.of());

        assertEquals(Set.of("Havana", "Santiago"), result.keySet());
        TradeRestriction r = result.get("Havana").restrictions().get(0);
        assertEquals(TradeRestriction.Type.TARIFF, r.type());
        assertEquals("UnitedKingdom (nation)", r.appliedBy());
        assertEquals(12.5, r.percentage());
        assertEquals(NOW + 60_000, r.expiresAt());
    }

    @Test
    void expiredTariffIsSkipped() {
        Map<String, TradeStatus> result = aggregate(
                Map.of("UnitedKingdom", tariffs(new Tariff(Tariff.TOWN, "Freeport", 5.0, NOW - 1))), Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void nationlessTownPoliciesApplyWithPlainTownName() {
        PolicySet set = tariffs(new Tariff(Tariff.TOWN, "London", 10.0, 0L));
        set.sanctions.add("town:Tortuga");
        set.embargoes.add("Cuba");

        Map<String, TradeStatus> result = aggregate(Map.of(), Map.of("Freeport", set));

        assertEquals(Set.of("London", "Tortuga", "Havana", "Santiago"), result.keySet());
        assertEquals("Freeport", result.get("London").restrictions().get(0).appliedBy());
        assertEquals(TradeRestriction.Type.SANCTION, result.get("Tortuga").restrictions().get(0).type());
        assertEquals(TradeRestriction.Type.EMBARGO, result.get("Havana").restrictions().get(0).type());
    }

    @Test
    void townPoliciesArePausedWhileTownIsInANation() {
        Map<String, TradeStatus> result = aggregate(Map.of(), Map.of("Santiago", sanctions("town:Freeport")));
        assertTrue(result.isEmpty());
    }

    @Test
    void embargoMarksAllTownsOfTargetNation() {
        Map<String, TradeStatus> result = aggregate(Map.of("UnitedKingdom", embargoes("Cuba")), Map.of());

        assertEquals(Set.of("Havana", "Santiago"), result.keySet());
        assertEquals(TradeStatus.Severity.EMBARGO, result.get("Havana").severity());
        assertEquals("UnitedKingdom (nation)", result.get("Santiago").restrictions().get(0).appliedBy());
    }

    @Test
    void playerSanctionsAreNotDrawn() {
        Map<String, TradeStatus> result = aggregate(
                Map.of("Cuba", sanctions("town:london", "player:0b0e6b3c-0000-0000-0000-000000000000")), Map.of());

        assertEquals(Set.of("London"), result.keySet());
        assertEquals(TradeRestriction.Type.SANCTION, result.get("London").restrictions().get(0).type());
    }

    @Test
    void restrictionsFromSeveralHoldersStackAndWorstWins() {
        Map<String, TradeStatus> result = aggregate(
                Map.of("UnitedKingdom", embargoes("Cuba"), "Cuba", tariffs(new Tariff(Tariff.TOWN, "London", 5.0, 0L))),
                Map.of("Freeport", tariffs(new Tariff(Tariff.TOWN, "Havana", 5.0, 0L))));

        TradeStatus havana = result.get("Havana");
        assertEquals(2, havana.restrictions().size());
        assertEquals(TradeStatus.Severity.EMBARGO, havana.severity());
        assertEquals(TradeStatus.Severity.TARIFF, result.get("London").severity());
    }

    @Test
    void filterHidesRestrictionTypes() {
        PolicySet set = tariffs(new Tariff(Tariff.TOWN, "London", 10.0, 0L));
        set.sanctions.add("town:Tortuga");
        set.embargoes.add("Cuba");

        Map<String, TradeStatus> result = aggregate(Map.of(), Map.of("Freeport", set),
                new TradeStatusAggregator.Filter(false, true, false), new ArrayList<>());

        assertEquals(Set.of("Tortuga"), result.keySet());
    }

    @Test
    void unknownHoldersAndTargetsAreSkippedWithWarning() {
        List<String> warnings = new ArrayList<>();
        Map<String, TradeStatus> result = aggregate(
                Map.of("Atlantis", embargoes("Cuba"),
                        "Cuba", tariffs(new Tariff(Tariff.NATION, "Ghost", 5.0, 0L))),
                Map.of("Nowhere", sanctions("town:London"),
                        "Freeport", sanctions("town:Missing")),
                TradeStatusAggregator.Filter.ALL, warnings);

        assertTrue(result.isEmpty());
        assertEquals(4, warnings.size());
        assertFalse(warnings.stream().anyMatch(w -> w.contains("London")));
    }
}
