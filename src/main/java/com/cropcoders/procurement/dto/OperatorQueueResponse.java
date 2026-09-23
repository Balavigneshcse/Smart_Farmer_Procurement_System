package com.cropcoders.procurement.dto;

import java.util.List;

/** Everything on the operator desk: counters, the waiting line, farmers still expected today, and numbers. */
public record OperatorQueueResponse(
        CentreResponse centre,
        List<CounterView> counters,
        List<OperatorBookingView> waiting,
        List<OperatorBookingView> expected,
        Stats stats) {

    /** booking is null when the counter is free. */
    public record CounterView(int counterNo, OperatorBookingView booking) {
    }

    public record Stats(int expected, int waiting, int atCounters, int served, Integer avgWaitMinutes,
                        int avgServiceMinutes) {
    }
}
