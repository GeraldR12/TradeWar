package com.nextinngames.tradeWar.bluemap;

import com.nextinngames.tradeWar.TradeDataManager;
import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TradeMarkerManager {

    private static final String MARKER_SET_ID = "tradewar";

    private final TradeWar plugin;
    private final TradeStatusAggregator aggregator = new TradeStatusAggregator();

    private volatile BlueMapAPI api;
    private final Map<String, String> markerWorldByTown = new HashMap<>();
    private final Set<String> knownMarkerTowns = new HashSet<>();
    private BukkitTask pendingExpirySweep;
    private boolean loggedInitialCount = false;

    public TradeMarkerManager(TradeWar plugin) {
        this.plugin = plugin;
    }

    public boolean isApiReady() {
        return api != null;
    }

    public void onBlueMapEnable(BlueMapAPI api) {
        this.api = api;
        fullRefresh();
    }

    public void onBlueMapDisable() {
        this.api = null;
    }

    public void fullRefresh() {
        if (api == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::doFullRefresh);
    }

    public void refreshTowns(Set<String> townNames) {
        if (api == null || townNames.isEmpty()) {
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
        if (api == null) {
            return;
        }
        try {
            for (String town : new HashSet<>(knownMarkerTowns)) {
                removeMarker(town);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[BlueMap] Failed to clear markers on shutdown: " + t.getMessage());
        }
        knownMarkerTowns.clear();
        markerWorldByTown.clear();
    }

    private void doFullRefresh() {
        if (api == null) {
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
            plugin.getLogger().info("BlueMap Trade Wars layer active: " + statuses.size() + " town marker(s).");
            loggedInitialCount = true;
        } else {
            plugin.getLogger().fine("BlueMap Trade Wars full refresh: " + statuses.size() + " town marker(s).");
        }
    }

    private void doTargetedRefresh(Set<String> townNames) {
        if (api == null) {
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
        plugin.getLogger().fine("BlueMap Trade Wars targeted refresh: " + townNames.size() + " town(s) touched.");
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
        boolean showTariffs = plugin.getConfig().getBoolean("bluemap.show-tariffs", true);
        boolean showSanctions = plugin.getConfig().getBoolean("bluemap.show-sanctions", true);
        boolean showEmbargoes = plugin.getConfig().getBoolean("bluemap.show-embargoes", true);

        return aggregator.aggregate(
                townNames,
                nationNames,
                townsByNation,
                showTariffs ? data.tariffRules : Map.of(),
                showSanctions ? data.sanctions : Map.of(),
                showEmbargoes ? data.embargoes : Map.of(),
                System.currentTimeMillis(),
                msg -> plugin.getLogger().warning("[BlueMap] " + msg)
        );
    }

    private boolean applyMarker(String townName, TradeStatus status) {
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            plugin.getLogger().warning("[BlueMap] Town no longer exists, marker skipped: " + townName);
            return false;
        }
        Location spawn = town.getSpawnOrNull();
        if (spawn == null || spawn.getWorld() == null) {
            plugin.getLogger().warning("[BlueMap] Town has no spawn set, marker skipped: " + townName);
            return false;
        }

        Optional<BlueMapWorld> worldOpt = api.getWorld(spawn.getWorld());
        if (worldOpt.isEmpty()) {
            plugin.getLogger().warning("[BlueMap] World not rendered by BlueMap, marker skipped for town: " + townName);
            return false;
        }

        long now = System.currentTimeMillis();
        POIMarker marker = POIMarker.builder()
                .label(TradeMarkerFormatter.label(status))
                .position(spawn.getX(), spawn.getY(), spawn.getZ())
                .detail(TradeMarkerFormatter.detailHtml(status, now))
                .styleClasses(TradeMarkerFormatter.styleClass(status.severity()))
                .sorting(-status.severity().ordinal())
                .build();

        String markerId = TradeMarkerFormatter.markerId(townName);
        String markerSetLabel = plugin.getConfig().getString("bluemap.marker-set-label", "Trade Wars");
        for (BlueMapMap map : worldOpt.get().getMaps()) {
            MarkerSet set = map.getMarkerSets().computeIfAbsent(MARKER_SET_ID, id ->
                    MarkerSet.builder().label(markerSetLabel).toggleable(true).build());
            set.getMarkers().put(markerId, marker);
        }
        markerWorldByTown.put(townName, spawn.getWorld().getName());
        return true;
    }

    private void removeMarker(String townName) {
        String markerId = TradeMarkerFormatter.markerId(townName);
        String worldName = markerWorldByTown.remove(townName);
        if (worldName == null) {
            return;
        }
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            return;
        }
        api.getWorld(bukkitWorld).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set != null) {
                    set.getMarkers().remove(markerId);
                }
            }
        });
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
