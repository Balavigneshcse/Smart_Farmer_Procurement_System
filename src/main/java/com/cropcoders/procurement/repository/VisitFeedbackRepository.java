package com.cropcoders.procurement.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.VisitFeedback;

public interface VisitFeedbackRepository extends JpaRepository<VisitFeedback, Long> {

    boolean existsByBookingId(Long bookingId);

    /** The reviews of several bookings in one query (the farmer's booking list). */
    List<VisitFeedback> findByBookingIdIn(Collection<Long> bookingIds);
}
