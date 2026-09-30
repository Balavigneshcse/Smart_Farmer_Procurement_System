package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import com.cropcoders.procurement.enums.Role;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Anyone who can log in. Farmers log in with their email address (OTP sent by email) and also
 * give a mobile number for the centre; staff, centre admins and the super admin have a username and password.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String email;
    private String mobile;
    private String username;
    private String passwordHash;

    /** The centre a staff member or centre admin belongs to (null for farmers and the super admin). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id")
    private Centre centre;

    private boolean active = true;
    private LocalDateTime createdAt;

    protected User() {
        // for JPA
    }

    public static User newFarmer(String name, String email, String mobile) {
        User user = new User();
        user.name = name;
        user.email = email;
        user.mobile = mobile;
        user.role = Role.FARMER;
        return user;
    }

    public static User newStaff(String name, Role role, String username, String passwordHash, Centre centre) {
        User user = new User();
        user.name = name;
        user.role = role;
        user.username = username;
        user.passwordHash = passwordHash;
        user.centre = centre;
        return user;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Centre getCentre() {
        return centre;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
