package com.cropcoders.procurement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cropcoders.procurement.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p where p.procurement.id in :procurementIds")
    List<Payment> findByProcurementIds(@Param("procurementIds") Collection<Long> procurementIds);

    /** All payments with the farmer, centre and crop loaded, newest first (admin payments page). */
    @Query("""
            select p from Payment p
            join fetch p.procurement pr
            join fetch pr.booking b
            join fetch b.farmer
            join fetch b.centre
            join fetch b.crop
            order by p.createdAt desc""")
    List<Payment> findAllForAdmin();

    @Query("""
            select p from Payment p
            join fetch p.procurement pr
            join fetch pr.booking b
            join fetch b.farmer
            join fetch b.centre
            join fetch b.crop
            where p.id = :id""")
    Optional<Payment> findDetailedById(@Param("id") Long id);

    /**
     * Locks one payment row until the transaction ends and returns its id. A second request for the same
     * payment (a double click, two admins) waits here, then sees what the first one saved, so the same
     * payment never gets two Razorpay orders or is marked paid twice.
     */
    @Query(nativeQuery = true, value = "SELECT id FROM payments WHERE id = :id FOR UPDATE")
    Optional<Long> lockById(@Param("id") Long id);

    /** Same as lockById, found by the Razorpay order id. */
    @Query(nativeQuery = true, value = "SELECT id FROM payments WHERE rzp_order_id = :orderId FOR UPDATE")
    Optional<Long> lockByRzpOrderId(@Param("orderId") String orderId);
}
