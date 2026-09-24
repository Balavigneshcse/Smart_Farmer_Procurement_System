package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time password sent to an email address. Only a hash of the code is stored. */
@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String codeHash;
    private LocalDateTime expiresAt;
    private int attempts;
    private boolean used;
    private LocalDateTime createdAt;

    protected OtpCode() {
        // for JPA
    }

    public OtpCode(String email, String codeHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public boolean isUsed() {
        return used;
    }

    public void markUsed() {
        used = true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
