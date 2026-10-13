package com.nextinngames.tradeWar;

import com.nextinngames.tradeWar.policy.PolicyHolder;
import com.nextinngames.tradeWar.policy.PolicyRules;
import com.nextinngames.tradeWar.policy.PolicyScope;
import com.nextinngames.tradeWar.policy.PolicySet;
import com.nextinngames.tradeWar.policy.Tariff;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;

public class TradeDataManager {
    private static final String NATIONS_PATH = "policies.nations";
    private static final String TOWNS_PATH = "policies.towns";

    private final TradeWar plugin;

    // Holder Name -> its policies. Town entries only take effect while that town has no nation.
    public final Map<String, PolicySet> nationPolicies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public final Map<String, PolicySet> townPolicies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public TradeDataManager(TradeWar plugin) {
        this.plugin = plugin;
    }

    public Map<String, PolicySet> policiesFor(PolicyScope scope) {
        return scope == PolicyScope.NATION ? nationPolicies : townPolicies;
    }

    /** The holder's policies, or null if it has none. */
    public PolicySet get(PolicyHolder holder) {
        return holder == null ? null : policiesFor(holder.scope()).get(holder.name());
    }

    public PolicySet getOrCreate(PolicyHolder holder) {
        return policiesFor(holder.scope()).computeIfAbsent(holder.name(), ignored -> new PolicySet());
    }

    /** Drops the holder's entry once it has no policies left, so data.yml stays tidy. */
    public void pruneIfEmpty(PolicyHolder holder) {
        PolicySet set = get(holder);
        if (set != null && set.isEmpty()) {
            policiesFor(holder.scope()).remove(holder.name());
        }
    }

    /**
     * Keeps policies attached after a Towny rename: moves the holder entry and rewrites every reference.
     */
    public void rename(PolicyScope scope, String oldName, String newName) {
        applyRename(scope, oldName, newName);
        saveData();
    }

    void applyRename(PolicyScope scope, String oldName, String newName) {
        Map<String, PolicySet> own = policiesFor(scope);
        PolicySet moved = own.remove(oldName);
        if (moved != null) {
            own.put(newName, moved);
        }

        String targetType = scope == PolicyScope.NATION ? Tariff.NATION : Tariff.TOWN;
        for (Map<String, PolicySet> holders : List.of(nationPolicies, townPolicies)) {
            for (PolicySet set : holders.values()) {
                set.tariffs.replaceAll(t -> t.targets(targetType, oldName) ? t.withTargetName(newName) : t);
                if (scope == PolicyScope.TOWN && set.sanctions.remove(PolicyRules.townKey(oldName))) {
                    set.sanctions.add(PolicyRules.townKey(newName));
                }
                if (scope == PolicyScope.NATION && set.embargoes.remove(oldName)) {
                    set.embargoes.add(newName);
                }
            }
        }
    }

