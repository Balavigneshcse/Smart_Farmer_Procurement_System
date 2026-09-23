package com.cropcoders.procurement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.Crop;

public interface CropRepository extends JpaRepository<Crop, Long> {

    List<Crop> findByActiveTrueOrderByNameEn();
}
