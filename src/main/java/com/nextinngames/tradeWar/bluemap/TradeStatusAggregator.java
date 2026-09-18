package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.TradeDataManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class TradeStatusAggregator {

    public Map<String, TradeStatus> aggregate(
            Set<String> townNames,
            Set<String> nationNames,
            Map<String, List<String>> townsByNation,
            Map<String, List<TradeDataManager.TariffRule>> tariffRules,
            Map<String, Set<String>> sanctions,
            Map<String, Set<String>> embargoes,
            long now,
            Consumer<String> warnSink
    ) {
        Map<String, String> townByLower = byLower(townNames);
        Map<String, String> nationByLower = byLower(nationNames);
        Map<String, List<TradeRestriction>> byTown = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : sanctions.entrySet()) {
            String host = townByLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (host == null) {
                warnSink.accept("Sanction issuing town not found (skipped): " + entry.getKey());
                continue;
            }
            for (String targetRaw : entry.getValue()) {
                String target = townByLower.get(targetRaw.toLowerCase(Locale.ROOT));
                if (target == null) {
                    warnSink.accept("Sanction target town not found (skipped): " + targetRaw + " (issued by " + host + ")");
                    continue;
                }
                byTown.computeIfAbsent(target, k -> new ArrayList<>()).add(TradeRestriction.sanction(host));
            }
        }

        for (Map.Entry<String, Set<String>> entry : embargoes.entrySet()) {
            String host = nationByLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (host == null) {
                warnSink.accept("Embargo issuing nation not found (skipped): " + entry.getKey());
                continue;
            }
            for (String targetRaw : entry.getValue()) {
                String target = nationByLower.get(targetRaw.toLowerCase(Locale.ROOT));
                if (target == null) {
                    warnSink.accept("Embargo target nation not found (skipped): " + targetRaw + " (issued by " + host + ")");
                    continue;
                }
                for (String town : townsByNation.getOrDefault(target, List.of())) {
                    byTown.computeIfAbsent(town, k -> new ArrayList<>()).add(TradeRestriction.embargo(host));
                }
            }
        }

        for (Map.Entry<String, List<TradeDataManager.TariffRule>> entry : tariffRules.entrySet()) {
            String host = townByLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (host == null) {
                warnSink.accept("Tariff issuing town not found (skipped): " + entry.getKey());
                continue;
            }
            for (TradeDataManager.TariffRule rule : entry.getValue()) {
                if (rule.expiryTime() > 0 && now > rule.expiryTime()) {
                    continue;
                }
                String target = townByLower.get(rule.targetName().toLowerCase(Locale.ROOT));
                if (target == null) {
                    warnSink.accept("Tariff target town not found (skipped): " + rule.targetName() + " (issued by " + host + ")");
                    continue;
                }
                String item = rule.item() == null ? "ALL" : rule.item().name();
                byTown.computeIfAbsent(target, k -> new ArrayList<>())
                        .add(TradeRestriction.tariff(host, rule.type(), item, rule.percentage(), rule.expiryTime()));
            }
        }

        Map<String, TradeStatus> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<TradeRestriction>> entry : byTown.entrySet()) {
            result.put(entry.getKey(), new TradeStatus(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return result;
    }

    private static Map<String, String> byLower(Set<String> names) {
        Map<String, String> map = new HashMap<>();
        for (String name : names) {
            map.put(name.toLowerCase(Locale.ROOT), name);
        }
        return map;
    }
}
