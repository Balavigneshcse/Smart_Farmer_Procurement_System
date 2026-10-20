package com.cropcoders.procurement.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.OtpCode;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    /**
     * The newest OTP of an email address, locked until the transaction ends (FOR UPDATE).
     * Wrong guesses sent at the same moment then wait for each other, so every one of them is counted
     * and nobody gets more than max-attempts tries (see OtpService.verifyOtp).
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM otp_codes
            WHERE email = :email
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            FOR UPDATE""")
    Optional<OtpCode> lockLatestByEmail(@Param("email") String email);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime since);

    /** OTPs created since the given moment, for every email address together (the daily limit). */
    @Query("select count(o) from OtpCode o where o.createdAt >= :since")
    long countCreatedSince(@Param("since") LocalDateTime since);
}
