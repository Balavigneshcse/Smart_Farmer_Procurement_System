package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The queue as everyone may see it (TV display, farmer's phone): token numbers only,
 * no names or phone numbers, so it can be public.
 */
public record PublicQueueResponse(
        Long centreId,
        String centreCode,
        String centreName,
        int counters,
        int avgServiceMinutes,
        List<ServingView> serving,
        List<WaitingView> waiting,
        int servedToday,
        Integer avgWaitMinutes,
        LocalDateTime updatedAt) {

    /** One counter: token is null when the counter is free. */
    public record ServingView(int counterNo, String token, LocalDateTime calledAt) {
    }

    public record WaitingView(int position, String token, int etaMinutes) {
    }
}
