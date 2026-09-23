package com.cropcoders.procurement.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.Slot;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Query("select s from Slot s join fetch s.centre where s.id = :id")
    Optional<Slot> findWithCentreById(@Param("id") Long id);

    List<Slot> findByCentreIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(Long centreId, LocalDate from, LocalDate to);

    /**
     * Takes one place in the slot if one is free. Returns 1 on success, 0 if the slot is full.
     * The check and the update happen in one SQL statement, so two farmers booking the
     * last place at the same moment can never both succeed.
     */
    @Modifying
    @Query("update Slot s set s.booked = s.booked + 1 where s.id = :id and s.booked < s.capacity")
    int reserveSeat(@Param("id") Long id);

    /** Gives a place back (booking cancelled). */
    @Modifying
    @Query("update Slot s set s.booked = s.booked - 1 where s.id = :id and s.booked > 0")
    int releaseSeat(@Param("id") Long id);
}