    /**
     * Saves all in-memory data to data.yml
     */
    public void saveData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration config = new YamlConfiguration();
        write(config, NATIONS_PATH, nationPolicies);
        write(config, TOWNS_PATH, townPolicies);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save trade data!");
            e.printStackTrace();
        }
    }

    private static void write(FileConfiguration config, String path, Map<String, PolicySet> holders) {
        for (Map.Entry<String, PolicySet> entry : holders.entrySet()) {
            PolicySet set = entry.getValue();
            if (set.isEmpty()) {
                continue;
            }
            String base = path + "." + entry.getKey();
            List<Map<String, Object>> tariffs = new ArrayList<>();
            for (Tariff tariff : set.tariffs) {
                tariffs.add(tariff.toMap());
            }
            config.set(base + ".tariffs", tariffs);
            config.set(base + ".sanctions", new ArrayList<>(set.sanctions));
            config.set(base + ".embargoes", new ArrayList<>(set.embargoes));
        }
    }

    /**
     * Drops everything in memory and loads data.yml again.
     */
    public void reload() {
        nationPolicies.clear();
        townPolicies.clear();
        loadData();
    }

    /**
     * Loads all data from data.yml into memory, migrating the old /tw format if found.
     */
    public void loadData() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Consumer<String> warn = msg -> plugin.getLogger().warning(msg);
        read(config.getConfigurationSection(NATIONS_PATH), nationPolicies, warn);
        read(config.getConfigurationSection(TOWNS_PATH), townPolicies, warn);

        if (migrateLegacy(config, nationPolicies, townPolicies, warn)) {
            File backup = new File(plugin.getDataFolder(), "data.yml.pre-towny-commands");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Migrated old /tw trade data to the /t + /n format. Backup: " + backup.getName());
                saveData();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not back up data.yml before migration. Copy it manually now: "
                        + "it will be rewritten in the new format at the next policy change.");
                e.printStackTrace();
            }
        }
    }

    private static void read(ConfigurationSection section, Map<String, PolicySet> into, Consumer<String> warn) {
        if (section == null) {
            return;
        }
        for (String holder : section.getKeys(false)) {
            PolicySet set = into.computeIfAbsent(holder, ignored -> new PolicySet());
            for (Map<?, ?> map : section.getMapList(holder + ".tariffs")) {
                try {
                    set.putTariff(Tariff.fromMap(map));
                } catch (Exception e) {
                    warn.accept("Failed to load a tariff for: " + holder);
                }
            }
            set.sanctions.addAll(section.getStringList(holder + ".sanctions"));
            set.embargoes.addAll(section.getStringList(holder + ".embargoes"));
        }
    }

    /**
     * Converts the pre-1.4 layout (/tw town tariffs, town sanctions, nation embargoes) into holder policies.
     * Item-specific tariffs can't be expressed as flat tariffs and are dropped with a warning.
     *
     * @return true if any old data was found
     */
    static boolean migrateLegacy(ConfigurationSection config, Map<String, PolicySet> nations, Map<String, PolicySet> towns, Consumer<String> warn) {
        boolean found = false;

        ConfigurationSection tariffSection = config.getConfigurationSection("tariffs");
        if (tariffSection != null) {
            found = true;
            for (String town : tariffSection.getKeys(false)) {
                for (Map<?, ?> rule : tariffSection.getMapList(town)) {
                    Object item = rule.get("item");
                    Object target = rule.get("targetName");
                    Object percentage = rule.get("percentage");
                    Object expiry = rule.get("expiry");
                    if (!(target instanceof String targetName) || !(percentage instanceof Number pct) || !(expiry instanceof Number exp)) {
                        warn.accept("[Migration] Skipped unreadable tariff of town " + town + ": " + rule);
                        continue;
                    }
                    if (item != null && !"ALL".equalsIgnoreCase(item.toString())) {
                        warn.accept("[Migration] Dropped item-specific tariff " + town + " -> " + targetName + " (" + item
                                + ", " + pct + "%). Re-add it with /t tariff if still wanted.");
                        continue;
                    }
                    PolicySet set = towns.computeIfAbsent(town, ignored -> new PolicySet());
                    Optional<Tariff> existing = set.tariffs.stream().filter(t -> t.targets(Tariff.TOWN, targetName)).findFirst();
                    // Old import + export rules on one target collapse into one flat tariff; keep the higher.
                    if (existing.isEmpty() || existing.get().percentage() < pct.doubleValue()) {
                        set.putTariff(new Tariff(Tariff.TOWN, targetName, pct.doubleValue(), exp.longValue()));
                    }
                }
            }
        }

        ConfigurationSection sanctionSection = config.getConfigurationSection("sanctions");
        if (sanctionSection != null) {
            found = true;
            for (String town : sanctionSection.getKeys(false)) {
                PolicySet set = towns.computeIfAbsent(town, ignored -> new PolicySet());
                for (String target : sanctionSection.getStringList(town)) {
                    set.sanctions.add(PolicyRules.townKey(target));
                }
            }
        }

        ConfigurationSection embargoSection = config.getConfigurationSection("embargoes");
        if (embargoSection != null) {
            found = true;
            for (String nation : embargoSection.getKeys(false)) {
                nations.computeIfAbsent(nation, ignored -> new PolicySet()).embargoes.addAll(embargoSection.getStringList(nation));
            }
        }
        return found;
    }
}
