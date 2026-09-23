package com.cropcoders.procurement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cropcoders.procurement.entity.FarmerProfile;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, Long> {
}
