package com.cropcoders.procurement.dto;

/** How much demo data is in the database right now. */
public record DemoDataResponse(int farmers, int bookingsToday, int bookingsTotal) {
}
