package com.cropcoders.procurement.dto;

import java.time.LocalDate;
import java.util.List;

/** All slots of one centre on one day, with day totals for the "how busy" indicator. */
public record SlotDayResponse(LocalDate date, int capacity, int booked, List<SlotResponse> slots) {
}
