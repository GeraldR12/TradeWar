package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.TradeWar;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.Set;

public final class BlueMapIntegration {

    private final TradeMarkerManager markerManager;

    private BlueMapIntegration(TradeWar plugin) {
        this.markerManager = new TradeMarkerManager(plugin);
    }

    public static Optional<BlueMapIntegration> tryInitialize(TradeWar plugin) {
        if (!plugin.getConfig().getBoolean("bluemap.enabled", true)) {
            return Optional.empty();
        }
        if (Bukkit.getPluginManager().getPlugin("BlueMap") == null) {
            plugin.getLogger().info("BlueMap not found - map integration disabled.");
            return Optional.empty();
        }
        try {
            BlueMapIntegration integration = new BlueMapIntegration(plugin);
            integration.register();
            return Optional.of(integration);
        } catch (Throwable t) {
            plugin.getLogger().warning("BlueMap integration failed to initialize; map integration disabled.");
            return Optional.empty();
        }
    }

    private void register() {
        BlueMapAPI.onEnable(api -> markerManager.onBlueMapEnable(api));
        BlueMapAPI.onDisable(api -> markerManager.onBlueMapDisable());
    }

    public void onTownChanged(String townName) {
        if (townName != null) {
            markerManager.refreshTowns(Set.of(townName));
        }
    }

    public void onTownsChanged(Set<String> townNames) {
        markerManager.refreshTowns(townNames);
    }

    public void fullRefresh() {
        markerManager.fullRefresh();
    }

    public void shutdown() {
        markerManager.shutdown();
    }
}
