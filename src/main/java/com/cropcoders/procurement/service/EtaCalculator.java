package com.cropcoders.procurement.service;

import java.util.List;

/**
 * Waiting-time estimates for the live queue. Pure functions, so they are easy to test and explain.
 *
 *   average service time = average of the last few farmers actually served today
 *                          (falls back to the centre's configured time until there are enough)
 *   ETA for a position   = ceil(farmers who can't go to a free counter / counters) x average service time
 */
public final class EtaCalculator {

    /** How many recent services are needed before we trust the real average. */
    static final int MIN_SAMPLES = 3;

    private EtaCalculator() {
    }

    /**
     * @param recentServiceMinutes minutes from "called" to "done" for the latest farmers served today
     * @param configuredMinutes    the centre's default average service time
     */
    public static int averageServiceMinutes(List<Double> recentServiceMinutes, int configuredMinutes) {
        if (recentServiceMinutes.size() < MIN_SAMPLES) {
            return configuredMinutes;
        }
        double average = recentServiceMinutes.stream().mapToDouble(Double::doubleValue).average().orElse(configuredMinutes);
        return Math.max(1, (int) Math.round(average));
    }

    /**
     * @param position     1 = first in the waiting line
     * @param counters     number of counters at the centre
     * @param freeCounters counters with nobody at them right now
     * @param avgMinutes   average service time per farmer
     * @return estimated minutes until this farmer is called
     */
    public static int etaMinutes(int position, int counters, int freeCounters, int avgMinutes) {
        int stillWaiting = Math.max(0, position - freeCounters);    // the first few can walk to a free counter
        int rounds = (stillWaiting + counters - 1) / counters;       // ceil(stillWaiting / counters)
        return rounds * avgMinutes;
    }
}
