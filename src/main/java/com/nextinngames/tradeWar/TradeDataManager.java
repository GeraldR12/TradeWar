package com.nextinngames.tradeWar;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TradeDataManager {
    private final TradeWar plugin;
    public final Map<String, Double> tariffs = new HashMap<>();
    public final Map<String, Set<String>> sanctions = new HashMap<>();
    public final Map<String, Set<String>> embargoes = new HashMap<>();

    public TradeDataManager(TradeWar plugin) { this.plugin = plugin; }

    public void saveData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("tariffs", tariffs);
        for (Map.Entry<String, Set<String>> entry : sanctions.entrySet()) {
            config.set("sanctions." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Map.Entry<String, Set<String>> entry : embargoes.entrySet()) {
            config.set("embargoes." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public void loadData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.getConfigurationSection("tariffs") != null) {
            for (String key : config.getConfigurationSection("tariffs").getKeys(false)) {
                tariffs.put(key, config.getDouble("tariffs." + key));
            }
        }
        if (config.getConfigurationSection("sanctions") != null) {
            for (String key : config.getConfigurationSection("sanctions").getKeys(false)) {
                sanctions.put(key, new HashSet<>(config.getStringList("sanctions." + key)));
            }
        }
        if (config.getConfigurationSection("embargoes") != null) {
            for (String key : config.getConfigurationSection("embargoes").getKeys(false)) {
                embargoes.put(key, new HashSet<>(config.getStringList("embargoes." + key)));
            }
        }
    }
}