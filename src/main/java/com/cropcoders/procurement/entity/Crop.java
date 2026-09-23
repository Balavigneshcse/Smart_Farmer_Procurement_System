package com.cropcoders.procurement.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A crop the centres buy, with its rate per quintal and moisture limit. */
@Entity
@Table(name = "crops")
public class Crop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nameEn;
    private String nameTa;
    private String nameHi;
    private BigDecimal ratePerQtl;
    private BigDecimal maxMoisturePct;
    private boolean active = true;

    /** Crop name in the farmer's language ("en", "ta" or "hi"). */
    public String nameIn(String language) {
        return switch (language) {
            case "ta" -> nameTa;
            case "hi" -> nameHi;
            default -> nameEn;
        };
    }

    public Long getId() {
        return id;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameTa() {
        return nameTa;
    }

    public void setNameTa(String nameTa) {
        this.nameTa = nameTa;
    }

    public String getNameHi() {
        return nameHi;
    }

    public void setNameHi(String nameHi) {
        this.nameHi = nameHi;
    }

    public BigDecimal getRatePerQtl() {
        return ratePerQtl;
    }

    public void setRatePerQtl(BigDecimal ratePerQtl) {
        this.ratePerQtl = ratePerQtl;
    }

    public BigDecimal getMaxMoisturePct() {
        return maxMoisturePct;
    }

    public void setMaxMoisturePct(BigDecimal maxMoisturePct) {
        this.maxMoisturePct = maxMoisturePct;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
