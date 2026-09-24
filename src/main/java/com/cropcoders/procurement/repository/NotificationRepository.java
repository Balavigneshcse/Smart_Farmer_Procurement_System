package com.cropcoders.procurement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.entity.Notification;
import com.cropcoders.procurement.enums.EmailStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The latest notifications of a farmer, newest first. */
    List<Notification> findTop30ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Emails sent (or being sent) since a moment - used for the daily email limit. */
    long countByEmailStatusInAndCreatedAtGreaterThanEqual(Collection<EmailStatus> statuses, LocalDateTime since);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Called by the background email sender, outside any other transaction. */
    @Transactional
    @Modifying
    @Query("update Notification n set n.emailStatus = :status, n.providerRef = :ref where n.id = :id")
    int updateEmailStatus(@Param("id") Long id, @Param("status") EmailStatus status, @Param("ref") String ref);
}
