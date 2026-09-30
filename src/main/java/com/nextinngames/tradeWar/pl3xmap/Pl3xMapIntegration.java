package com.nextinngames.tradeWar.pl3xmap;

import com.nextinngames.tradeWar.TradeWar;
import net.pl3x.map.core.Pl3xMap;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.Set;

public final class Pl3xMapIntegration {

    private final Pl3xMapMarkerManager markerManager;

    private Pl3xMapIntegration(TradeWar plugin) {
        this.markerManager = new Pl3xMapMarkerManager(plugin);
    }

    public static Optional<Pl3xMapIntegration> tryInitialize(TradeWar plugin) {
        if (!plugin.getConfig().getBoolean("pl3xmap.enabled", true)) {
            return Optional.empty();
        }
        if (Bukkit.getPluginManager().getPlugin("Pl3xMap") == null) {
            plugin.getLogger().info("Pl3xMap not found - map integration disabled.");
            return Optional.empty();
        }
        try {
            Pl3xMapIntegration integration = new Pl3xMapIntegration(plugin);
            integration.register();
            return Optional.of(integration);
        } catch (Throwable t) {
            plugin.getLogger().warning("Pl3xMap integration failed to initialize; map integration disabled.");
            return Optional.empty();
        }
    }

    private void register() {
        Pl3xMap.api().getEventRegistry().register(markerManager);
        if (Pl3xMap.api().isEnabled()) {
            markerManager.onPl3xMapReady();
        }
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
