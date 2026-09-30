package com.nextinngames.tradeWar.pl3xmap;

import com.nextinngames.tradeWar.TradeDataManager;
import com.nextinngames.tradeWar.TradeWar;
import com.nextinngames.tradeWar.bluemap.TradeMarkerFormatter;
import com.nextinngames.tradeWar.bluemap.TradeRestriction;
import com.nextinngames.tradeWar.bluemap.TradeStatus;
import com.nextinngames.tradeWar.bluemap.TradeStatusAggregator;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.server.Pl3xMapDisabledEvent;
import net.pl3x.map.core.event.server.Pl3xMapEnabledEvent;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Pl3xMapMarkerManager implements EventListener {

    private static final String LAYER_KEY = "tradewar";
    private static final double MARKER_RADIUS = 24.0;

    private final TradeWar plugin;
    private final TradeStatusAggregator aggregator = new TradeStatusAggregator();

    private volatile boolean ready = false;
    private final Map<String, String> markerWorldByTown = new HashMap<>();
    private final Set<String> knownMarkerTowns = new HashSet<>();
    private BukkitTask pendingExpirySweep;
    private boolean loggedInitialCount = false;

    public Pl3xMapMarkerManager(TradeWar plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPl3xMapEnabled(Pl3xMapEnabledEvent event) {
        onPl3xMapReady();
    }

    @EventHandler
    public void onPl3xMapDisabled(Pl3xMapDisabledEvent event) {
        this.ready = false;
    }

    @EventHandler
    public void onWorldLoaded(WorldLoadedEvent event) {
        // Pl3xMap rebuilds its worlds (and their layer registries) on reload, so re-add our layer.
        fullRefresh();
    }

    public void onPl3xMapReady() {
        this.ready = true;
        fullRefresh();
    }

    public void fullRefresh() {
        if (!ready) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::doFullRefresh);
    }

    public void refreshTowns(Set<String> townNames) {
        if (!ready || townNames.isEmpty()) {
            return;
        }
        Set<String> copy = Set.copyOf(townNames);
        Bukkit.getScheduler().runTask(plugin, () -> doTargetedRefresh(copy));
    }

    public void shutdown() {
        BukkitTask task = pendingExpirySweep;
        if (task != null) {
            task.cancel();
            pendingExpirySweep = null;
        }
        if (!ready) {
            return;
        }
        try {
            Pl3xMap.api().getWorldRegistry().values().forEach(world -> world.getLayerRegistry().unregister(LAYER_KEY));
        } catch (Throwable t) {
            plugin.getLogger().warning("[Pl3xMap] Failed to clear markers on shutdown: " + t.getMessage());
        }
        knownMarkerTowns.clear();
        markerWorldByTown.clear();
    }

    private void doFullRefresh() {
        if (!ready) {
            return;
        }
        Map<String, TradeStatus> statuses = computeAllStatuses();

        Set<String> newKnown = new HashSet<>();
        for (Map.Entry<String, TradeStatus> entry : statuses.entrySet()) {
            if (applyMarker(entry.getKey(), entry.getValue())) {
                newKnown.add(entry.getKey());
            }
        }
        for (String previous : knownMarkerTowns) {
            if (!newKnown.contains(previous)) {
                removeMarker(previous);
            }
        }
        knownMarkerTowns.clear();
        knownMarkerTowns.addAll(newKnown);

        scheduleNextExpirySweep(statuses);

        if (!loggedInitialCount) {
            plugin.getLogger().info("Pl3xMap Trade Wars layer active: " + statuses.size() + " town marker(s).");
            loggedInitialCount = true;
        } else {
            plugin.getLogger().fine("Pl3xMap Trade Wars full refresh: " + statuses.size() + " town marker(s).");
        }
    }

    private void doTargetedRefresh(Set<String> townNames) {
        if (!ready) {
            return;
        }
        Map<String, TradeStatus> statuses = computeAllStatuses();

        for (String townName : townNames) {
            TradeStatus status = statuses.get(townName);
            if (status == null) {
                if (knownMarkerTowns.remove(townName)) {
                    removeMarker(townName);
                }
            } else if (applyMarker(townName, status)) {
                knownMarkerTowns.add(townName);
            }
        }

        scheduleNextExpirySweep(statuses);
        plugin.getLogger().fine("Pl3xMap Trade Wars targeted refresh: " + townNames.size() + " town(s) touched.");
    }

    private Map<String, TradeStatus> computeAllStatuses() {
        List<Town> towns = TownyAPI.getInstance().getTowns();
        List<Nation> nations = TownyAPI.getInstance().getNations();

        Set<String> townNames = new HashSet<>();
        for (Town town : towns) {
            townNames.add(town.getName());
        }

        Set<String> nationNames = new HashSet<>();
        Map<String, List<String>> townsByNation = new HashMap<>();
        for (Nation nation : nations) {
            nationNames.add(nation.getName());
            List<String> memberTowns = new ArrayList<>();
            for (Town town : nation.getTowns()) {
                memberTowns.add(town.getName());
            }
            townsByNation.put(nation.getName(), memberTowns);
        }

        TradeDataManager data = plugin.getData();
        boolean showTariffs = plugin.getConfig().getBoolean("pl3xmap.show-tariffs", true);
        boolean showSanctions = plugin.getConfig().getBoolean("pl3xmap.show-sanctions", true);
        boolean showEmbargoes = plugin.getConfig().getBoolean("pl3xmap.show-embargoes", true);

        return aggregator.aggregate(
                townNames,
                nationNames,
                townsByNation,
                showTariffs ? data.tariffRules : Map.of(),
                showSanctions ? data.sanctions : Map.of(),
                showEmbargoes ? data.embargoes : Map.of(),
                System.currentTimeMillis(),
                msg -> plugin.getLogger().warning("[Pl3xMap] " + msg)
        );
    }

    private boolean applyMarker(String townName, TradeStatus status) {
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            plugin.getLogger().warning("[Pl3xMap] Town no longer exists, marker skipped: " + townName);
            return false;
        }
        Location spawn = town.getSpawnOrNull();
        if (spawn == null || spawn.getWorld() == null) {
            plugin.getLogger().warning("[Pl3xMap] Town has no spawn set, marker skipped: " + townName);
            return false;
        }

        String worldName = spawn.getWorld().getName();
        World world = Pl3xMap.api().getWorldRegistry().get(worldName);
        if (world == null || !world.isEnabled()) {
            plugin.getLogger().warning("[Pl3xMap] World not rendered by Pl3xMap, marker skipped for town: " + townName);
            return false;
        }

        // A town whose spawn moved to another world must not leave a stale marker behind.
        String previousWorld = markerWorldByTown.get(townName);
        if (previousWorld != null && !previousWorld.equals(worldName)) {
            removeMarker(townName);
        }

        long now = System.currentTimeMillis();
        int color = severityColor(status.severity());
        Marker<?> marker = Marker.circle(TradeMarkerFormatter.markerId(townName), spawn.getX(), spawn.getZ(), MARKER_RADIUS)
                .setOptions(Options.builder()
                        .strokeColor(color)
                        .fillColor((color & 0x00FFFFFF) | 0x55000000)
                        .tooltipContent(TradeMarkerFormatter.label(status))
                        .popupContent(TradeMarkerFormatter.detailHtml(status, now))
                        .build());

        getOrCreateLayer(world).addMarker(marker);
        markerWorldByTown.put(townName, worldName);
        return true;
    }

    private SimpleLayer getOrCreateLayer(World world) {
        Layer existing = world.getLayerRegistry().get(LAYER_KEY);
        if (existing instanceof SimpleLayer simpleLayer) {
            return simpleLayer;
        }
        String label = plugin.getConfig().getString("pl3xmap.layer-label", "Trade Wars");
        SimpleLayer layer = new SimpleLayer(LAYER_KEY, () -> label);
        layer.setShowControls(true);
        layer.setDefaultHidden(false);
        world.getLayerRegistry().register(layer);
        return layer;
    }

    private void removeMarker(String townName) {
        String markerId = TradeMarkerFormatter.markerId(townName);
        String worldName = markerWorldByTown.remove(townName);
        if (worldName == null) {
            return;
        }
        World world = Pl3xMap.api().getWorldRegistry().get(worldName);
        if (world == null) {
            return;
        }
        Layer layer = world.getLayerRegistry().get(LAYER_KEY);
        if (layer instanceof SimpleLayer simpleLayer) {
            simpleLayer.removeMarker(markerId);
        }
    }

    private static int severityColor(TradeStatus.Severity severity) {
        return switch (severity) {
            case EMBARGO -> 0xFF7A0000;
            case SANCTION -> 0xFFE53935;
            case TARIFF -> 0xFFFB8C00;
            case NONE -> 0xFF9E9E9E;
        };
    }

    private void scheduleNextExpirySweep(Map<String, TradeStatus> statuses) {
        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        for (TradeStatus status : statuses.values()) {
            for (TradeRestriction r : status.restrictions()) {
                if (r.type() == TradeRestriction.Type.TARIFF && r.expiresAt() > 0 && r.expiresAt() < soonest) {
                    soonest = r.expiresAt();
                }
            }
        }

        if (pendingExpirySweep != null) {
            pendingExpirySweep.cancel();
            pendingExpirySweep = null;
        }
        if (soonest == Long.MAX_VALUE) {
            return;
        }

        long delayMillis = Math.max(0, soonest - now) + 1000L;
        long delayTicks = Math.max(1L, delayMillis / 50L);
        pendingExpirySweep = Bukkit.getScheduler().runTaskLater(plugin, this::doFullRefresh, delayTicks);
    }
}
