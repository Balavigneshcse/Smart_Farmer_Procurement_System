package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import com.cropcoders.procurement.security.EncryptedText;

import jakarta.persistence.Convert;
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
    /** Encrypted in the database (see EncryptedText). */
    @Convert(converter = EncryptedText.class)
    private String aadhaarLast4;

    /** "en", "ta" or "hi" - used for the farmer's pages and emails. */
    private String language;

    /** "BANK" or "UPI"; null until the farmer gives payment details. */
    private String payoutMethod;
    private String accountHolder;
    /** Encrypted in the database (see EncryptedText). */
    @Convert(converter = EncryptedText.class)
    private String accountNumber;
    private String ifsc;
    private String bankName;
    /** Encrypted in the database (see EncryptedText). */
    @Convert(converter = EncryptedText.class)
    private String upiId;

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

    /** Bank account: the UPI ID is cleared. */
    public void payToBank(String accountHolder, String accountNumber, String ifsc, String bankName) {
        this.payoutMethod = "BANK";
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
        this.ifsc = ifsc;
        this.bankName = bankName;
        this.upiId = null;
    }

    /** UPI: the bank details are cleared. */
    public void payToUpi(String upiId) {
        this.payoutMethod = "UPI";
        this.upiId = upiId;
        this.accountHolder = null;
        this.accountNumber = null;
        this.ifsc = null;
        this.bankName = null;
    }

    public String getPayoutMethod() {
        return payoutMethod;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public String getBankName() {
        return bankName;
    }

    public String getUpiId() {
        return upiId;
    }

    /** "UPI ravi@okaxis" or "State Bank of India, account ending 4321, IFSC SBIN0001234"; null if not given. */
    public String payoutSummary() {
        if ("UPI".equals(payoutMethod)) {
            return "UPI " + upiId;
        }
        if ("BANK".equals(payoutMethod)) {
            return bankName + ", account ending " + accountNumber.substring(accountNumber.length() - 4)
                    + ", IFSC " + ifsc + " (" + accountHolder + ")";
        }
        return null;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
