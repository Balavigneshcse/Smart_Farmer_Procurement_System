package com.cropcoders.procurement.repository;

import java.time.LocalDate;
import java.time.LocalTime;
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

    // ---------- Queue (operator desk, TV display) ----------

    /** Every booking of one centre on one day, with everything the queue screens show. */
    @Query("""
            select b from Booking b
            join fetch b.slot
            join fetch b.crop
            join fetch b.farmer
            where b.centre.id = :centreId and b.bookingDate = :date""")
    List<Booking> findAllForCentreDay(@Param("centreId") Long centreId, @Param("date") LocalDate date);

    @Query("""
            select b from Booking b
            join fetch b.slot join fetch b.centre join fetch b.crop join fetch b.farmer
            where b.id = :id""")
    Optional<Booking> findDetailedById(@Param("id") Long id);

    @Query("""
            select b from Booking b
            join fetch b.slot join fetch b.centre join fetch b.crop join fetch b.farmer
            where b.centre.id = :centreId and b.bookingDate = :date and b.token = :token""")
    Optional<Booking> findDetailedByToken(@Param("centreId") Long centreId, @Param("date") LocalDate date,
                                          @Param("token") String token);

    // ---------- Automation jobs ----------

    /** Booked (not yet arrived) slots of one day that start after :from and no later than :until, not reminded yet. */
    @Query("""
            select b from Booking b
            join fetch b.slot s join fetch b.centre join fetch b.crop join fetch b.farmer
            where b.status = com.cropcoders.procurement.enums.BookingStatus.BOOKED
              and b.reminderSent = false
              and b.bookingDate = :date and s.startTime > :from and s.startTime <= :until""")
    List<Booking> findDueForReminder(@Param("date") LocalDate date, @Param("from") LocalTime from,
                                     @Param("until") LocalTime until);

    /** Bookings whose slot ended before the cutoff moment and the farmer never checked in. */
    @Query("""
            select b from Booking b
            join fetch b.slot s join fetch b.centre join fetch b.crop join fetch b.farmer
            where b.status = com.cropcoders.procurement.enums.BookingStatus.BOOKED
              and (b.bookingDate < :cutoffDate or (b.bookingDate = :cutoffDate and s.endTime < :cutoffTime))""")
    List<Booking> findNotArrivedBefore(@Param("cutoffDate") LocalDate cutoffDate,
                                       @Param("cutoffTime") LocalTime cutoffTime);

    boolean existsByCentreIdAndBookingDateAndStatusAndCounterNo(Long centreId, LocalDate date, BookingStatus status,
                                                                Integer counterNo);

    /**
     * The next farmer to call: first in queue order, and locked for this transaction.
     * SKIP LOCKED means two counters pressing "Call next" at the same moment get two different farmers.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM bookings
            WHERE centre_id = :centreId AND booking_date = :date AND status = 'CHECKED_IN'
            ORDER BY queue_priority, checked_in_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED""")
    Optional<Booking> lockNextWaiting(@Param("centreId") Long centreId, @Param("date") LocalDate date);
}
