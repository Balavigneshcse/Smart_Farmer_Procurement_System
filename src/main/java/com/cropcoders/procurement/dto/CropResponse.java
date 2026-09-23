package com.cropcoders.procurement.dto;

import java.math.BigDecimal;

import com.cropcoders.procurement.entity.Crop;

public record CropResponse(
        Long id,
        String nameEn,
        String nameTa,
        String nameHi,
        BigDecimal ratePerQtl,
        BigDecimal maxMoisturePct) {

    public static CropResponse from(Crop crop) {
        return new CropResponse(
                crop.getId(),
                crop.getNameEn(),
                crop.getNameTa(),
                crop.getNameHi(),
                crop.getRatePerQtl(),
                crop.getMaxMoisturePct());
    }
}
