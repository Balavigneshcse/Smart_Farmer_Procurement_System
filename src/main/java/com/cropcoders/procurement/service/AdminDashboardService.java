package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.AdminDashboardResponse;
import com.cropcoders.procurement.dto.AdminDashboardResponse.CentreToday;
import com.cropcoders.procurement.repository.DashboardRepository;

/** Today's numbers for the admin: how busy each centre is, how long farmers wait, what was procured and paid. */
@Service
public class AdminDashboardService {

    private static final int CHART_DAYS = 7;

    private final DashboardRepository dashboardRepository;

    public AdminDashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate firstChartDay = today.minusDays(CHART_DAYS - 1L);

        List<CentreToday> centres = dashboardRepository.centresToday(today);
        DashboardRepository.TodayTotals totals = dashboardRepository.todayTotals(today);

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

        return new AdminDashboardResponse(today, todaySummary, centres, dashboardRepository.payments(today),
                dashboardRepository.dayTotals(firstChartDay, today),
                dashboardRepository.cropTotals(firstChartDay, today),
                LocalDateTime.now());
    }
}
