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

    @Query("""
            select p from Payment p
            join fetch p.procurement pr
            join fetch pr.booking b
            join fetch b.farmer
            join fetch b.centre
            join fetch b.crop
            where p.rzpOrderId = :orderId""")
    Optional<Payment> findDetailedByRzpOrderId(@Param("orderId") String orderId);
}
