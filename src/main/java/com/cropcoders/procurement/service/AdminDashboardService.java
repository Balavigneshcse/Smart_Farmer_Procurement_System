package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.AdminDashboardResponse;
import com.cropcoders.procurement.dto.AdminDashboardResponse.CentreToday;
import com.cropcoders.procurement.dto.AdminDashboardResponse.FeedbackSummary;
import com.cropcoders.procurement.dto.AdminDashboardResponse.IssueCount;
import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;
import com.cropcoders.procurement.repository.DashboardRepository;

/**
 * Today's numbers for the admin: how busy each centre is, how long farmers wait, what was procured and paid,
 * how many farmers registered and brought their crop, and what farmers said in their reviews.
 */
@Service
public class AdminDashboardService {

    private static final int CHART_DAYS = 7;
    private static final int RECENT_REVIEWS = 10;

    private final DashboardRepository dashboardRepository;

    public AdminDashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    /** centreId = null: all centres (super admin); otherwise the numbers of that one centre. */
    public AdminDashboardResponse dashboard(Long centreId) {
        LocalDate today = LocalDate.now();
        LocalDate firstChartDay = today.minusDays(CHART_DAYS - 1L);

        List<CentreToday> centres = dashboardRepository.centresToday(today, centreId);
        DashboardRepository.TodayTotals totals = dashboardRepository.todayTotals(today, centreId);

        var todaySummary = new AdminDashboardResponse.Today(
                centres.stream().mapToInt(CentreToday::booked).sum(),
                totals.arrived(),
                centres.stream().mapToInt(CentreToday::waiting).sum(),
                centres.stream().mapToInt(CentreToday::atCounter).sum(),
                centres.stream().mapToInt(CentreToday::served).sum(),
                totals.rejected(),
                centres.stream().mapToInt(CentreToday::noShow).sum(),
                totals.cancelled(),
                centres.stream().map(CentreToday::quantityQtl).reduce(BigDecimal.ZERO, BigDecimal::add),
                totals.value(),
                totals.avgWaitMinutes());

        return new AdminDashboardResponse(today, todaySummary, centres, dashboardRepository.payments(today, centreId),
                dashboardRepository.dayTotals(firstChartDay, today, centreId),
                dashboardRepository.cropTotals(firstChartDay, today, centreId),
                dashboardRepository.farmers(firstChartDay.atStartOfDay(), centreId),
                feedback(centreId),
                LocalDateTime.now());
    }

    /** The farmers' reviews: totals, how often each improvement was chosen, and the newest ones. */
    private FeedbackSummary feedback(Long centreId) {
        DashboardRepository.FeedbackTotals totals = dashboardRepository.feedbackTotals(centreId);
        Map<String, Map<String, Integer>> issues = dashboardRepository.issueCounts(centreId);
        return new FeedbackSummary(totals.count(), totals.avgCentreRating(), totals.avgAppRating(),
                everyCode(CentreIssue.values(), issues.getOrDefault("CENTRE", Map.of())),
                everyCode(AppIssue.values(), issues.getOrDefault("APP", Map.of())),
                dashboardRepository.recentFeedback(centreId, RECENT_REVIEWS));
    }

    /** All codes in their fixed order, with 0 for a code nobody chose, so the page can always draw 5 bars. */
    private static List<IssueCount> everyCode(Enum<?>[] codes, Map<String, Integer> counts) {
        return Arrays.stream(codes)
                .map(code -> new IssueCount(code.name(), counts.getOrDefault(code.name(), 0)))
                .toList();
    }
}
