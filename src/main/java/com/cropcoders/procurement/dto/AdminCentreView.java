package com.cropcoders.procurement.dto;

import java.time.LocalTime;

import com.cropcoders.procurement.entity.Centre;

/** A centre as the admin sees it, including centres that are switched off. */
public record AdminCentreView(Long id, String code, String name, String district, String address,
                              LocalTime openTime, LocalTime closeTime, int slotMinutes, int counters,
                              int avgServiceMin, int slotCapacity, boolean active) {

    public static AdminCentreView from(Centre centre) {
        return new AdminCentreView(centre.getId(), centre.getCode(), centre.getName(), centre.getDistrict(),
                centre.getAddress(), centre.getOpenTime(), centre.getCloseTime(), centre.getSlotMinutes(),
                centre.getCounters(), centre.getAvgServiceMin(), centre.slotCapacity(), centre.isActive());
    }
}
