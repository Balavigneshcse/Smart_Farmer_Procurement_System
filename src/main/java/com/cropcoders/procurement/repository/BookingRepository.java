package com.cropcoders.procurement.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Sets turn_alert_sent ("your turn is near" email) if it is not set yet. Returns 1 only for the call that
     * set it. Only this one column is written, so a change another counter saved meanwhile is kept.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Booking b set b.turnAlertSent = true where b.id = :id and b.turnAlertSent = false")
    int markTurnAlertSent(@Param("id") Long id);

    /** Several bookings with everything the emails need, e.g. the ones locked by lockNotArrivedBefore. */
    @Query("""
            select b from Booking b
            join fetch b.slot join fetch b.centre join fetch b.crop join fetch b.farmer
            where b.id in :ids""")
    List<Booking> findDetailedByIds(@Param("ids") Collection<Long> ids);

    // ---------- Row locks ----------
    // Each one locks booking rows until the transaction ends and returns only the ids (like
    // PaymentRepository.lockById). A second request for the same booking (two cancels, cancel and check-in,
    // two check-ins, skip and weighment, ...) waits here, then sees what the first one saved.
    // Always lock FIRST and load the booking after: a booking already loaded in this transaction would
    // not be read from the database again, so the lock would not help.

    @Query(nativeQuery = true, value = "SELECT id FROM bookings WHERE id = :id FOR UPDATE")
    Optional<Long> lockById(@Param("id") Long id);

    /** Locks the booking only if it belongs to this farmer (cancel). */
    @Query(nativeQuery = true, value = "SELECT id FROM bookings WHERE id = :id AND farmer_id = :farmerId FOR UPDATE")
    Optional<Long> lockForFarmer(@Param("id") Long id, @Param("farmerId") Long farmerId);

    /** Locks a booking found by its token at one centre on one day (check-in by token). */
    @Query(nativeQuery = true, value = """
            SELECT id FROM bookings
            WHERE centre_id = :centreId AND booking_date = :date AND token = :token
            FOR UPDATE""")
    Optional<Long> lockByToken(@Param("centreId") Long centreId, @Param("date") LocalDate date,
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

    /**
     * Sets reminder_sent, but only if it is not set yet and the booking is still BOOKED. Returns 1 if this
     * call set it, 0 if not. Only this one column is written, so a cancel saved by another request in the
     * meantime is kept, and two runs at the same moment cannot both send the reminder.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Booking b set b.reminderSent = true
            where b.id = :id and b.reminderSent = false
              and b.status = com.cropcoders.procurement.enums.BookingStatus.BOOKED""")
    int markReminderSent(@Param("id") Long id);

    /**
     * Ids of bookings whose slot ended before the cutoff moment and the farmer never checked in, locked.
     * SKIP LOCKED: a booking the operator desk is changing right now (e.g. a late check-in) is left out;
     * the next run looks at it again.
     */
    @Query(nativeQuery = true, value = """
            SELECT b.id FROM bookings b JOIN slots s ON s.id = b.slot_id
            WHERE b.status = 'BOOKED'
              AND (b.booking_date < :cutoffDate OR (b.booking_date = :cutoffDate AND s.end_time < :cutoffTime))
            FOR UPDATE OF b SKIP LOCKED""")
    List<Long> lockNotArrivedBefore(@Param("cutoffDate") LocalDate cutoffDate,
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
