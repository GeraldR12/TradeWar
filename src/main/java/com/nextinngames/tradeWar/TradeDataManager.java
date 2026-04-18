package com.nextinngames.tradeWar;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TradeDataManager {
    private final TradeWar plugin;

    // Updated storage: Host Nation Name -> List of specific Tariff Rules
    public final Map<String, List<TariffRule>> tariffRules = new HashMap<>();
    public final Map<String, Set<String>> sanctions = new HashMap<>();
    public final Map<String, Set<String>> embargoes = new HashMap<>();

    public TradeDataManager(TradeWar plugin) {
        this.plugin = plugin;
    }

    /**
     * Data class to represent a complex tariff policy
     */
    public static record TariffRule(
            String type,        // "import" or "export"
            String targetType,  // "town" or "nation"
            String targetName,  // Name of the town or nation
            Material item,      // Material type or null for "all"
            double percentage,
            long expiryTime     // Timestamp in milliseconds (0 for permanent)
    ) {
        // Helper to convert a rule to a map for YAML saving
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("targetType", targetType);
            map.put("targetName", targetName);
            map.put("item", item == null ? "ALL" : item.name());
            map.put("percentage", percentage);
            map.put("expiry", expiryTime);
            return map;
        }

        // Helper to create a rule from a YAML map
        public static TariffRule fromMap(Map<?, ?> map) {
            String itemStr = (String) map.get("item");
            Material material = itemStr.equalsIgnoreCase("ALL") ? null : Material.valueOf(itemStr);

            return new TariffRule(
                    (String) map.get("type"),
                    (String) map.get("targetType"),
                    (String) map.get("targetName"),
                    material,
                    ((Number) map.get("percentage")).doubleValue(),
                    ((Number) map.get("expiry")).longValue()
            );
        }
    }

    public void saveData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration config = new YamlConfiguration();

        // Save complex Tariffs
        for (Map.Entry<String, List<TariffRule>> entry : tariffRules.entrySet()) {
            List<Map<String, Object>> serializedRules = new ArrayList<>();
            for (TariffRule rule : entry.getValue()) {
                serializedRules.add(rule.toMap());
            }
            config.set("tariffs." + entry.getKey(), serializedRules);
        }

        // Save Sanctions
        for (Map.Entry<String, Set<String>> entry : sanctions.entrySet()) {
            config.set("sanctions." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        // Save Embargoes
        for (Map.Entry<String, Set<String>> entry : embargoes.entrySet()) {
            config.set("embargoes." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save trade data!");
            e.printStackTrace();
        }
    }

    public void loadData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load complex Tariffs
        ConfigurationSection tariffSection = config.getConfigurationSection("tariffs");
        if (tariffSection != null) {
            for (String nation : tariffSection.getKeys(false)) {
                List<Map<?, ?>> ruleMaps = config.getMapList("tariffs." + nation);
                List<TariffRule> rules = new ArrayList<>();
                for (Map<?, ?> map : ruleMaps) {
                    try {
                        rules.add(TariffRule.fromMap(map));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load a tariff rule for " + nation);
                    }
                }
                tariffRules.put(nation, rules);
            }
        }

        // Load Sanctions
        ConfigurationSection sanctionSection = config.getConfigurationSection("sanctions");
        if (sanctionSection != null) {
            for (String nation : sanctionSection.getKeys(false)) {
                sanctions.put(nation, new HashSet<>(config.getStringList("sanctions." + nation)));
            }
        }

        // Load Embargoes
        ConfigurationSection embargoSection = config.getConfigurationSection("embargoes");
        if (embargoSection != null) {
            for (String nation : embargoSection.getKeys(false)) {
                embargoes.put(nation, new HashSet<>(config.getStringList("embargoes." + nation)));
            }
        }
    }
}