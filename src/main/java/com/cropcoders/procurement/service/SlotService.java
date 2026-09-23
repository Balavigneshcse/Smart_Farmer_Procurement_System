package com.cropcoders.procurement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.SlotDayResponse;
import com.cropcoders.procurement.dto.SlotResponse;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.Slot;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.SlotRepository;

/** Creates the bookable time slots and lists them for the booking page. */
@Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);

    // ON CONFLICT DO NOTHING: slots that already exist are skipped, so this is safe to run any number of times.
    private static final String INSERT_SLOT = """
            INSERT INTO slots (centre_id, slot_date, start_time, end_time, capacity, booked)
            VALUES (?, ?, ?, ?, ?, 0)
            ON CONFLICT (centre_id, slot_date, start_time) DO NOTHING""";

    private final CentreRepository centreRepository;
    private final SlotRepository slotRepository;
    private final JdbcTemplate jdbc;
    private final int daysAhead;

    public SlotService(CentreRepository centreRepository, SlotRepository slotRepository, JdbcTemplate jdbc,
                       AppProperties properties) {
        this.centreRepository = centreRepository;
        this.slotRepository = slotRepository;
        this.jdbc = jdbc;
        this.daysAhead = properties.booking().daysAhead();
    }

    /**
     * Makes sure every active centre has slots from today up to daysAhead days.
     * Example: open 08:00-17:00 with 30-minute slots gives 18 slots a day.
     * Returns how many new slots were created.
     */
    @Transactional
    public int generateUpcomingSlots() {
        LocalDate today = LocalDate.now();
        List<Object[]> rows = new ArrayList<>();

        for (Centre centre : centreRepository.findByActiveTrueOrderByName()) {
            int open = centre.getOpenTime().toSecondOfDay() / 60;
            int close = centre.getCloseTime().toSecondOfDay() / 60;
            int length = centre.getSlotMinutes();

            for (int day = 0; day < daysAhead; day++) {
                LocalDate date = today.plusDays(day);
                for (int start = open; start + length <= close; start += length) {
                    rows.add(new Object[]{
                            centre.getId(), date,
                            LocalTime.ofSecondOfDay(start * 60L), LocalTime.ofSecondOfDay((start + length) * 60L),
                            centre.slotCapacity()});
                }
            }
        }

        int created = Arrays.stream(jdbc.batchUpdate(INSERT_SLOT, rows)).map(n -> Math.max(n, 0)).sum();
        log.info("Slot generation: {} new slots for the next {} days", created, daysAhead);
        return created;
    }

    /** Slots of one centre for today and the next days, grouped by day. */
    @Transactional(readOnly = true)
    public List<SlotDayResponse> upcomingSlots(Long centreId) {
        centreRepository.findById(centreId)
                .filter(Centre::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CENTRE_NOT_FOUND", "Centre not found."));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<Slot> slots = slotRepository.findByCentreIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                centreId, today, today.plusDays(daysAhead - 1L));

        Map<LocalDate, List<Slot>> byDay = new LinkedHashMap<>();
        slots.forEach(slot -> byDay.computeIfAbsent(slot.getSlotDate(), d -> new ArrayList<>()).add(slot));

        return byDay.entrySet().stream()
                .map(day -> new SlotDayResponse(
                        day.getKey(),
                        day.getValue().stream().mapToInt(Slot::getCapacity).sum(),
                        day.getValue().stream().mapToInt(Slot::getBooked).sum(),
                        day.getValue().stream().map(slot -> SlotResponse.from(slot, now)).toList()))
                .toList();
    }
}
