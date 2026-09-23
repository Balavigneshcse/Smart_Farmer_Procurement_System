package com.cropcoders.procurement.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.Procurement;

public interface ProcurementRepository extends JpaRepository<Procurement, Long> {

    @Query("select p from Procurement p where p.booking.id in :bookingIds")
    List<Procurement> findByBookingIds(@Param("bookingIds") Collection<Long> bookingIds);
}
