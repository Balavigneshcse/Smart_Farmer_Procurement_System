package com.cropcoders.procurement.dto;

import java.math.BigDecimal;

import com.cropcoders.procurement.entity.Crop;

/** A crop as the admin sees it, including crops that are switched off. */
public record AdminCropView(Long id, String nameEn, String nameTa, String nameHi, BigDecimal ratePerQtl,
                            BigDecimal maxMoisturePct, boolean active) {

    public static AdminCropView from(Crop crop) {
        return new AdminCropView(crop.getId(), crop.getNameEn(), crop.getNameTa(), crop.getNameHi(),
                crop.getRatePerQtl(), crop.getMaxMoisturePct(), crop.isActive());
    }
}
