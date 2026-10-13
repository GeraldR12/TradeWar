package com.nextinngames.tradeWar.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Flat (all goods) tariff on a nation or a town.
 */
public record Tariff(
        String targetType,  // "nation" or "town"
        String targetName,
        double percentage,
        long expiryTime     // Timestamp in milliseconds (0 for permanent)
) {
    public static final String NATION = "nation";
    public static final String TOWN = "town";

    public boolean isExpired(long now) {
        return expiryTime > 0 && now > expiryTime;
    }

    public boolean targets(String type, String name) {
        return targetType.equals(type) && targetName.equalsIgnoreCase(name);
    }

    public Tariff withTargetName(String newName) {
        return new Tariff(targetType, newName, percentage, expiryTime);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("targetType", targetType);
        map.put("targetName", targetName);
        map.put("percentage", percentage);
        map.put("expiry", expiryTime);
        return map;
    }

    public static Tariff fromMap(Map<?, ?> map) {
        return new Tariff(
                (String) map.get("targetType"),
                (String) map.get("targetName"),
                ((Number) map.get("percentage")).doubleValue(),
                ((Number) map.get("expiry")).longValue()
        );
    }
}
