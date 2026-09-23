package com.cropcoders.procurement.dto;

import java.time.LocalTime;

import com.cropcoders.procurement.entity.Centre;

public record CentreResponse(
        Long id,
        String code,
        String name,
        String district,
        String address,
        LocalTime openTime,
        LocalTime closeTime,
        int slotMinutes,
        int counters,
        int slotCapacity) {

    public static CentreResponse from(Centre centre) {
        return new CentreResponse(
                centre.getId(),
                centre.getCode(),
                centre.getName(),
                centre.getDistrict(),
                centre.getAddress(),
                centre.getOpenTime(),
                centre.getCloseTime(),
                centre.getSlotMinutes(),
                centre.getCounters(),
                centre.slotCapacity());
    }
}
