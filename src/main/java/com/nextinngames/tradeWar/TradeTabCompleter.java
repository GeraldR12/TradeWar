package com.nextinngames.tradeWar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class TradeTabCompleter implements TabCompleter {
    private final TradeWar plugin;

    public TradeTabCompleter(TradeWar plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            return filterCompletions(Arrays.asList("tariff", "sanction", "embargo"), args[0]);
        }

        String category = args[0].toLowerCase(Locale.ROOT);
        Resident resident = sender instanceof Player player ? TownyAPI.getInstance().getResident(player) : null;

        switch (category) {
            case "tariff":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args[1].equalsIgnoreCase("add")) {
                    if (args.length == 3) return filterCompletions(Arrays.asList("import", "export"), args[2]);
                    if (args.length == 4) return filterCompletions(getTownNames(), args[3]);
                    if (args.length == 5) {
                        List<String> items = Arrays.stream(Material.values()).map(m -> m.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
                        items.add("all");
                        return filterCompletions(items, args[4]);
                    }
                    if (args.length == 6) return filterCompletions(Arrays.asList("10", "50", "100"), args[5]);
                    if (args.length == 7) return filterCompletions(Arrays.asList("60", "1440"), args[6]);
                }
                if (args[1].equalsIgnoreCase("remove") && args.length == 3 && resident != null && resident.hasTown()) {
                    return suggestConfiguredTargets(configuredTariffTargets(resident), args[2]);
                }
                break;

            case "sanction":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
                    Set<String> alreadySanctioned = resident != null && resident.hasTown()
                            ? plugin.getData().sanctions.getOrDefault(resident.getTownOrNull().getName(), Set.of())
                            : Set.of();
                    return suggestNewTargets(getTownNames(), alreadySanctioned, args[2]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                    Set<String> sanctioned = resident != null && resident.hasTown()
                            ? plugin.getData().sanctions.getOrDefault(resident.getTownOrNull().getName(), Set.of())
                            : Set.of();
                    return suggestConfiguredTargets(sanctioned, args[2]);
                }
                break;

            case "embargo":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
                    Set<String> alreadyEmbargoed = resident != null && resident.hasNation()
                            ? plugin.getData().embargoes.getOrDefault(resident.getNationOrNull().getName(), Set.of())
                            : Set.of();
                    return suggestNewTargets(getNationNames(), alreadyEmbargoed, args[2]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                    Set<String> embargoed = resident != null && resident.hasNation()
                            ? plugin.getData().embargoes.getOrDefault(resident.getNationOrNull().getName(), Set.of())
                            : Set.of();
                    return suggestConfiguredTargets(embargoed, args[2]);
                }
                break;
        }

        return completions;
    }

    private Set<String> configuredTariffTargets(Resident resident) {
        Town hostTown = resident.getTownOrNull();
        if (hostTown == null) return Set.of();
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(hostTown.getName());
        if (rules == null || rules.isEmpty()) return Set.of();
        return rules.stream().map(TradeDataManager.TariffRule::targetName).collect(Collectors.toSet());
    }

    private List<String> filterCompletions(List<String> list, String currentArg) {
        return list.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(currentArg.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    static List<String> suggestConfiguredTargets(Set<String> configuredTargets, String currentArg) {
        if (configuredTargets == null || configuredTargets.isEmpty()) return List.of();
        String needle = currentArg.toLowerCase(Locale.ROOT);
        return configuredTargets.stream()
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toMap(t -> t.toLowerCase(Locale.ROOT), t -> t, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(needle))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    static List<String> suggestNewTargets(List<String> allNames, Set<String> alreadyConfigured, String currentArg) {
        Set<String> configuredLower = alreadyConfigured == null ? Set.of() : alreadyConfigured.stream()
                .filter(s -> s != null)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String needle = currentArg.toLowerCase(Locale.ROOT);
        return allNames.stream()
                .filter(n -> !configuredLower.contains(n.toLowerCase(Locale.ROOT)))
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(needle))
                .collect(Collectors.toList());
    }

    private List<String> getTownNames() {
        return TownyAPI.getInstance().getTowns().stream().map(Town::getName).collect(Collectors.toList());
    }

    private List<String> getNationNames() {
        return TownyAPI.getInstance().getNations().stream().map(Nation::getName).collect(Collectors.toList());
    }
}
