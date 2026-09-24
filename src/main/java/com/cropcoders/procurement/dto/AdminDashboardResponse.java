package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything on the admin dashboard: today at a glance, each centre live, payments and the last 7 days. */
public record AdminDashboardResponse(
        LocalDate date,
        Today today,
        List<CentreToday> centres,
        Payments payments,
        List<DayTotal> lastDays,
        List<CropTotal> crops,
        LocalDateTime updatedAt) {

    /** All centres together, today. Cancelled bookings are not counted as booked. */
    public record Today(int booked, int arrived, int waiting, int atCounter, int served, int rejected, int noShow,
                        int cancelled, BigDecimal quantityQtl, BigDecimal value, Integer avgWaitMinutes) {
    }

    public record CentreToday(Long id, String code, String name, boolean active, int counters,
                              int booked, int expected, int waiting, int atCounter, int served, int noShow,
                              BigDecimal quantityQtl, Integer avgWaitMinutes, int capacity) {
    }

    public record Payments(int pendingCount, BigDecimal pendingAmount, int paidTodayCount, BigDecimal paidTodayAmount,
                           BigDecimal paidTotal) {
    }

    /** One day of the last-7-days chart. */
    public record DayTotal(LocalDate date, int booked, int served, int noShow, BigDecimal quantityQtl,
                           BigDecimal value) {
    }

    /** Accepted crop in the last 7 days. */
    public record CropTotal(String crop, int farmers, BigDecimal quantityQtl, BigDecimal value) {
    }
}
