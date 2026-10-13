package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.policy.PolicyHolder;
import com.nextinngames.tradeWar.policy.PolicyRules;
import com.nextinngames.tradeWar.policy.PolicyScope;
import com.nextinngames.tradeWar.policy.PolicySet;
import com.nextinngames.tradeWar.policy.Tariff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class TradeStatusAggregator {

    /** Which restriction types to put on the map. */
    public record Filter(boolean tariffs, boolean sanctions, boolean embargoes) {
        public static final Filter ALL = new Filter(true, true, true);
    }

    /**
     * Works out, per town, every restriction other holders have placed on it.
     * Town policies are skipped while that town is in a nation: only the nation's apply then.
     */
    public Map<String, TradeStatus> aggregate(
            Set<String> townNames,
            Set<String> nationNames,
            Map<String, List<String>> townsByNation,
            Map<String, PolicySet> nationPolicies,
            Map<String, PolicySet> townPolicies,
            Filter filter,
            long now,
            Consumer<String> warnSink
    ) {
        Map<String, String> townByLower = byLower(townNames);
        Map<String, String> nationByLower = byLower(nationNames);
        Set<String> townsInNations = new HashSet<>();
        townsByNation.values().forEach(towns -> towns.forEach(t -> townsInNations.add(t.toLowerCase(Locale.ROOT))));

        Map<String, List<TradeRestriction>> byTown = new LinkedHashMap<>();
        Resolver resolver = new Resolver(townByLower, nationByLower, townsByNation, warnSink);

        for (Map.Entry<String, PolicySet> entry : nationPolicies.entrySet()) {
            String host = nationByLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (host == null) {
                warnSink.accept("Policy issuing nation not found (skipped): " + entry.getKey());
                continue;
            }
            collect(new PolicyHolder(PolicyScope.NATION, host), entry.getValue(), filter, now, resolver, byTown);
        }

        for (Map.Entry<String, PolicySet> entry : townPolicies.entrySet()) {
            String host = townByLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (host == null) {
                warnSink.accept("Policy issuing town not found (skipped): " + entry.getKey());
                continue;
            }
            if (townsInNations.contains(host.toLowerCase(Locale.ROOT))) {
                continue; // Paused while the town is in a nation.
            }
            collect(new PolicyHolder(PolicyScope.TOWN, host), entry.getValue(), filter, now, resolver, byTown);
        }

        Map<String, TradeStatus> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<TradeRestriction>> entry : byTown.entrySet()) {
            result.put(entry.getKey(), new TradeStatus(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return result;
    }

    private static void collect(PolicyHolder holder, PolicySet policies, Filter filter, long now,
                                Resolver resolver, Map<String, List<TradeRestriction>> byTown) {
        String appliedBy = holder.mapLabel();

        if (filter.embargoes()) {
            for (String nation : policies.embargoes) {
                for (String town : resolver.towns(Tariff.NATION, nation, "Embargo", holder)) {
                    byTown.computeIfAbsent(town, k -> new ArrayList<>()).add(TradeRestriction.embargo(appliedBy));
                }
            }
        }

        if (filter.sanctions()) {
            for (String key : policies.sanctions) {
                // Player sanctions have no territory to draw.
                if (!key.regionMatches(true, 0, PolicyRules.TOWN_PREFIX, 0, PolicyRules.TOWN_PREFIX.length())) {
                    continue;
                }
                String target = key.substring(PolicyRules.TOWN_PREFIX.length());
                for (String town : resolver.towns(Tariff.TOWN, target, "Sanction", holder)) {
                    byTown.computeIfAbsent(town, k -> new ArrayList<>()).add(TradeRestriction.sanction(appliedBy));
                }
            }
        }

        if (filter.tariffs()) {
            for (Tariff tariff : policies.tariffs) {
                if (tariff.isExpired(now)) {
                    continue;
                }
                for (String town : resolver.towns(tariff.targetType(), tariff.targetName(), "Tariff", holder)) {
                    byTown.computeIfAbsent(town, k -> new ArrayList<>())
                            .add(TradeRestriction.tariff(appliedBy, tariff.percentage(), tariff.expiryTime()));
                }
            }
        }
    }

    private record Resolver(
            Map<String, String> townByLower,
            Map<String, String> nationByLower,
            Map<String, List<String>> townsByNation,
            Consumer<String> warnSink
    ) {
        /** The towns a nation/town target covers; nations expand to every member town. */
        List<String> towns(String targetType, String targetName, String what, PolicyHolder holder) {
            if (Tariff.NATION.equals(targetType)) {
                String nation = nationByLower.get(targetName.toLowerCase(Locale.ROOT));
                if (nation != null) {
                    return townsByNation.getOrDefault(nation, List.of());
                }
            } else {
                String town = townByLower.get(targetName.toLowerCase(Locale.ROOT));
                if (town != null) {
                    return List.of(town);
                }
            }
            warnSink.accept(what + " target " + targetType + " not found (skipped): " + targetName + " (issued by " + holder.label() + ")");
            return List.of();
        }
    }

    private static Map<String, String> byLower(Set<String> names) {
        Map<String, String> map = new HashMap<>();
        for (String name : names) {
            map.put(name.toLowerCase(Locale.ROOT), name);
        }
        return map;
    }
}
