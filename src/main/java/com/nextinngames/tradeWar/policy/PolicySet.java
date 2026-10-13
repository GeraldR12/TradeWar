package com.nextinngames.tradeWar.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything one holder (nation or nationless town) has imposed.
 */
public final class PolicySet {
    /** At most one per target. */
    public final List<Tariff> tariffs = new ArrayList<>();
    /** {@code town:<name>} or {@code player:<uuid>}. */
    public final Set<String> sanctions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    /** Nation names. */
    public final Set<String> embargoes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public boolean isEmpty() {
        return tariffs.isEmpty() && sanctions.isEmpty() && embargoes.isEmpty();
    }

    /** Adds the tariff, replacing any existing one on the same target. */
    public void putTariff(Tariff tariff) {
        tariffs.removeIf(t -> t.targets(tariff.targetType(), tariff.targetName()));
        tariffs.add(tariff);
    }
}
