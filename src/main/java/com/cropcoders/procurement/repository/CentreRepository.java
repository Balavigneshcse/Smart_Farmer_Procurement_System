package com.cropcoders.procurement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.Centre;

public interface CentreRepository extends JpaRepository<Centre, Long> {

    List<Centre> findByActiveTrueOrderByName();
}
