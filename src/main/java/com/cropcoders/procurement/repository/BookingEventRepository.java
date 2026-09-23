package com.cropcoders.procurement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.BookingEvent;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {

    List<BookingEvent> findByBookingIdOrderByCreatedAtAscIdAsc(Long bookingId);
}
