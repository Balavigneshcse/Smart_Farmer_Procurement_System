package com.cropcoders.procurement.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cropcoders.procurement.service.AutomationService;

/**
 * Reminders and no-show marking, every 15 minutes during the day.
 * Nothing runs at night, so the free database (Neon) can sleep and save its monthly hours.
 * Also runs at startup, because the free Render server sleeps and may have missed some runs.
 */
@Component
public class AutomationJob {

    private static final Logger log = LoggerFactory.getLogger(AutomationJob.class);

    private final AutomationService automationService;

    public AutomationJob(AutomationService automationService) {
        this.automationService = automationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    /** At :00, :15, :30 and :45 from 7:00 am to 9:45 pm, Indian time. */
    @Scheduled(cron = "0 */15 7-21 * * *", zone = "Asia/Kolkata")
    public void every15Minutes() {
        run();
    }

    private void run() {
        try {
            int reminders = automationService.sendReminders();
            int noShows = automationService.markNoShows();
            if (reminders > 0 || noShows > 0) {
                log.info("Automation: {} reminder(s) sent, {} booking(s) marked as no-show", reminders, noShows);
            }
        } catch (RuntimeException e) {
            // One failed run (e.g. database briefly unreachable) must not stop the app; the next run tries again.
            log.error("Automation run failed", e);
        }
    }
}
