package com.cropcoders.procurement.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByFarmerIdAndBookingDateAndStatusIn(Long farmerId, LocalDate bookingDate,
                                                      Collection<BookingStatus> statuses);

    /** All of a farmer's bookings with slot, centre and crop loaded in one query. */
    @Query("""
            select b from Booking b
            join fetch b.slot s
            join fetch b.centre
            join fetch b.crop
            where b.farmer.id = :farmerId
            order by b.bookingDate desc, s.startTime desc""")
    List<Booking> findAllForFarmer(@Param("farmerId") Long farmerId);

    /** One booking, only if it belongs to this farmer. */
    @Query("""
            select b from Booking b
            join fetch b.slot
            join fetch b.centre
            join fetch b.crop
            where b.id = :id and b.farmer.id = :farmerId""")
    Optional<Booking> findForFarmer(@Param("id") Long id, @Param("farmerId") Long farmerId);
}
