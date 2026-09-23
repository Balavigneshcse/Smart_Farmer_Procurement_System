package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Registration details of a farmer. Shares its primary key with the farmer's user row. */
@Entity
@Table(name = "farmer_profiles")
public class FarmerProfile {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String address;
    private String district;
    private String aadhaarLast4;

    /** "en", "ta" or "hi" - used for the farmer's pages and SMS. */
    private String language;

    private LocalDateTime updatedAt;

    protected FarmerProfile() {
        // for JPA
    }

    public FarmerProfile(User user, String address, String district, String aadhaarLast4, String language) {
        this.user = user;
        this.address = address;
        this.district = district;
        this.aadhaarLast4 = aadhaarLast4;
        this.language = language;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getAadhaarLast4() {
        return aadhaarLast4;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
