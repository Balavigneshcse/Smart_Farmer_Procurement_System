package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;

/**
 * Everything on the admin dashboard: today at a glance, each centre live, payments, the last 7 days,
 * the farmers (registered and who brought their crop) and the farmers' reviews of their visits.
 */
public record AdminDashboardResponse(
        LocalDate date,
        Today today,
        List<CentreToday> centres,
        Payments payments,
        List<DayTotal> lastDays,
        List<CropTotal> crops,
        Farmers farmers,
        FeedbackSummary feedback,
        LocalDateTime updatedAt) {

    /** All centres together, today. Cancelled bookings are not counted as booked. */
    public record Today(int booked, int arrived, int waiting, int atCounter, int served, int rejected, int noShow,
                        int cancelled, BigDecimal quantityQtl, BigDecimal value, Integer avgWaitMinutes) {
    }

    /** rating = average centre stars from the farmers' reviews (null when none yet), reviews = how many. */
    public record CentreToday(Long id, String code, String name, boolean active, int counters,
                              int booked, int expected, int waiting, int atCounter, int served, int noShow,
                              BigDecimal quantityQtl, Integer avgWaitMinutes, int capacity,
                              BigDecimal rating, int reviews) {
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

    /**
     * registered and newThisWeek: farmer accounts on the whole site (the same for every admin).
     * broughtCrops: farmers whose crop was weighed at least once (accepted or rejected), accepted: farmers with at
     * least one accepted crop, visits: weighed bookings; these three are all time, for the admin's centre(s).
     */
    public record Farmers(int registered, int newThisWeek, int broughtCrops, int accepted, int visits) {
    }

    /**
     * The farmers' reviews for the admin's centre(s): how many, average stars (one decimal, null when there are
     * none), how often each improvement was chosen (every code, in the fixed order) and the 10 newest reviews.
     */
    public record FeedbackSummary(int count, BigDecimal avgCentreRating, BigDecimal avgAppRating,
                                  List<IssueCount> centreIssues, List<IssueCount> appIssues,
                                  List<FeedbackItem> recent) {
    }

    /** How many reviews chose this improvement code (CentreIssue or AppIssue). */
    public record IssueCount(String code, int count) {
    }

    /** One review; date = the day of the booking. A comment not written is null. */
    public record FeedbackItem(Long id, String farmerName, String centreName, String token, LocalDate date,
                               int centreRating, int appRating, List<CentreIssue> centreIssues,
                               List<AppIssue> appIssues, String centreComment, String appComment,
                               LocalDateTime createdAt) {
    }
}
