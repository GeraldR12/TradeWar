package com.nextinngames.tradeWar;

import com.nextinngames.tradeWar.policy.PolicyRules;
import com.nextinngames.tradeWar.policy.PolicyScope;
import com.nextinngames.tradeWar.policy.PolicySet;
import com.nextinngames.tradeWar.policy.Tariff;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeDataManagerTest {

    private static Map<String, Object> rule(String type, String target, String item, double percentage) {
        return Map.of("type", type, "targetType", "town", "targetName", target, "item", item,
                "percentage", percentage, "expiry", 0L);
    }

    @Test
    void legacyDataMigratesToHolderPolicies() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("tariffs.Havana", List.of(
                rule("import", "London", "ALL", 10.0),
                rule("export", "London", "ALL", 25.0),
                rule("import", "Freeport", "IRON_INGOT", 15.0)));
        config.set("sanctions.Havana", List.of("Tortuga"));
        config.set("embargoes.Cuba", List.of("UnitedKingdom"));
        Map<String, PolicySet> nations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, PolicySet> towns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> warnings = new ArrayList<>();

        assertTrue(TradeDataManager.migrateLegacy(config, nations, towns, warnings::add));

        PolicySet havana = towns.get("Havana");
        // import + export on the same target collapse into one flat tariff at the higher rate
        assertEquals(List.of(new Tariff(Tariff.TOWN, "London", 25.0, 0L)), havana.tariffs);
        assertEquals(Set.of("town:Tortuga"), havana.sanctions);
        assertEquals(Set.of("UnitedKingdom"), nations.get("cuba").embargoes);
        // the item-specific tariff can't be expressed and is reported
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("IRON_INGOT"));
    }

    @Test
    void newFormatHasNothingToMigrate() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("policies.nations.Cuba.embargoes", List.of("UnitedKingdom"));
        assertFalse(TradeDataManager.migrateLegacy(config, new TreeMap<>(), new TreeMap<>(), w -> { }));
    }

    @Test
    void townRenameMovesHolderAndRewritesReferences() {
        TradeDataManager data = new TradeDataManager(null);
        PolicySet freeport = data.townPolicies.computeIfAbsent("Freeport", k -> new PolicySet());
        freeport.sanctions.add(PolicyRules.townKey("Tortuga"));
        PolicySet cuba = data.nationPolicies.computeIfAbsent("Cuba", k -> new PolicySet());
        cuba.putTariff(new Tariff(Tariff.TOWN, "Freeport", 10.0, 0L));
        cuba.putTariff(new Tariff(Tariff.NATION, "Freeport", 3.0, 0L));

        data.applyRename(PolicyScope.TOWN, "Freeport", "NewPort");
        data.applyRename(PolicyScope.TOWN, "tortuga", "Turtle");

        assertNull(data.townPolicies.get("Freeport"));
        assertEquals(Set.of("town:Turtle"), data.townPolicies.get("NewPort").sanctions);
        assertTrue(cuba.tariffs.contains(new Tariff(Tariff.TOWN, "NewPort", 10.0, 0L)));
        // a nation that happens to share the old name is untouched
        assertTrue(cuba.tariffs.contains(new Tariff(Tariff.NATION, "Freeport", 3.0, 0L)));
    }

    @Test
    void nationRenameRewritesEmbargoesAndNationTariffs() {
        TradeDataManager data = new TradeDataManager(null);
        PolicySet freeport = data.townPolicies.computeIfAbsent("Freeport", k -> new PolicySet());
        freeport.embargoes.add("Cuba");
        freeport.putTariff(new Tariff(Tariff.NATION, "Cuba", 5.0, 0L));
        data.nationPolicies.computeIfAbsent("Cuba", k -> new PolicySet()).embargoes.add("UnitedKingdom");

        data.applyRename(PolicyScope.NATION, "Cuba", "Republic");

        assertEquals(Set.of("Republic"), freeport.embargoes);
        assertEquals(List.of(new Tariff(Tariff.NATION, "Republic", 5.0, 0L)), freeport.tariffs);
        assertEquals(Set.of("UnitedKingdom"), data.nationPolicies.get("Republic").embargoes);
    }
}
