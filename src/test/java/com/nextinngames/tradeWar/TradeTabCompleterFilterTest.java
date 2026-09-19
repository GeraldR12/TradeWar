package com.nextinngames.tradeWar;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTabCompleterFilterTest {

    @Test
    void suggestConfiguredTargetsOnlyReturnsCurrentlyActiveEntries() {
        Set<String> sanctioned = Set.of("Riverside", "Oakhaven");
        List<String> result = TradeTabCompleter.suggestConfiguredTargets(sanctioned, "");
        assertEquals(List.of("Oakhaven", "Riverside"), result);
    }

    @Test
    void suggestConfiguredTargetsFiltersByPrefixCaseInsensitive() {
        Set<String> sanctioned = Set.of("Riverside", "Oakhaven");
        assertEquals(List.of("Riverside"), TradeTabCompleter.suggestConfiguredTargets(sanctioned, "riv"));
        assertEquals(List.of("Riverside"), TradeTabCompleter.suggestConfiguredTargets(sanctioned, "RIV"));
        assertTrue(TradeTabCompleter.suggestConfiguredTargets(sanctioned, "zzz").isEmpty());
    }

    @Test
    void suggestConfiguredTargetsHandlesEmptyOrNullSafely() {
        assertTrue(TradeTabCompleter.suggestConfiguredTargets(null, "").isEmpty());
        assertTrue(TradeTabCompleter.suggestConfiguredTargets(Set.of(), "").isEmpty());
    }

    @Test
    void suggestConfiguredTargetsDeduplicatesCaseVariants() {
        Set<String> staleDuplicateCasing = Set.of("Riverside", "riverside");
        List<String> result = TradeTabCompleter.suggestConfiguredTargets(staleDuplicateCasing, "");
        assertEquals(1, result.size());
    }

    @Test
    void suggestNewTargetsExcludesAlreadyConfiguredCaseInsensitive() {
        List<String> allTowns = List.of("Riverside", "Oakhaven", "Millbrook");
        Set<String> alreadySanctioned = Set.of("riverside");
        List<String> result = TradeTabCompleter.suggestNewTargets(allTowns, alreadySanctioned, "");
        assertEquals(List.of("Oakhaven", "Millbrook"), result);
    }

    @Test
    void suggestNewTargetsHandlesStaleConfiguredEntryNotInLiveTownList() {
        List<String> allTowns = List.of("Riverside", "Oakhaven");
        Set<String> alreadySanctioned = Set.of("DeletedTown");
        List<String> result = TradeTabCompleter.suggestNewTargets(allTowns, alreadySanctioned, "");
        assertEquals(List.of("Riverside", "Oakhaven"), result);
    }

    @Test
    void suggestNewTargetsFiltersByPrefix() {
        List<String> allTowns = List.of("Riverside", "Oakhaven", "Millbrook");
        List<String> result = TradeTabCompleter.suggestNewTargets(allTowns, Set.of(), "oak");
        assertEquals(List.of("Oakhaven"), result);
    }

    @Test
    void suggestNewTargetsHandlesNullConfiguredSet() {
        List<String> allTowns = List.of("Riverside");
        assertEquals(List.of("Riverside"), TradeTabCompleter.suggestNewTargets(allTowns, null, ""));
    }
}
