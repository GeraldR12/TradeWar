package com.nextinngames.tradeWar.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyRulesTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    void nationTariffAppliesToEveryTownInTargetNation() {
        List<Tariff> tariffs = List.of(new Tariff(Tariff.NATION, "Cuba", 10.0, 0L));
        assertEquals(10.0, PolicyRules.applicablePercentage(tariffs, "Havana", "cuba", NOW));
        assertEquals(0.0, PolicyRules.applicablePercentage(tariffs, "Havana", "Mexico", NOW));
        assertEquals(0.0, PolicyRules.applicablePercentage(tariffs, "Havana", null, NOW));
    }

    @Test
    void townAndNationTariffsStack() {
        List<Tariff> tariffs = List.of(
                new Tariff(Tariff.NATION, "Cuba", 10.0, 0L),
                new Tariff(Tariff.TOWN, "Havana", 5.5, 0L),
                new Tariff(Tariff.TOWN, "Santiago", 50.0, 0L));
        assertEquals(15.5, PolicyRules.applicablePercentage(tariffs, "havana", "Cuba", NOW));
    }

    @Test
    void townTariffAppliesToNationlessTown() {
        List<Tariff> tariffs = List.of(new Tariff(Tariff.TOWN, "Freeport", 20.0, 0L));
        assertEquals(20.0, PolicyRules.applicablePercentage(tariffs, "Freeport", null, NOW));
    }

    @Test
    void expiredTariffIsIgnored() {
        List<Tariff> tariffs = List.of(
                new Tariff(Tariff.NATION, "Cuba", 10.0, NOW - 1),
                new Tariff(Tariff.TOWN, "Havana", 3.0, NOW + 1));
        assertEquals(3.0, PolicyRules.applicablePercentage(tariffs, "Havana", "Cuba", NOW));
        assertEquals(0.0, PolicyRules.applicablePercentage(null, "Havana", "Cuba", NOW));
    }

    @Test
    void sanctionMatchesTownCaseInsensitiveOrPlayerUuid() {
        UUID player = UUID.randomUUID();
        Set<String> sanctions = Set.of("town:Havana", PolicyRules.playerKey(player));

        assertTrue(PolicyRules.isSanctioned(sanctions, "HAVANA", null));
        assertTrue(PolicyRules.isSanctioned(sanctions, null, player));
        assertTrue(PolicyRules.isSanctioned(sanctions, "Santiago", player));
        assertFalse(PolicyRules.isSanctioned(sanctions, "Santiago", UUID.randomUUID()));
        assertFalse(PolicyRules.isSanctioned(null, "Havana", player));
    }

    @Test
    void sanctionOnPlayerNameDoesNotMatchTownWithSameName() {
        assertFalse(PolicyRules.isSanctioned(Set.of("player:Havana"), "Havana", null));
    }

    @Test
    void embargoMatchesNationCaseInsensitive() {
        Set<String> embargoes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        embargoes.add("Cuba");
        assertTrue(PolicyRules.isEmbargoed(embargoes, "CUBA"));
        assertTrue(PolicyRules.isEmbargoed(Set.of("Cuba"), "cuba"));
        assertFalse(PolicyRules.isEmbargoed(embargoes, null));
        assertFalse(PolicyRules.isEmbargoed(null, "Cuba"));
    }

    @Test
    void putTariffReplacesExistingTargetCaseInsensitive() {
        PolicySet set = new PolicySet();
        set.putTariff(new Tariff(Tariff.TOWN, "Havana", 10.0, 0L));
        set.putTariff(new Tariff(Tariff.NATION, "Havana", 7.0, 0L));
        set.putTariff(new Tariff(Tariff.TOWN, "havana", 25.0, 0L));

        assertEquals(2, set.tariffs.size());
        assertEquals(32.0, PolicyRules.applicablePercentage(set.tariffs, "Havana", "Havana", NOW));
    }

    @Test
    void durationParsesSuffixesAndDefaultsToMinutes() {
        assertEquals(45_000L, PolicyRules.parseDurationMillis("45s"));
        assertEquals(30 * 60_000L, PolicyRules.parseDurationMillis("30m"));
        assertEquals(2 * 3_600_000L, PolicyRules.parseDurationMillis("2H"));
        assertEquals(86_400_000L, PolicyRules.parseDurationMillis("1d"));
        assertEquals(90 * 60_000L, PolicyRules.parseDurationMillis("90"));
    }

    @Test
    void durationRejectsInvalidInput() {
        assertThrows(NumberFormatException.class, () -> PolicyRules.parseDurationMillis("abc"));
        assertThrows(NumberFormatException.class, () -> PolicyRules.parseDurationMillis("5w"));
        assertThrows(IllegalArgumentException.class, () -> PolicyRules.parseDurationMillis("0m"));
        assertThrows(IllegalArgumentException.class, () -> PolicyRules.parseDurationMillis("-5"));
        assertThrows(IllegalArgumentException.class, () -> PolicyRules.parseDurationMillis(" "));
        assertThrows(ArithmeticException.class, () -> PolicyRules.parseDurationMillis("9223372036854775807d"));
    }

    @Test
    void remainingFormat() {
        assertEquals("Permanent", PolicyRules.formatRemaining(0L, NOW));
        assertEquals("Expired", PolicyRules.formatRemaining(NOW - 1, NOW));
        assertEquals("<1m", PolicyRules.formatRemaining(NOW + 30_000L, NOW));
        assertEquals("1d 2h 5m", PolicyRules.formatRemaining(NOW + (26 * 60 + 5) * 60_000L, NOW));
        assertEquals("2h", PolicyRules.formatRemaining(NOW + 120 * 60_000L, NOW));
    }
}
