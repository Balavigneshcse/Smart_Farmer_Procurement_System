package com.cropcoders.procurement.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.OtpCode;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByMobileOrderByCreatedAtDesc(String mobile);

    long countByMobileAndCreatedAtAfter(String mobile, LocalDateTime since);
}
