package com.cropcoders.procurement.scheduler;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cropcoders.procurement.service.SlotService;

/**
 * Keeps a rolling week of slots available - no manual work for the admin.
 * Runs at startup (the free Render server sleeps, so it may miss a night) and every morning.
 */
@Component
public class SlotGenerationJob {

    private final SlotService slotService;

    public SlotGenerationJob(SlotService slotService) {
        this.slotService = slotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        slotService.generateUpcomingSlots();
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void everyMorning() {
        slotService.generateUpcomingSlots();
    }
}
